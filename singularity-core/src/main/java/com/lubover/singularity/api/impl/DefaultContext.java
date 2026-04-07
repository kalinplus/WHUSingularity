package com.lubover.singularity.api.impl;

import com.lubover.singularity.api.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultContext implements Context {

    private final Map<String, Object> values = new HashMap<>();
    private final Actor actor;
    private final Slot slot;
    private Result result;
    private final List<Interceptor> chain;
    private int index = -1;

    public DefaultContext(Actor actor, Slot slot, List<Interceptor> chain) {
        this.actor = actor;
        this.slot = slot;
        this.chain = chain;
    }

    @Override
    public Object getValue(String key) {
        return values.get(key);
    }

    @Override
    public void withValue(String key, Object obj) {
        values.put(key, obj);
    }

    @Override
    public Actor getCurrActor() {
        return actor;
    }

    @Override
    public Slot getCurrSlot() {
        return slot;
    }

    @Override
    public Result getResult() {
        return result;
    }

    @Override
    public void setResult(Result result) {
        this.result = result;
    }

    @Override
    public void next() {
        index++;
        if (index < chain.size()) {
            chain.get(index).handle(this);
        }
    }
}
