package com.hmdp.service;

import com.hmdp.entity.TbBlogLike;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 罗胜
 * @since 2026-09-27
 */
public interface ITbBlogLikeService extends IService<TbBlogLike> {


    List<TbBlogLike> getLikeUserByBlogId(Long id);
}
