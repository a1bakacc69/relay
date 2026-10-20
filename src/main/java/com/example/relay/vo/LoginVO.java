package com.example.relay.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录成功后返回给前端的东西。
 *
 * <p><b>token 之外的信息是顺手给前端的</b>：界面右上角要显示名字、
 * 菜单要按角色显示不同的项，前端不该为了这两样再查一次接口。
 */
@Data
@AllArgsConstructor
public class LoginVO {

    /** 之后每个请求放在 Authorization: Bearer <token> 里 */
    private String token;

    private String username;

    private String displayName;

    /** ADMIN / USER */
    private String role;
}
