package com.example.demo.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP协议处理器
 * 
 * 实现Model Context Protocol (MCP) 2024-11-05版本规范
 * 负责处理AI模型发送的JSON-RPC 2.0格式请求，并调用相应的工具
 * 
 * MCP协议是连接AI模型和外部工具的桥梁，允许AI模型：
 * 1. 发现可用的工具和资源
 * 2. 调用工具执行特定任务
 * 3. 获取结构化的响应数据
 * 
 * 支持的MCP方法：
 * - initialize: 初始化连接，返回服务器能力信息
 * - tools/list: 列出所有可用工具
 * - tools/call: 调用指定工具执行任务
 * 
 * @author MJY
 * @version 1.0
 *
 */
@Service  // Spring服务层组件，由Spring容器管理生命周期
public class McpProtocol {

    // SLF4J日志记录器，用于记录协议处理过程中的关键信息
    private static final Logger logger = LoggerFactory.getLogger(McpProtocol.class);
    
    // 注入MCP工具集合，包含所有可供AI调用的业务工具
    @Autowired
    private McpTools mcpTools;
    
    /**
     * 处理MCP请求的主入口方法
     * 
     * 这是MCP协议的核心处理逻辑，负责：
     * 1. 解析JSON-RPC 2.0格式的请求
     * 2. 根据method字段分发到对应的处理方法
     * 3. 构建符合规范的响应
     * 4. 处理异常并返回标准错误响应
     * 
     * 所有响应都遵循JSON-RPC 2.0规范，包含：
     * - jsonrpc: 协议版本标识 "2.0"
     * - id: 请求标识符，用于请求响应匹配
     * - result: 成功响应的结果数据
     * - error: 失败响应的错误信息
     * 
     * @param request MCP请求Map，必须包含method、params、id等字段
     * @return JSON-RPC 2.0格式的响应Map
     */
    public Map<String, Object> handleRequest(Map<String, Object> request) {
        try {
            // 从请求中提取核心参数
            String method = (String) request.get("method");        // 请求方法名，如"tools/call"
            Map<String, Object> params = (Map<String, Object>) request.get("params");  // 方法参数
            Object id = request.get("id");                         // 请求ID，响应时必须原样返回
            
            // 记录请求信息，便于调试和监控
            logger.info("处理MCP请求: method={}, id={}", method, id);
            
            // 构建基础响应结构，符合JSON-RPC 2.0规范
            Map<String, Object> response = new HashMap<>();
            response.put("jsonrpc", "2.0");  // 协议版本标识
            response.put("id", id);          // 响应ID必须与请求ID一致
            
            // 根据方法名分发请求到具体的处理逻辑
            switch (method) {
                case "initialize":
                    // 处理初始化请求，返回服务器能力和信息
                    // 这通常是MCP连接建立后的第一个请求
                    response.put("result", handleInitialize());
                    break;
                case "tools/list":
                    // 处理工具列表查询请求
                    // AI模型通过此接口了解可以调用哪些工具
                    response.put("result", handleToolsList());
                    break;
                case "tools/call":
                    // 处理工具调用请求，执行具体的业务逻辑
                    // 这是最重要的方法，实现AI与业务系统的交互
                    response.put("result", handleToolCall(params));
                    break;
                default:
                    // 处理未知方法，返回标准的"方法未找到"错误
                    response.put("error", Map.of(
                        "code", -32601,                    // JSON-RPC标准错误码：方法未找到
                        "message", "Method not found: " + method
                    ));
            }
            
            return response;
            
        } catch (Exception e) {
            // 捕获所有异常，返回标准的内部错误响应
            // 确保即使出现未预期的错误，也能返回符合规范的响应
            logger.error("MCP请求处理异常", e);
            return Map.of(
                "jsonrpc", "2.0",
                "id", request.get("id"),
                "error", Map.of(
                    "code", -32603,                        // JSON-RPC标准错误码：内部错误
                    "message", "Internal error: " + e.getMessage()
                )
            );
        }
    }
    
    /**
     * 处理initialize请求
     * 
     * 初始化是MCP连接建立时的第一步，用于：
     * 1. 协商协议版本
     * 2. 交换服务器能力信息
     * 3. 提供服务器基本信息
     * 
     * 这个方法返回的信息告诉AI模型：
     * - 服务器支持的MCP协议版本
     * - 服务器具备的能力（如工具支持、通知支持等）
     * - 服务器的名称和版本信息
     * 
     * @return 初始化响应Map，包含协议版本、能力和服务器信息
     */
    private Map<String, Object> handleInitialize() {
        return Map.of(
            // MCP协议版本，必须与客户端兼容
            "protocolVersion", "2024-11-05",
            
            // 服务器能力声明
            "capabilities", Map.of(
                "tools", Map.of(
                    // 声明支持工具列表变更通知
                    // 当工具列表发生变化时，服务器可以主动通知客户端
                    "listChanged", true
                )
            ),
            
            // 服务器基本信息
            "serverInfo", Map.of(
                "name", "health-check-mcp-server",        // 服务器名称
                "version", "1.0.0"                        // 服务器版本
            )
        );
    }
    
