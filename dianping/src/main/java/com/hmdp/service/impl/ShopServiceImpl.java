package com.hmdp.service.impl;


import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
//import com.hmdp.utils.Lock;
//import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;
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

//    @Resource
//    StringRedisTemplate stringRedisTemplate;
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

}
