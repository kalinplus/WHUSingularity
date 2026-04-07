package com.lubover.singularity.order.mapper;

import com.lubover.singularity.order.entity.Order;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderMapper {

    @Insert("INSERT INTO t_order (order_id, actor_id, slot_id, status, create_time) " +
            "VALUES (#{orderId}, #{actorId}, #{slotId}, #{status}, #{createTime})")
    int insert(Order order);

    @Select("SELECT id, order_id, actor_id, slot_id, status, create_time " +
            "FROM t_order WHERE order_id = #{orderId}")
    Order selectByOrderId(String orderId);
}
