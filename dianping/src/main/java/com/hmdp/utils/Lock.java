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

    public boolean isLocked(Long id) {
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_SHOP_KEY + id, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    public void unlock() {
        stringRedisTemplate.delete(LOCK_SHOP_KEY);
    }

}
