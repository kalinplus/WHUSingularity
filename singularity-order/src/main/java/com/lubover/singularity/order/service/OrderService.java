package com.lubover.singularity.order.service;

import com.lubover.singularity.api.Actor;
import com.lubover.singularity.api.Result;

public interface OrderService {

    /**
     * snagOrder 为一个 actor 抢占一个 slot 中的资源（下单）
     * @param actor 发起抢占的用户
     * @return 抢占结果
     */
    Result snagOrder(Actor actor);
}
