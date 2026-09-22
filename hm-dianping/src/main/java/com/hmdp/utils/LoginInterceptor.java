package com.hmdp.utils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Resource
    StringRedisTemplate stringRedisTemplate;


//     session 保存的用户信息判断登录状态
//    @Override
//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        //1.获取请求的session
//        User user = (User) request.getSession().getAttribute("user");
//        if (user == null) {
//            //2.未登录则返回未登录结果
//            response.setStatus(401);
//            return false;
//        }
//        UserHolder.saveUser((User) user);
//        return true;
//    }

    /*使用redis保存用户的信息判断登录状态*/
//    @Override
//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        //更新redis有效信息
//        //1.获取token
//        String token = request.getHeader("Authorization");
//        if (StrUtil.isBlank(token)){
//            //2.未登录则返回未登录结果
//            response.setStatus(401);
//            return false;
//        }
//        //3.从redis中获取用户token
//        log.info("REDISKEY{}",  LOGIN_USER_KEY+token);
//        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
//        User user = BeanUtil.toBean(userMap, User.class);
//        //4.保存用户信息
//        UserHolder.saveUser(user);
//        stringRedisTemplate.expire(LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);
//        return true;
//    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        User user = UserHolder.getUser();
        if (user == null) {
            response.setStatus(401);
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }

}


