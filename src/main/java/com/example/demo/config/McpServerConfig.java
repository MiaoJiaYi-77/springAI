package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MCP服务器配置类
 * 
 * 配置跨域访问策略，允许MCP客户端从不同域名访问服务器
 * MCP (Model Context Protocol) 是AI模型与外部工具交互的协议
 * 
 * @author MJY
 * @version 1.0
 */
@Configuration
public class McpServerConfig implements WebMvcConfigurer {

    /**
     * 配置CORS跨域映射
     * 
     * 允许所有来源访问 /mcp/** 路径下的接口
     * 这是为了让AI模型客户端能够跨域调用MCP工具
     * 
     * @param registry CORS注册表
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/mcp/**")                    // 匹配所有MCP相关路径
                .allowedOrigins("*")                      // 允许所有来源域名
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")  // 允许的HTTP方法
                .allowedHeaders("*");                     // 允许所有请求头
    }
}