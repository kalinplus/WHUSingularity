package com.lubover.singularity.order.listener;

import com.lubover.singularity.order.entity.Order;
import com.lubover.singularity.order.mapper.OrderMapper;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 消费已提交的订单消息，异步落库到 MySQL
 * payload 格式为 orderId（由事务消息确认后投递）
 */
@Component
@RocketMQMessageListener(topic = "order-topic", consumerGroup = "order-consumer-group")
public class OrderConsumer implements RocketMQListener<String> {

    private final OrderMapper orderMapper;

    public OrderConsumer(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public void onMessage(String orderId) {
        // 防重：如果数据库中已有该订单则跳过
        if (orderMapper.selectByOrderId(orderId) != null) {
            return;
        }

        Order order = new Order();
        order.setOrderId(orderId);
        order.setStatus(1);
        order.setCreateTime(LocalDateTime.now());
        orderMapper.insert(order);
    }
}
