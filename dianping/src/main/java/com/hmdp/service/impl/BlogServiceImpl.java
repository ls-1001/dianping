package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.Blog;
import com.hmdp.entity.TbBlogLike;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.ITbBlogLikeService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ITbBlogLikeService tbBlogLikeService;
    @Resource
    private IUserService userService;

    @Override
    public Blog getBlogById(Long id) {
        // 根据id查询blog
        Blog blog = getById(id);
        if (blog == null) {
            return null;
        }
        // 查询blog关联的用户
        User user = userService.getById(blog.getUserId());
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
        blog.setIsLike(this.isLikedByUser(id));
        return blog;
    }

    @Override
    public void likeBlog(Long id) {
        //获取当前用户
        User user = UserHolder.getUser();

        //从redis中查询当前用户是否已经点赞
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id, user.getId().toString());

        if (score != null) {
            // 如果用户已经点赞，则取消点赞
            stringRedisTemplate.opsForZSet().remove(BLOG_LIKED_KEY + id, user.getId().toString());
            tbBlogLikeService.remove(new QueryWrapper<TbBlogLike>().eq("blog_id", id).eq("user_id", user.getId()));
        } else {
            //数据库查询
            List<TbBlogLike> likeUser = tbBlogLikeService.getLikeUserByBlogId(id);
            if (likeUser.isEmpty()){
                //获取当前时间
                LocalDateTime now = LocalDateTime.now();
                // 如果用户没有点赞，则点赞
                stringRedisTemplate.opsForZSet().add(BLOG_LIKED_KEY + id, user.getId().toString(), now.toInstant(ZoneOffset.of("+8")).toEpochMilli());
                //TODO REDIS 点赞数据定时写入数据库  （tb_bloglike,  blog的liked字段）
                //给这个key设置过期时间，例：7天过期
                stringRedisTemplate.expire(BLOG_LIKED_KEY + id,7, TimeUnit.DAYS);
                return ;
            }
            //将查询的数据保存到redis
            Set<ZSetOperations.TypedTuple<String>> typedTuples = likeUser.stream()
                    .map(like -> {
                        long likeTime = like.getCreateTime().toInstant(ZoneOffset.of("+8")).toEpochMilli();
                        return new DefaultTypedTuple<>(like.getUserId().toString(), (double) likeTime);
                    }).collect(Collectors.toSet());
            stringRedisTemplate.opsForZSet().add(BLOG_LIKED_KEY + id, typedTuples);
            stringRedisTemplate.expire(BLOG_LIKED_KEY + id, 7, TimeUnit.DAYS);
        }
    }

    @Override
    public boolean isLikedByUser(Long id) {
        //获取当前用户
        User user = UserHolder.getUser();
        //从redis中查询当前用户是否已经点赞
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id, user.getId().toString());
        if (score == null) {
            return tbBlogLikeService.query().eq("blog_id", id).eq("user_id", user.getId()).count() > 0;
        }
        return true;
    }

    /**
     * 获取点赞用户
     * @param id   blog id
     * @return    点赞用户列表
     */
    @Override
    public Object likesBlog(Long id) {
        //从redis中获取点赞用户
        Set<String> range = stringRedisTemplate.opsForZSet().range(BLOG_LIKED_KEY + id, 0, 9);
        if (range == null || range.isEmpty()) {
            return null;
        }
        List<User> users = userService.getByIds(range);
        //封装到userDTO
        return users.stream().map(user -> {
            User userDTO = new User();
            userDTO.setId(user.getId());
            userDTO.setNickName(user.getNickName());
            userDTO.setIcon(user.getIcon());
            return userDTO;
        }).collect(Collectors.toList());
    }
}
