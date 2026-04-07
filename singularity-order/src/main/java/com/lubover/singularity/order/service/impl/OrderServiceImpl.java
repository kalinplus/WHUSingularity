package com.lubover.singularity.order.service.impl;

import com.lubover.singularity.api.*;
import com.lubover.singularity.api.impl.DefaultAllocator;
import com.lubover.singularity.order.service.OrderService;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {

    private final Allocator allocator;

    public OrderServiceImpl(Registry registry,
                            ShardPolicy shardPolicy,
                            @Autowired(required = false) List<Interceptor> interceptors,
                            StringRedisTemplate redisTemplate,
                            RocketMQTemplate rocketMQTemplate) {

        Interceptor handler = context -> {
            Actor actor = context.getCurrActor();
            Slot slot = context.getCurrSlot();
            String orderId = UUID.randomUUID().toString();

            // 1. 构造 RocketMQ 事务消息
            Message<String> msg = MessageBuilder.withPayload(orderId)
                    .setHeader("orderId", orderId)
                    .setHeader("actorId", actor.getId())
                    .setHeader("slotId", slot.getId())
                    .build();

            // 2. 发送半消息，由 OrderTransactionListener 执行本地事务（Redis 扣库存 + 写订单）
            TransactionSendResult sendResult = rocketMQTemplate.sendMessageInTransaction(
                    "order-topic", msg, null);

            // 3. 根据本地事务执行结果设置 Result
            if (sendResult.getLocalTransactionState()
                    == org.apache.rocketmq.client.producer.LocalTransactionState.COMMIT_MESSAGE) {
                context.setResult(new Result(true, orderId));
            } else {
                context.setResult(new Result(false, "transaction rolled back for slot: " + slot.getId()));
            }
        };

        this.allocator = new DefaultAllocator(
                registry, shardPolicy,
                interceptors != null ? interceptors : Collections.emptyList(),
                handler);
    }

    @Override
    public Result snagOrder(Actor actor) {
        return allocator.allocate(actor);
    }
}
