package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private SeckillVoucherServiceImpl seckillVoucherService;
    @Lazy
    @Resource
    private IVoucherOrderService self;
    /**
     * 秒杀优惠券
     * @param  voucherId 优惠券id
     * @return 订单id
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户ID
        Long userId = UserHolder.getUser().getId();
        if (userId == null) {
            return Result.fail("用户未登录");
        }
        //添加用户锁  防止线程并发同一用户并发重复购买
        synchronized (userId.toString().intern()){
            //判断用户是否已经购买过该优惠券
            int count = this.query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if ( count > 0) {
                return Result.fail("用户已经购买过该优惠券");
            }
            return Result.ok(createVoucherOrder(voucherId, userId));
        }
    }

    public Result createVoucherOrder(Long voucherId, Long userId) {
        //扣减优惠券库存   判断和扣减原子操作
        //TODO MQ异步写入数据库
        Long stock = stringRedisTemplate.opsForValue().decrement(SECKILL_STOCK_KEY + voucherId);
        if (stock < 0) {
            //查询数据库获取库存
            SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
            if (seckillVoucher == null || seckillVoucher.getStock() <= 0) {
                stringRedisTemplate.opsForValue().increment(SECKILL_STOCK_KEY + voucherId);
                return Result.fail("库存不足");
            }
            //将库存保存到Redis并设置过期时间
            long now = LocalDateTime.now().toEpochSecond(ZoneOffset.of("+8"));
            long end = seckillVoucher.getEndTime().toEpochSecond(ZoneOffset.of("+8"));
            if (now > end) {
                stringRedisTemplate.opsForValue().increment(SECKILL_STOCK_KEY + voucherId);
                return Result.fail("秒杀已结束");
            }
            stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + voucherId, String.valueOf(seckillVoucher.getStock() - 1), end - now, TimeUnit.SECONDS);
        }
        try {
            //创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            voucherOrder.setUserId(userId);
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);
            return Result.ok(voucherOrder.getId());
        } catch (Exception e) {
            //库存加一
            stringRedisTemplate.opsForValue().increment(SECKILL_STOCK_KEY + voucherId);
            throw new RuntimeException(e);
        }
    }
}
