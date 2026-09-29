package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.FOLLOW_USER_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    /** 关注集合缓存，key格式：follows:{userId}，成员为被关注用户id */
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 共同关注查询后回表获取用户昵称和头像 */
    @Resource
    private IUserService userService;

    /**
     * 关注用户
     * @param followUserId 被关注用户
     * @param isFollow 是否关注
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        String key = FOLLOW_USER_KEY + userId;
        if (Boolean.TRUE.equals(isFollow)) {
            // key缺失时先回源，避免只补回一个新关注而丢失原有集合
            ensureFollowSet(userId);
            // 先更新Redis，保证重复操作幂等
            stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            // 数据库侧先查再插，避免重复写入同一条关注关系
            long count = count(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            if (count == 0) {
                Follow follow = new Follow();
                follow.setUserId(userId);
                follow.setFollowUserId(followUserId);
                save(follow);
            }
        } else {
            // 取消关注时同步清理Redis缓存和数据库记录
            stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
        }
        return Result.ok();
    }

    /**
     * 判断是否关注
     * @param followUserId 被关注用户
     * @return 是否关注
     */
    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        // 缓存未命中先回源，避免集合被淘汰后误判为未关注
        ensureFollowSet(userId);
        Boolean isMember = stringRedisTemplate.opsForSet()
                .isMember(FOLLOW_USER_KEY + userId, followUserId.toString());
        return Result.ok(isMember);
    }

    /**
     * 获取关注用户
     * @param userId 用户id
     * @return 关注用户
     */
    @Override
    public Result followCommons(Long userId) {
        Long currentUserId = UserHolder.getUser().getId();
        // 交集前先补齐双方集合，任意一个key缺失都会导致交集结果错误
        ensureFollowSet(currentUserId);
        ensureFollowSet(userId);
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(
                FOLLOW_USER_KEY + currentUserId,
                FOLLOW_USER_KEY + userId);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 将共同关注的用户id批量转为Long，再一次性回表查询
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(ids).stream()
                .map((User user) -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }

    /**
     * 确保关注集合存在于Redis，不存在则从数据库回源重建
     * @param userId 用户id
     */
    private void ensureFollowSet(Long userId) {
        String key = FOLLOW_USER_KEY + userId;
        Boolean exists = stringRedisTemplate.hasKey(key);
        if (Boolean.TRUE.equals(exists)) {
            return;
        }
        List<Follow> follows = list(new QueryWrapper<Follow>().eq("user_id", userId));
        if (follows == null || follows.isEmpty()) {
            // 未关注任何用户时Redis不会存在空Set，这里直接返回即可
            return;
        }
        List<String> ids = follows.stream()
                .map(follow -> follow.getFollowUserId().toString())
                .collect(Collectors.toList());
        stringRedisTemplate.opsForSet().add(key, ids.toArray(new String[0]));
    }
}
