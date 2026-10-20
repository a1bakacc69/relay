package com.example.relay.Controller;

import com.example.relay.common.R;
import com.example.relay.dto.LoginRequest;
import com.example.relay.dto.RegisterRequest;
import com.example.relay.service.AuthService;
import com.example.relay.vo.LoginVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录注册入口。
 *
 * <p>这两个接口是 SecurityConfig 里唯二不要求 token 的业务接口 ——
 * 用户还没登录，不可能带着 token 来。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * 注册。
     *
     * <pre>POST /auth/register
     * {"username":"admin","password":"123456","displayName":"左容旭"}</pre>
     */
    @PostMapping("/register")
    public R<String> register(@Valid @RequestBody RegisterRequest req) {
        return R.ok(authService.register(req));
    }

    /**
     * 登录，成功返回 JWT。
     *
     * <p>之后的请求都要带上：<code>Authorization: Bearer &lt;token&gt;</code>
     */
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginRequest req) {
        return R.ok(authService.login(req));
    }
}
