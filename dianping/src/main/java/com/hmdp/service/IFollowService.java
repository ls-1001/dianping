package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注或取消关注
     *
     * @param followUserId 目标用户id
     * @param isFollow     true关注，false取消关注
     * @return 操作结果
     */
    Result follow(Long followUserId, Boolean isFollow);

    /**
     * 判断当前用户是否已关注目标用户
     *
     * @param followUserId 目标用户id
     * @return data为true表示已关注，false表示未关注
     */
    Result isFollow(Long followUserId);

    /**
     * 查询当前用户与目标用户的共同关注
     *
     * @param userId 目标用户id
     * @return data为共同关注的用户列表
     */
    Result followCommons(Long userId);
}
