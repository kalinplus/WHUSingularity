package com.lubover.singularity.scaler.config;

import lombok.Data;

import java.util.Map;

@Data
public class ServiceConfig {
    private String name;
    private int basePort;
    private int portStep;
    private int minInstances;
    private int maxInstances;
    private double qpsScaleUpThreshold;
    private double qpsScaleDownThreshold;
    private String image;
    private Map<String, String> env;
}
