package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.ScrollResult;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.TbBlogLike;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.*;

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
    @Resource
    private IFollowService followService;

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

    /**
     * 点赞
     * @param id  blog id
     */
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

    /**
     * 判断当前用户是否已经点赞
     * @param id  blog id
     * @return  true:已点赞 false:未点赞
     */
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

    @Override
    public Long saveBlog(Blog blog) {
        // 获取登录用户
        User user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        LocalDateTime now = LocalDateTime.now();
        blog.setCreateTime(now);
        save(blog);
        //推送给粉丝
        //1.获取粉丝列表
        List<Long> fans = followService.query()
                .in("follow_user_id", user.getId())
                .list()
                .stream()
                .map(Follow::getUserId)
                .collect(Collectors.toList());
        if (fans.isEmpty()) {
            return null;
        }
        long score = blog.getCreateTime().toInstant(ZoneOffset.of("+8")).toEpochMilli();
        fans.forEach(fan -> {
            stringRedisTemplate.opsForZSet().add(FEED_KEY + fan, blog.getId().toString(), (double) score);
        });
        // 返回id
        return blog.getId();
    }

    /**
     * 查询关注的博主的博文
     * @param max   最大时间
     * @param offset  偏移量
     * @return  博文列表
     */
//    @Override
//    public Object queryBlogOfFollow(Long max, Long offset) {
//        // 1.获取当前用户
//        Long userId = UserHolder.getUser().getId();
//        // 2.查询收件箱 ZREVRANGEBYSCORE key max min LIMIT offset count
//        String key = FEED_KEY + userId;
//        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
//                .reverseRangeByScoreWithScores(key, 0, max, offset, 10);
//        // 3.非空判断
//        if (typedTuples == null || typedTuples.isEmpty()) {
//            return null;
//        }
//        // 4.解析数据：blogId、minTime（时间戳）、offset
//        List<Long> ids = new ArrayList<>(typedTuples.size());
//        long minTime = 0;
//        int os = 1;
//        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
//            // 4.1.获取id
//            ids.add(Long.valueOf(tuple.getValue()));
//            // 4.2.获取分数(时间戳)
//            long time = tuple.getScore().longValue();
//            if (time == minTime) {
//                os++;
//            } else {
//                minTime = time;
//                os = 1;
//            }
//        }
//        // 5.根据id查询blog
//        String idStr = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
//        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
//        for (Blog blog : blogs){
//            blog.setIsLike(isLikedByUser(blog.getId()));
//            User user = userService.getById(blog.getUserId());
//            blog.setName(user.getNickName());
//            blog.setIcon(user.getIcon());
//        }
//        // 6.封装并返回
//        ScrollResult result = new ScrollResult();
//        result.setList(blogs);
//        result.setMinTime(minTime);
//        result.setOffset(os);
//        return result;
//    }
// ... existing code ...
    @Override
    public Object queryBlogOfFollow(Long max, Long offset) {
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2.查询收件箱 ZREVRANGEBYSCORE key max min LIMIT offset count
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 10);
        // 3.非空判断，Redis无数据则回源数据库
        List<Long> ids;
        long minTime;
        int os;
        if (typedTuples == null || typedTuples.isEmpty()) {
            // 3.1 查询当前用户关注的人
            List<Long> followUserIds = followService.query()
                    .eq("user_id", userId)
                    .list()
                    .stream()
                    .map(Follow::getFollowUserId)
                    .collect(Collectors.toList());
            if (followUserIds.isEmpty()) {
                return null;
            }
            // 3.2 从数据库查询这些用户发布的博客，按创建时间倒序
            LocalDateTime maxTime = max == 0 ? LocalDateTime.now()
                    : LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(max), ZoneOffset.of("+8"));
            List<Blog> blogs = query()
                    .in("user_id", followUserIds)
                    .lt("create_time", maxTime)
                    .orderByDesc("create_time")
                    .last("LIMIT " + offset + ", 10")
                    .list();
            if (blogs.isEmpty()) {
                return null;
            }
            // 3.3 封装结果
            ids = blogs.stream().map(Blog::getId).collect(Collectors.toList());
            minTime = blogs.get(blogs.size() - 1).getCreateTime()
                    .toInstant(ZoneOffset.of("+8")).toEpochMilli();
            os = 1;
            // 3.4 回填Redis（可选，加速下次查询）
            blogs.forEach(blog -> {
                long score = blog.getCreateTime().toInstant(ZoneOffset.of("+8")).toEpochMilli();
                stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), (double) score);
            });
            stringRedisTemplate.expire(key, 30, TimeUnit.DAYS);
        } else {
            // 4.Redis有数据，正常解析
            ids = new ArrayList<>(typedTuples.size());
            minTime = 0;
            os = 1;
            for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
                ids.add(Long.valueOf(tuple.getValue()));
                long time = tuple.getScore().longValue();
                if (time == minTime) {
                    os++;
                } else {
                    minTime = time;
                    os = 1;
                }
            }
        }
        // 5.根据id查询blog详情
        String idStr = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Blog blog : blogs) {
            blog.setIsLike(isLikedByUser(blog.getId()));
            User user = userService.getById(blog.getUserId());
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
        }
        // 6.封装并返回
        ScrollResult result = new ScrollResult();
        result.setList(blogs);
        result.setMinTime(minTime);
        result.setOffset(os);
        return result;
    }


}
