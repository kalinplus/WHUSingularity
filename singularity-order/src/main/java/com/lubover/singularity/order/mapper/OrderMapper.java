package com.lubover.singularity.order.mapper;

import com.lubover.singularity.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 订单 Mapper 接口
 */
@Mapper
public interface OrderMapper {

    /**
     * 插入订单记录
     * 
     * @param order 订单实体
     * @return 影响的行数
     */
    int insert(Order order);

    /**
     * 根据订单ID查询订单
     * 
     * @param orderId 订单ID
     * @return 订单实体，不存在则返回 null
     */
    Order selectByOrderId(@Param("orderId") String orderId);

    /**
     * 更新订单状态
     * 
     * @param orderId 订单ID
     * @param status 新状态
     * @return 影响的行数
     */
    int updateStatus(@Param("orderId") String orderId, @Param("status") String status);
}