    /**
     * 处理tools/list请求
     * 
     * 返回所有可用工具的详细信息，让AI模型了解：
     * 1. 有哪些工具可以调用
     * 2. 每个工具的功能描述
     * 3. 每个工具需要什么参数
     * 4. 参数的类型和约束
     * 
     * 工具定义遵循JSON Schema规范，确保AI模型能够：
     * - 理解工具的用途
     * - 正确构造调用参数
     * - 验证参数的有效性
     * 
     * @return 工具列表响应Map，包含所有可用工具的详细定义
     */
    private Map<String, Object> handleToolsList() {
        // 定义所有可用工具的列表
        List<Map<String, Object>> tools = List.of(
            // 工具1：体检数据查询工具
            Map.of(
                "name", "get_health_check",                // 工具唯一标识符
                "description", "查询用户体检指标数据",      // 工具功能描述
                "inputSchema", Map.of(                     // 输入参数JSON Schema
                    "type", "object",                      // 参数类型为对象
                    "properties", Map.of(                  // 对象属性定义
                        "personName", Map.of(              // 参数名：用户姓名
                            "type", "string",              // 参数类型：字符串
                            "description", "用户姓名"       // 参数描述
                        )
                    ),
                    "required", List.of("personName")      // 必需参数列表
                )
            ),
            
            // 工具2：RAG体检分析工具
            Map.of(
                "name", "analyze_health_check_with_rag",
                "description", "使用AI和医学知识库分析体检报告",
                "inputSchema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "personName", Map.of(
                            "type", "string",
                            "description", "用户姓名"
                        )
                    ),
                    "required", List.of("personName")
                )
            ),
            
            // 工具3：医学知识问答工具
            Map.of(
                "name", "ask_medical_question",
                "description", "向医学知识库提问，获取专业医学知识",
                "inputSchema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "question", Map.of(
                            "type", "string",
                            "description", "医学问题"
                        )
                    ),
                    "required", List.of("question")
                )
            )
        );
        
        // 返回工具列表，包装在tools字段中
        return Map.of("tools", tools);
    }
    
    /**
     * 处理tools/call请求
     * 
     * 这是MCP协议中最重要的方法，负责：
     * 1. 解析工具调用参数
     * 2. 验证参数有效性
     * 3. 调用对应的业务工具
     * 4. 返回工具执行结果
     * 
     * 工具调用的流程：
     * 1. 从params中提取工具名称和参数
     * 2. 根据工具名称路由到具体的工具实现
     * 3. 验证必需参数是否存在
     * 4. 调用工具并获取结果
     * 5. 将结果包装成MCP响应格式
     * 
     * @param params 工具调用参数，包含name和arguments字段
     * @return 工具调用结果Map，包含content字段
     */
    private Map<String, Object> handleToolCall(Map<String, Object> params) {
        try {
            // 提取工具调用的核心参数
            String name = (String) params.get("name");                              // 工具名称
            Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");  // 工具参数
            
            // 记录工具调用信息，便于调试和监控
            logger.info("调用工具: name={}, arguments={}", name, arguments);
            
            // 使用switch表达式根据工具名称路由到具体实现
            String result = switch (name) {
                case "get_health_check" -> {
                    // 体检数据查询工具
                    String personName = (String) arguments.get("personName");
                    // 参数验证：确保用户姓名不为空
                    if (personName == null || personName.trim().isEmpty()) {
                        yield "参数错误：用户姓名不能为空";
                    }
                    // 调用业务逻辑获取体检数据
                    yield mcpTools.getHealthCheck(personName);
                }
                case "analyze_health_check_with_rag" -> {
                    // RAG体检分析工具
                    String personName = (String) arguments.get("personName");
                    // 参数验证
                    if (personName == null || personName.trim().isEmpty()) {
                        yield "参数错误：用户姓名不能为空";
                    }
                    // 调用RAG分析逻辑
                    yield mcpTools.analyzeHealthCheckWithRAG(personName);
                }
                case "ask_medical_question" -> {
                    // 医学知识问答工具
                    String question = (String) arguments.get("question");
                    // 参数验证
                    if (question == null || question.trim().isEmpty()) {
                        yield "参数错误：问题不能为空";
                    }
                    // 调用医学问答逻辑
                    yield mcpTools.askMedicalQuestion(question);
                }
                default -> "未知工具: " + name;  // 处理未知工具名称
            };
            
            // 将工具执行结果包装成MCP标准响应格式
            // content字段包含一个数组，每个元素代表一个内容块
            return Map.of(
                "content", List.of(
                    Map.of(
                        "type", "text",    // 内容类型：文本
                        "text", result     // 实际的文本内容
                    )
                )
            );
            
        } catch (Exception e) {
            // 捕获工具调用过程中的异常
            logger.error("工具调用异常", e);
            
            // 返回错误信息，保持响应格式的一致性
            return Map.of(
                "content", List.of(
                    Map.of(
                        "type", "text",
                        "text", "工具调用失败: " + e.getMessage()
                    )
                )
            );
        }
    }
}