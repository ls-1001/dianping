package com.hmdp.service.impl;


import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
//import com.hmdp.utils.Lock;
//import org.springframework.data.redis.core.StringRedisTemplate;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.data.redis.domain.geo.Metrics;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    StringRedisTemplate stringRedisTemplate;
//    @Resource
//    private Lock lock;
    @Resource
    private CacheClient cacheClient;

//    @Override
//    public Result queryById(Long id) {
//        //先从redis缓存中查询
//        String strShop = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//        if (StrUtil.isNotBlank(strShop)){
//            return Result.ok(JSONUtil.toBean(strShop, Shop.class));
//        }
//        if (strShop != null){
//            return Result.fail("Shop not found");
//        }
//        //解决缓存击穿问题
//        //1.获取锁
//        try {
//            boolean isLocked = lock.isLocked( LOCK_SHOP_KEY, id);
//            if (!isLocked){
//                Thread.sleep(500);
//                queryById(id);
//            }
//            //如果redis中不存在，从mysql中查询
//            Shop shop = getById(id);
//            if (shop != null){
//                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop));
//                stringRedisTemplate.expire(CACHE_SHOP_KEY + id, CACHE_SHOP_TTL, TimeUnit.MINUTES);
//                return Result.ok(shop);
//            }
//            //防止缓存击穿
//            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return Result.fail("Shop not found");
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            lock.unlock(LOCK_SHOP_KEY, id);
//        }
//    }


    @Override
    public Result queryById(Long id) {
        //使用缓存工具类查询
        return Result.ok(cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, CACHE_SHOP_TTL, TimeUnit.MINUTES, LOCK_SHOP_KEY , Shop.class, this::getById ));
    }

    /**
     * 根据类型分页查询
     * @param typeId 类型ID
     * @param current 当前页
     * @param x 经度
     * @param y 纬度
     * @return
     */
    @Override
    public Object getShopByType(Integer typeId, Integer current, Double x, Double y) {
        if (x == null || y == null) {
            // 如果x和y为空，不进行排序
            return query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        }

        //分页
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        String key = SHOP_GEO_KEY + typeId;
        //从redis查询
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000, Metrics.METERS),
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance().sortAscending().limit(end));
        if (results == null || results.getContent().isEmpty()) {
            results = rebuildGeoShop(typeId, key, x, y, end);
            if (results == null || results.getContent().isEmpty()) {
                return null;
            }
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        //获取shopId
        //根据shopId查询shop
        List<Shop> shops = new ArrayList<>(list.size());
        Map<Long, Distance> shopMap = new LinkedHashMap<>(list.size());
        list.stream().skip(from).forEach(geoResult -> {
            Long shopId = Long.valueOf(geoResult.getContent().getName());
            shopMap.put(shopId, geoResult.getDistance());
        });
        //根据shopId查询shop
        for (Long shopId : shopMap.keySet()){
            Shop shop;
            //先从redis中查询
            String strShop = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + shopId);
            if (strShop == null || strShop.isEmpty()) {
                //从数据库查询
                shop = getById(shopId);
                shop.setDistance(shopMap.get(shopId).getValue());
                shops.add(shop);
                //缓存
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + shopId, JSONUtil.toJsonStr(shop));
                stringRedisTemplate.expire(CACHE_SHOP_KEY + shopId, CACHE_SHOP_TTL, TimeUnit.MINUTES);
            }else {
                //封装shop
                shop = JSONUtil.toBean(strShop, Shop.class);
                shop.setDistance(shopMap.get(shopId).getValue());
                shops.add(shop);
            }
        }
        return shops;
    }


    /**
     * 从数据库查询商铺并重建Redis GEO缓存
     * @param typeId 商铺类型ID
     * @param key Redis GEO key
     * @param x 经度
     * @param y 纬度
     * @param limit 查询数量上限
     * @return GEO查询结果，若无数据则返回null
     */
    private GeoResults<RedisGeoCommands.GeoLocation<String>> rebuildGeoShop(Integer typeId, String key, Double x, Double y, int limit) {
        List<Shop> shopList = query().eq("type_id", typeId).list();
        if (shopList == null || shopList.isEmpty()) {
            return null;
        }
        Map<String, Point> geoMap = new HashMap<>(shopList.size());
        for (Shop shop : shopList) {
            geoMap.put(shop.getId().toString(), new Point(shop.getX(), shop.getY()));
        }
        stringRedisTemplate.opsForGeo().add(key, geoMap);
        return stringRedisTemplate.opsForGeo()
                .search(key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000, Metrics.METERS),
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance().sortAscending().limit(limit));
    }


}
