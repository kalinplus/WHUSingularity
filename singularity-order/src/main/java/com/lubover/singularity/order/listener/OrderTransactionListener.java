package com.lubover.singularity.order.listener;

import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * RocketMQ 事务消息监听器
 * 在半消息发送成功后执行本地事务：Redis 扣库存 + 写订单记录
 * 提供回查接口供 RocketMQ Broker 在半消息长时间未确认时回调
 */
@RocketMQTransactionListener
public class OrderTransactionListener implements RocketMQLocalTransactionListener {

    private final StringRedisTemplate redisTemplate;

    public OrderTransactionListener(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        String orderId = (String) msg.getHeaders().get("orderId");
        String actorId = (String) msg.getHeaders().get("actorId");
        String slotId = (String) msg.getHeaders().get("slotId");

        try {
            // 1. 原地减库存
            Long remaining = redisTemplate.opsForValue().decrement("stock:" + slotId);
            if (remaining == null || remaining < 0) {
                // 库存不足，回滚
                redisTemplate.opsForValue().increment("stock:" + slotId);
                return RocketMQLocalTransactionState.ROLLBACK;
            }

            // 2. 在 Redis 写入订单记录（作为唯一信源，供回查使用）
            Map<String, String> orderInfo = new HashMap<>();
            orderInfo.put("orderId", orderId);
            orderInfo.put("actorId", actorId);
            orderInfo.put("slotId", slotId);
            orderInfo.put("status", "1");
            orderInfo.put("createTime", LocalDateTime.now().toString());
            redisTemplate.opsForHash().putAll("order:" + orderId, orderInfo);

            return RocketMQLocalTransactionState.COMMIT;
        } catch (Exception e) {
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    /**
     * 回查本地事务状态，Broker 在半消息长时间未确认时回调此方法
     * 通过检查 Redis 中是否存在对应订单来判定事务状态
     */
    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        String orderId = (String) msg.getHeaders().get("orderId");
        Boolean exists = redisTemplate.hasKey("order:" + orderId);
        return Boolean.TRUE.equals(exists)
                ? RocketMQLocalTransactionState.COMMIT
                : RocketMQLocalTransactionState.ROLLBACK;
    }
}
