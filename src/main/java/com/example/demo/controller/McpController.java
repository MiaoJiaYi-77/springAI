package com.example.demo.controller;

import com.example.demo.mcp.McpProtocol;
import com.example.demo.mcp.McpTools;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * MCP (Model Context Protocol) 控制器
 * 
 * 提供MCP协议的HTTP接口，支持AI模型通过HTTP调用MCP工具
 * MCP是AI模型与外部工具交互的标准协议，遵循JSON-RPC 2.0规范
 * 
 * 主要功能：
 * 1. 处理MCP协议消息 - 标准的JSON-RPC 2.0格式
 * 2. 提供工具列表查询 - 让AI了解可用工具
 * 3. 提供测试接口 - 方便开发和调试
 * 
 * @author MJY
 * @version 1.0
 */
@RestController
@RequestMapping("/mcp")             // MCP接口基础路径
@CrossOrigin(origins = "*")         // 允许跨域访问，支持不同域名的AI客户端
public class McpController {

    /**
     * MCP协议处理器
     * 负责解析和处理符合MCP规范的JSON-RPC 2.0请求
     */
    @Autowired
    private McpProtocol mcpProtocol;

    /**
     * MCP工具集合
     * 包含所有可供AI模型调用的业务工具
     */
    @Autowired
    private McpTools mcpTools;

    /**
     * 处理MCP协议消息的主接口
     * 
     * 这是MCP协议的核心接口，接收AI模型发送的JSON-RPC 2.0格式请求
     * 支持的MCP方法包括：initialize、tools/list、tools/call等
     * 
     * 请求格式示例：
     * {
     *   "jsonrpc": "2.0",
     *   "method": "tools/call",
     *   "params": {
     *     "name": "get_health_check",
     *     "arguments": {"personName": "张三"}
     *   },
     *   "id": 1
     * }
     * 
     * @param request MCP请求，必须符合JSON-RPC 2.0规范
     * @return JSON-RPC 2.0格式的响应，包含结果或错误信息
     */
    @PostMapping("/message")
    public ResponseEntity<?> handleMcpMessage(@RequestBody Map<String, Object> request) {
        try {
            // 委托给MCP协议处理器处理具体的协议逻辑
            Map<String, Object> response = mcpProtocol.handleRequest(request);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            // 发生异常时，返回符合JSON-RPC 2.0规范的错误响应
            return ResponseEntity.badRequest().body(Map.of(
                "jsonrpc", "2.0",                          // JSON-RPC版本
                "id", request.get("id"),                   // 请求ID，用于响应匹配
                "error", Map.of(
                    "code", -32603,                        // 内部错误码（JSON-RPC标准）
                    "message", "Internal error: " + e.getMessage()  // 错误描述
                )
            ));
        }
    }

    /**
     * 获取可用工具列表接口
     * 
     * 提供一个简化的HTTP GET接口来查询所有可用的MCP工具
     * 这个接口主要用于开发调试，让开发者快速了解系统提供的工具
     * 
     * 响应格式：
     * {
     *   "tools": {
     *     "get_health_check": "查询用户体检指标数据",
     *     "analyze_health_check_with_rag": "使用AI和医学知识库分析体检报告",
     *     "ask_medical_question": "向医学知识库提问"
     *   }
     * }
     * 
     * @return 包含所有可用工具及其描述的映射
     */
    @GetMapping("/tools")
    public ResponseEntity<?> listTools() {
        return ResponseEntity.ok(Map.of(
            "tools", Map.of(
                "get_health_check", "查询用户体检指标数据",
                "analyze_health_check_with_rag", "使用AI和医学知识库分析体检报告", 
                "ask_medical_question", "向医学知识库提问"
            )
        ));
    }

    /**
     * 测试MCP工具的简化接口
     * 
     * 这是一个为开发和测试设计的简化接口，不需要完整的JSON-RPC 2.0格式
     * 主要用途：
     * 1. 快速测试工具功能
     * 2. 开发阶段的调试
     * 3. 演示和教学
     * 
     * 与标准MCP接口的区别：
     * - 使用简单的JSON格式而非JSON-RPC 2.0
     * - 直接返回工具执行结果
     * - 更适合人工测试和调试
     * 
     * 请求格式：
     * {
     *   "action": "工具名称",
     *   "param": "参数值"
     * }
     * 
     * @param request 简化的测试请求，包含action和param字段
     * @return 工具执行结果或错误信息
     */
    @PostMapping("/test")
    public ResponseEntity<?> testTool(@RequestBody Map<String, String> request) {
        try {
            // 提取测试参数
            String action = request.get("action");    // 要测试的工具名称
            String param = request.get("param");      // 工具参数（简化为单个字符串）
            
            // 参数验证
            if (action == null || action.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "action参数不能为空"));
            }
            
            if (param == null || param.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "param参数不能为空"));
            }
            
            // 根据action路由到对应的工具
            // 这里使用switch表达式简化代码结构
            String result = switch (action) {
                case "get_health_check" -> 
                    // 测试体检数据查询工具
                    mcpTools.getHealthCheck(param);
                    
                case "analyze_health_check_with_rag" -> 
                    // 测试RAG体检分析工具
                    mcpTools.analyzeHealthCheckWithRAG(param);
                    
                case "ask_medical_question" -> {
                    // 测试医学知识问答工具
                    // 注意：这里使用RAG问答方法获取更详细的结果
                    Map<String, Object> ragResult = mcpTools.askWithRAG(param);
                    yield (String) ragResult.get("answer");  // 只返回答案部分
                }
                
                default -> 
                    // 处理未知的工具名称
                    "未知操作: " + action + "。支持的操作：get_health_check, analyze_health_check_with_rag, ask_medical_question";
            };
            
            // 返回成功响应
            return ResponseEntity.ok(Map.of("result", result));
            
        } catch (Exception e) {
            // 捕获异常并返回错误响应
            // 使用400状态码表示客户端请求错误
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}