package com.lubover.singularity.scaler.metrics;

import com.lubover.singularity.scaler.config.ScalerProperties;
import com.lubover.singularity.scaler.config.ServiceConfig;
import com.lubover.singularity.scaler.discovery.InstanceDiscovery;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsScraper {

    private final InstanceDiscovery instanceDiscovery;
    private final PrometheusTextParser prometheusTextParser;
    private final ScalerProperties scalerProperties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final Map<String, MetricSnapshot> previousSnapshots = new ConcurrentHashMap<>();

    public double scrapeQps(String serviceName) {
        var instances = instanceDiscovery.getHealthyInstances(serviceName);
        if (instances.isEmpty()) {
            log.warn("No healthy instances found for {}", serviceName);
            return 0.0;
        }
        Instance instance = instances.get(0);
        String url = String.format("http://%s:%d/actuator/prometheus", instance.getIp(), instance.getPort());
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Failed to scrape metrics from {}: status {}", url, response.statusCode());
                return 0.0;
            }
            var metrics = prometheusTextParser.parse(response.body());
            double totalCount = prometheusTextParser.extractRate(metrics, "http_server_requests_seconds_count");
            long now = System.currentTimeMillis();
            String key = serviceName + "@" + instance.getIp() + ":" + instance.getPort();
            MetricSnapshot prev = previousSnapshots.put(key, new MetricSnapshot(now, totalCount));
            if (prev == null) {
                return 0.0;
            }
            double delta = totalCount - prev.getValue();
            double seconds = (now - prev.getTimestamp()) / 1000.0;
            if (seconds <= 0) {
                return 0.0;
            }
            double qps = delta / seconds;
            log.info("Scraped QPS for {}: {} (count={}, delta={}, seconds={})", serviceName, qps, totalCount, delta, seconds);
            return qps;
        } catch (Exception e) {
            log.warn("Failed to scrape metrics from {}: {}", url, e.getMessage());
            return 0.0;
        }
    }

    public ServiceConfig getServiceConfig(String serviceName) {
        if (scalerProperties.getServices() == null) {
            return null;
        }
        return scalerProperties.getServices().stream()
                .filter(s -> s.getName().equals(serviceName))
                .findFirst()
                .orElse(null);
    }
}
