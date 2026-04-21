package com.lubover.singularity.order.consumer;

import com.lubover.singularity.order.entity.Order;
import com.lubover.singularity.order.mapper.OrderMapper;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 订单消息消费者
 *
 * <p>
 * 监听 order-topic，从 RocketMQ 消费订单消息，将订单数据从 Redis 读取后落库到 MySQL。
 *
 * <p>
 * 功能：
 * <ol>
 * <li>监听 RocketMQ 的 order-topic</li>
 * <li>消费订单消息（orderId）</li>
 * <li>从 Redis 读取订单详情（order:{orderId}）</li>
 * <li>插入 MySQL 订单表</li>
 * <li>实现幂等性（主键冲突时忽略）</li>
 * </ol>
 */
@Service
@RocketMQMessageListener(
        topic = "order-topic",
        consumerGroup = "order-consumer-group"
)
public class OrderConsumerService implements RocketMQListener<String> {

    private static final Logger log = LoggerFactory.getLogger(OrderConsumerService.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private OrderMapper orderMapper;

    @Override
    public void onMessage(String orderId) {
        try {
            log.info("Received order message: orderId={}", orderId);

            // Step 1: 从 Redis 读取订单详情
            String orderKey = "order:" + orderId;
            Map<Object, Object> orderData = redisTemplate.opsForHash().entries(orderKey);

            if (orderData == null || orderData.isEmpty()) {
                log.warn("Order not found in Redis: orderId={}", orderId);
                return;
            }

            String userId = (String) orderData.get("actorId");
            String slotId = (String) orderData.get("slotId");
            String status = (String) orderData.get("status");
            String createTimeStr = (String) orderData.get("createTime");

            if (orderId == null || userId == null || slotId == null) {
                log.error("Invalid order data in Redis: orderId={}, data={}", orderId, orderData);
                return;
            }

            // Step 2: 构造订单实体
            Order order = new Order();
            order.setOrderId(orderId);
            order.setUserId(userId);
            order.setSlotId(slotId);
            order.setStatus("CREATED"); // 固定为 CREATED 状态
            order.setCreateTime(createTimeStr != null ? LocalDateTime.parse(createTimeStr) : LocalDateTime.now());
            order.setUpdateTime(LocalDateTime.now());

            // Step 3: 插入数据库（幂等性由主键约束保证）
            try {
                int rows = orderMapper.insert(order);
                if (rows > 0) {
                    log.info("Order persisted to database: orderId={}, userId={}, slotId={}",
                            orderId, userId, slotId);
                } else {
                    log.warn("Order insert failed (0 rows affected): orderId={}", orderId);
                }
            } catch (DuplicateKeyException e) {
                // 主键冲突，说明订单已经存在（重复消费），忽略即可
                log.info("Order already exists in database (duplicate message ignored): orderId={}", orderId);
            }

        } catch (Exception e) {
            log.error("Failed to process order message: orderId={}", orderId, e);
            // 抛出异常让 RocketMQ 重试
            throw new RuntimeException("Order processing failed: " + orderId, e);
        }
    }
}
