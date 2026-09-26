package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

//import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_TTL;

@Component
public class Lock {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end"
        );
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public boolean isLocked(String key, Long id ,String value,Long time) {
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key + id, value, time, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    public void unlock(String key, Long id) {
        stringRedisTemplate.delete(key + id);
    }
    public void unlock(String key, Long id, String value) {
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key + id), value);
    }

}
