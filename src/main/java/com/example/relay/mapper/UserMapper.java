package com.example.relay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.relay.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * users 表。
 *
 * <p>注册查重、登录查人，两个都是按 username 等值查询，
 * LambdaQueryWrapper 就够了，不需要手写 SQL。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
