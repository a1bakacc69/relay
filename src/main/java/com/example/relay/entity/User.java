package com.example.relay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台用户。
 * 对应表：users
 *
 * <p><b>密码字段存的是 BCrypt 哈希，不是明文。</b>
 * BCrypt 每次加密都会随机加盐，同一个密码两次算出来的哈希也不一样，
 * 所以校验只能靠 {@code encoder.matches(明文, 哈希)}，不能拿哈希去比对。
 *
 * <p>哈希长度固定 60 字符，但列宽留到 100 —— 将来从 BCrypt 换别的算法不用改表。
 */
@Data
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录名。唯一键 uk_username 保证不重 */
    private String username;

    /** ★ BCrypt 哈希，形如 $2a$10$...，绝不存明文 */
    private String passwordHash;

    /** 显示名，界面上展示用，可以为空 */
    private String displayName;

    /** 角色：ADMIN / USER。目前只登录不鉴权，留着以后限制写操作 */
    private String role;

    private LocalDateTime createTime;

    @TableField(update = "now()")
    private LocalDateTime updateTime;
}
