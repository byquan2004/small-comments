package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.PasswordEncoder;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone) {
        checkThePhone(phone);

        String code = RandomUtil.randomString(6);
        String key = LOGIN_CODE_KEY + phone;
        stringRedisTemplate.opsForValue().set(key, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        log.debug("<<<<<<<<<<<<<<<<<发送验证码成功，验证码：{}", code);

        return Result.ok();
    }

    private static void checkThePhone(String phone) {
        boolean phoneInvalid = RegexUtils.isPhoneInvalid(phone);
        if(phoneInvalid) {
            throw new RuntimeException("手机号 输入错误");
        }
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        checkThePhone(phone);
        String password = loginForm.getPassword();
        String code = loginForm.getCode();
        User entity = lambdaQuery().eq(User::getPhone, phone).one();

        if(entity == null || ObjectUtils.isEmpty( entity)) {
            entity = createWithPhone(phone);
        }
        UserDTO user = new UserDTO();
        BeanUtils.copyProperties(entity,user);
        String token = UUID.randomUUID().toString();
        // 密码登录
        if(StrUtil.isNotBlank(password) && RegexUtils.isPasswordInvalid(password)) {
            boolean matches = PasswordEncoder.matches(password, entity.getPassword());
            if(!matches) {
                throw new RuntimeException("密码错误");
            }
            save2Cache(token, user);
            return Result.ok(token);
        }

        // 验证码登录
        if(StrUtil.isNotBlank(code) && RegexUtils.isCodeInvalid(password)) {
            String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
            boolean matches = code.equals(cacheCode);
            if(!matches) {
                throw new RuntimeException("验证码错误");
            }
            save2Cache(token, user);
            return Result.ok(token);
        }

        return Result.fail("登录失败");
    }

    @Override
    public Result logout(String token) {
        Long userId = UserHolder.getUser().getId();
        User user = getById(userId);
        List<String> list = List.of(
                LOGIN_USER_KEY + token,
                LOGIN_CODE_KEY + user.getPhone()
        );
        stringRedisTemplate.delete(list);
        return Result.ok();
    }

    @Override
    public Result me() {
        Long userId = UserHolder.getUser().getId();
        return Result.ok(getById(userId));
    }

    private void save2Cache(String token, UserDTO user) {
        Map<String, Object> map = BeanUtil.beanToMap(user, new HashMap<>(), CopyOptions.create());
        Map<String, String> stringMap = map.entrySet().stream()
                .collect(
                        java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue() == null ? "" : e.getValue().toString()
                        )
                );
        String key = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(key, stringMap);
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);
    }

    private User createWithPhone(String phone) {
        User user = new User().setPhone(phone)
                .setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        log.info("<<<<<<<<<<<<<<<<<创建用户成功，用户信息：{}", user);
        save( user);
        return user;
    }
}
