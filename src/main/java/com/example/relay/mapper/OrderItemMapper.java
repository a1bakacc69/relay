package com.example.relay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.relay.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
}
