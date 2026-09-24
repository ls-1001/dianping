package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;


/**
 * @Description: 缓存工具类
 * @Author: 罗胜
 * @Date: 2026/9/25
 */
@Component
public class CacheClient {
    private StringRedisTemplate stringRedisTemplate;
    private Lock lock;

    public CacheClient(StringRedisTemplate stringRedisTemplate , Lock lock) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.lock = lock;
    }

    /**
     * 将任意Java对象序列化为JSON并存储在Redis中
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void set(String key, Object value, Long time , TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }



    /**
     * 使用setnx互斥锁的方式防止缓存击穿
     * 通过缓存空值的方式防止缓存穿透
     * @param Key
     * @param Id
     * @param time
     * @param unit
     * @param lockKey
     * @param type
     * @param dbFallback
     * @param <R>
     * @return
     */
    public <R> R queryWithPassThrough(String Key , Long Id , Long time , TimeUnit unit , String lockKey , Class<R> type , Function<Long, R> dbFallback) {
        //先从redis缓存中查询
        String strShop = stringRedisTemplate.opsForValue().get(Key+Id);
        if (StrUtil.isNotBlank(strShop)){
            return JSONUtil.toBean(strShop, type);
        }
        if (strShop != null){
            return null;
        }
        //解决缓存击穿问题
        //1.获取锁
        try {
            boolean isLocked = lock.isLocked(lockKey, Id);
            if (!isLocked){
                Thread.sleep(500);
                queryWithPassThrough(Key, Id, time, unit, lockKey, type, dbFallback);
            }
            //如果redis中不存在，从mysql中查询
            R r = dbFallback.apply(Id);
            if (r != null){
                this.set(Key+Id, r, time, unit);
                return r;
            }
            //防止缓存击穿
            this.set(Key+Id, null, CACHE_NULL_TTL, unit);
            return null;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock(lockKey, Id);
        }
    }

}
