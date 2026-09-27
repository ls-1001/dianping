package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    /**
     * 关注或取消关注目标用户
     *
     * @param followUserId 目标用户id
     * @param isFollow     true关注，false取消关注
     */
    @PutMapping("/{followUserId}/{isFollow}")
    public Result follow(@PathVariable("followUserId") Long followUserId,
                         @PathVariable("isFollow") Boolean isFollow) {
        return followService.follow(followUserId, isFollow);
    }

    /**
     * 判断当前登录用户是否已关注目标用户
     *
     * @param followUserId 目标用户id
     */
    @GetMapping("/or/not/{followUserId}")
    public Result isFollow(@PathVariable("followUserId") Long followUserId) {
        return followService.isFollow(followUserId);
    }

    /**
     * 查询当前登录用户与目标用户的共同关注列表
     *
     * @param userId 目标用户id
     */
    @GetMapping("/common/{userId}")
    public Result followCommons(@PathVariable("userId") Long userId) {
        return followService.followCommons(userId);
    }
}
