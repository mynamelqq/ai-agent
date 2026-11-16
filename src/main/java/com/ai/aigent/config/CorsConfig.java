package com.ai.aigent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowCredentials(true)
                .allowedOriginPatterns("*")  // 或使用 .allowedOrigins("https://agent-fronter.vercel.app")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")  // 全部大写
                .allowedHeaders("*")
                .exposedHeaders("*")
                .maxAge(3600);  // 添加预检请求缓存时间
    }
}
