package com.example.relay.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.relay.common.BizException;
import com.example.relay.common.JwtUtil;
import com.example.relay.dto.LoginRequest;
import com.example.relay.dto.RegisterRequest;
import com.example.relay.entity.User;
import com.example.relay.mapper.UserMapper;
import com.example.relay.vo.LoginVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 注册和登录。
 *
 * <p>两件事都只碰 users 表，逻辑很短 —— 但有两个安全细节不能省：
 * 密码进库前必须哈希；登录失败不能透露「是用户名不存在还是密码错了」。
 */
@Service
public class AuthService {

    private static final String DEFAULT_ROLE = "USER";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public String register(RegisterRequest req) {

        String username = req.getUsername().trim();

        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (exists != null && exists > 0) {
            throw new BizException(400, "用户名已被占用：" + username);
        }

        User user = new User();
        user.setUsername(username);
        // ★ 只存哈希，明文密码内存里用完就丢，任何地方都不落盘
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setDisplayName(isBlank(req.getDisplayName()) ? username : req.getDisplayName().trim());
        user.setRole(DEFAULT_ROLE);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 先查后插之间被人抢先注册了 —— 唯一键 uk_username 是最后一道防线
            throw new BizException(400, "用户名已被占用：" + username);
        }

        return "注册成功：" + username;
    }

    public LoginVO login(LoginRequest req) {

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername().trim()));

        // 用户不存在 和 密码错误 返回同一句话：
        // 分开说等于告诉攻击者「这个用户名存在」，可以拿去撞库
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BizException(401, "用户名或密码错误");
        }

        String token = jwtUtil.generate(user.getUsername(), user.getRole());
        return new LoginVO(token, user.getUsername(), user.getDisplayName(), user.getRole());
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
