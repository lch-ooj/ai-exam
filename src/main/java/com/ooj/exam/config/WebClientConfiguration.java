package com.ooj.exam.config;

import com.ooj.exam.properties.QwenProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;



@Configuration
public class WebClientConfiguration {

    @Autowired
    private QwenProperties qwenProperties;

    @Bean
    public WebClient webClient(){
        return WebClient.builder()
                .baseUrl(qwenProperties.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Authorization", "Bearer " + qwenProperties.getApiKey())
                .build();
    }
}
