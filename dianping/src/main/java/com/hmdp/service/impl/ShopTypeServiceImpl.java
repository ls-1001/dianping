package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
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
@Slf4j
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result typeList() {
        // 从Redis中查询缓存
        Map<Object, Object> shopTypeMap = stringRedisTemplate.opsForHash().entries(CACHE_SHOP_TYPE_KEY);
        if (!shopTypeMap.isEmpty()) {
            // 返回缓存数据shopTypeMap
            //封装成List<ShopType>
            List<ShopType> typeList = shopTypeMap.values().stream()
                    .map(shopType -> JSONUtil.toBean(shopType.toString(), ShopType.class))
                    .collect(Collectors.toList());
            log.info("Cache hit for shop type list {}", typeList.toString());
            return Result.ok(typeList);
        }
        // mysql查询店铺类型
        List<ShopType> typeList = query().orderByAsc("sort").list();
        log.info("Cache miss for shop type list, querying from database {}", typeList.toString());
        if (typeList == null){
            return Result.fail("店铺类型查询失败");
        }
        //缓存到Redis
//        stringRedisTemplate.opsForHash().putAll(CACHE_SHOP_TYPE_KEY, typeList.stream()
//                .collect(Collectors.toMap(ShopType::getId, shopType -> JSONUtil.toJsonStr(shopType))));
//        // ... existing code ...
        stringRedisTemplate.opsForHash().putAll(CACHE_SHOP_TYPE_KEY, typeList.stream()
                .collect(Collectors.toMap(
                        shopType -> shopType.getId().toString(),
                        shopType -> JSONUtil.toJsonStr(shopType)
                )));
        //设置缓存过期时间
        stringRedisTemplate.expire(CACHE_SHOP_TYPE_KEY, CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);
        log.info("Cache updated for shop type list {}", typeList.toString());
        return Result.ok(typeList);
    }
}
