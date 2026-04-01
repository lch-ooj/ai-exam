package com.ooj.exam.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "qwen.api")
public class QwenProperties {
    private String baseUrl;
    private String apiKey;
    private String model;
}
