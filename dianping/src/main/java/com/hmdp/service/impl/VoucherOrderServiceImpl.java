package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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

    /**
     * 秒杀优惠券
     * @param  voucherId 优惠券id
     * @return 订单id
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        //查看优惠券库存
        String stock = stringRedisTemplate.opsForValue().get(SECKILL_STOCK_KEY + voucherId);
        if (stock == null || stock.equals("0")) {
            //查询数据库获取库存
            SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
            if (seckillVoucher == null || seckillVoucher.getStock() == 0) {
                return Result.fail("库存不足");
            }
            //将库存保存到Redis并设置过期时间
            long now = LocalDateTime.now().toEpochSecond(ZoneOffset.of("+8"));
            long end = seckillVoucher.getEndTime().toEpochSecond(ZoneOffset.of("+8"));
            stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + voucherId, seckillVoucher.getStock().toString(), end - now, TimeUnit.SECONDS);
        }
        //获取用户ID
        Long userId = UserHolder.getUser().getId();
        if (userId == null) {
            return Result.fail("用户未登录");
        }
        //判断用户是否已经购买过该优惠券
        if (this.query().eq("user_id", userId).eq("voucher_id", voucherId).one() != null) {
            return Result.fail("用户已经购买过该优惠券");
        }
        //扣减优惠券库存
        //TODO MQ异步写入数据库
        stringRedisTemplate.opsForValue().decrement(SECKILL_STOCK_KEY + voucherId);
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        return Result.ok(voucherOrder.getId());
    }


}
