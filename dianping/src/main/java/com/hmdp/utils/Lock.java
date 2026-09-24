package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_TTL;

@Component
public class Lock {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public boolean isLocked(String key, Long id) {
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key + id, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    public void unlock(String key, Long id) {
        stringRedisTemplate.delete(key + id);
    }

}
