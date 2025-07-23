package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.nio.charset.Charset;

/**
 * 健康检查MCP服务主应用类
 * 
 * 这是一个基于Spring Boot的MCP (Model Context Protocol) 服务器应用，
 * 主要功能包括：
 * 1. 提供体检数据查询服务
 * 2. 基于医学知识库的AI分析服务
 * 3. 医学知识问答服务
 * 
 * MCP协议是AI模型与外部工具交互的标准协议，遵循JSON-RPC 2.0规范
 * 
 * @author MJY
 * @version 1.0
 *
 */
@SpringBootApplication  // Spring Boot主配置注解，启用自动配置、组件扫描等功能
public class HealthCheckMcpApplication {

    /**
     * 应用程序入口点
     * 
     * 执行以下初始化操作：
     * 1. 设置系统字符编码为UTF-8，确保中文字符正确处理
     * 2. 输出编码信息用于调试
     * 3. 启动Spring Boot应用容器
     * 
     * @param args 命令行参数，可用于传递配置参数
     */
    public static void main(String[] args) {
        // 设置JVM系统属性，确保文件读写使用UTF-8编码
        // 这对于处理中文医学术语和用户姓名非常重要
        System.setProperty("file.encoding", "UTF-8");
        
        // 设置JNU（Java Native Interface）编码，影响本地化操作
        System.setProperty("sun.jnu.encoding", "UTF-8");
        
        // 输出当前JVM默认字符集，便于开发调试
        // 在生产环境中可以通过日志级别控制是否输出
        System.out.println("当前默认字符集: " + Charset.defaultCharset());
        System.out.println("当前文件编码: " + System.getProperty("file.encoding"));

        SpringApplication.run(HealthCheckMcpApplication.class, args);
    }
}