package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendcode(String phone, HttpSession session) {
        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式无效");
        }
        //输出session
        log.debug(String.valueOf(session.getAttribute("code")));
        //生成验证码
        String code = String.valueOf((int) ((Math.random() * 9 + 1) * 100000));
        //session.setAttribute("code", code);     //存入session
        //存入  redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code , LOGIN_CODE_TTL, TimeUnit.MINUTES);  //( key ,value ,有效time ,timeUnit)
        //TODO 发送验证码
        log.debug("发送验证码：" + code + "给：" + phone);
        log.debug(String.valueOf(stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone)));
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //

        //校验手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("手机号格式无效");
        }
        //校验验证码    从session获取验证码
//        if (!loginForm.getCode().equals(session.getAttribute("code"))) {
//            return Result.fail("验证码无效");
//        }
        //从redis获取验证码
        if (loginForm.getCode() != null){
            String code = (String) stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + loginForm.getPhone());
            if (code == null || !code.equals(loginForm.getCode())) {
                return Result.fail("验证码无效");
            }
        }
        //查询用户
        User user = query().eq("phone", loginForm.getPhone()).one();
        if (user == null) {
            //如果不存在则创建用户
            user = new User();
            user.setPhone(loginForm.getPhone());
            user.setNickName("用户" + loginForm.getPhone());
            save(user);
        }
        //将用户信息存入session
        //session.setAttribute("user", user);
        //生成token，
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
        Map<String, Object> usermap = BeanUtil.beanToMap(userDTO);   //转map
        Map<String, String> stringMap = new HashMap<>();
        usermap.forEach((k, v) -> {
            if (v != null) {
                stringMap.put(k, v.toString());
            }
        });
        //用户信息存入redis
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,stringMap);
        stringRedisTemplate.expire(LOGIN_USER_KEY+token, LOGIN_USER_TTL, TimeUnit.SECONDS);  //设置过期时间
        return Result.ok(token);
    }
}
