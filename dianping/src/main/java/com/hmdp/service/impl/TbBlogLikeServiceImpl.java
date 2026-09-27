package com.hmdp.service.impl;

import com.hmdp.entity.TbBlogLike;
import com.hmdp.mapper.TbBlogLikeMapper;
import com.hmdp.service.ITbBlogLikeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 罗胜
 * @since 2026-09-27
 */
@Service
public class TbBlogLikeServiceImpl extends ServiceImpl<TbBlogLikeMapper, TbBlogLike> implements ITbBlogLikeService {

    /**
     * 根据博文id获取点赞用户
     *
     * @param id
     * @return
     */
    @Override
    public List<TbBlogLike> getLikeUserByBlogId(Long id) {
        // 根据博文id获取点赞用户
        List<TbBlogLike> likelist = lambdaQuery().eq(TbBlogLike::getBlogId, id).list();
        return likelist;
    }
}
