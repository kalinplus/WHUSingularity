package com.lubover.singularity.scaler.policy;

import com.lubover.singularity.scaler.model.ScaleAction;
import org.springframework.stereotype.Component;

@Component
public class PolicyEvaluator {

    public ScaleAction evaluate(double qps, double scaleUpThreshold, double scaleDownThreshold,
                                int currentInstances, int minInstances, int maxInstances) {
        if (qps >= scaleUpThreshold && currentInstances < maxInstances) {
            return ScaleAction.SCALE_UP;
        }
        if (qps <= scaleDownThreshold && currentInstances > minInstances) {
            return ScaleAction.SCALE_DOWN;
        }
        return ScaleAction.NONE;
    }
}
