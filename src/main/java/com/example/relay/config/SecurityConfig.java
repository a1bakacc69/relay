package com.example.relay.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;

/**
 * 安全配置：谁能不登录就访问、token 从哪儿读、被拦下来时回什么。
 *
 * <p><b>放行清单只有这里一处</b>，改的时候不会漏。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter)
            throws Exception {

        http
                // 前后端分离 + Bearer token，不用 cookie，CSRF 攻击无从下手，可以关掉
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 每个请求自带 token，服务端不存 session
                // —— 这也是能水平扩容的前提：Vercel 每次可能是不同实例
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // 登录注册：还没拿到 token，必须放行
                        .requestMatchers("/auth/login", "/auth/register").permitAll()
                        // Shopify 推过来的，它没有我们的 token，安全性靠 HMAC 验签保证
                        .requestMatchers("/webhook/**").permitAll()
                        // 容器里异常会转发到 /error，被拦的话真实错误就看不见了
                        .requestMatchers("/error").permitAll()
                        // 其余全部要登录
                        .anyRequest().authenticated())

                .exceptionHandling(e -> e.authenticationEntryPoint(
                        (request, response, ex) -> writeUnauthorized(response)))

                // JWT 过滤器放在用户名密码过滤器之前 —— 我们的认证方式替掉它
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 被拦下来时返回的 JSON。
     *
     * <p>不用默认的 403 空白页：前端 axios 拦截器统一看 body 里的 code，
     * 回一段标准结构它才能把「请重新登录」弹出来。
     */
    private void writeUnauthorized(HttpServletResponse response) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of(
                "code", 401,
                "msg", "未登录或登录已过期"
        ));
    }

    /** BCrypt：自带随机盐，同一个密码每次哈希都不同，校验只能走 matches */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 顶掉 Spring Security 自动配的那个内存用户。
     *
     * <p>不定义它的话，启动日志会打一串随机密码，还平白多出一个能走表单登录的账号。
     * 我们的认证只走 JWT，这里永远查不到人 —— 登录接口也不经过它。
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("本系统不支持表单登录，请走 /auth/login");
        };
    }

    /**
     * 跨域配置。
     *
     * <p>本地前端是 localhost:5173，线上是 relay-web 和 hopeyk.xyz，
     * 环境一多写死清单容易漏，这里用 allowedOriginPatterns 放行。
     * 因为认证走 Authorization 头而不是 cookie，不需要 allowCredentials，
     * 放宽不会带来 CSRF 风险。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
