# 健康检查MCP服务 (Health Check MCP Service)

基于Spring Boot和Model Context Protocol (MCP) 的智能医疗健康检查服务，集成AI分析和医学知识库检索功能。

## 功能特性

- **体检数据查询**: 通过MCP工具查询用户体检指标
- **智能分析**: 基于医学知识库的RAG分析体检报告
- **医学问答**: 支持医学知识库检索和通用AI问答
- **Web界面**: 提供友好的聊天式交互界面

## 技术架构

- **后端**: Spring Boot 3.2.7 + Spring AI 1.0.0
- **数据库**: PostgreSQL
- **AI模型**: 智谱AI ChatGLM Turbo
- **向量存储**: Spring AI Vector Store
- **协议**: MCP (Model Context Protocol) 2024-11-05

## 系统要求

- Java 17+
- Maven 3.6+
- PostgreSQL 12+
- 智谱AI API密钥

## 快速开始

### 1. 环境配置

```bash
# 克隆项目
git clone <repository-url>
cd health-check-mcp

# 配置数据库
createdb mcpdb
createuser mcpuser
```

### 2. 配置文件

编辑 `src/main/resources/application.yaml`:

```yaml
spring:
  ai:
    zhipuai:
      api-key: YOUR_ZHIPUAI_API_KEY
  datasource:
    url: jdbc:postgresql://localhost:5432/mcpdb
    username: mcpuser
    password: mcppass
```

### 3. 启动服务

```bash
# 编译运行
mvn spring-boot:run

# 或打包运行
mvn clean package
java -jar target/health-check-mcp-0.0.1-SNAPSHOT.jar
```

### 4. 访问服务

- Web界面: http://localhost:8081/ai-assistant.html


## API接口

### MCP协议接口

```bash
# 获取工具列表
GET /mcp/tools

# MCP消息处理
POST /mcp/message
```

### AI服务接口

```bash
# 体检数据查询
POST /ai/health-check
Content-Type: application/json
{"personName": "张三"}

# 体检报告分析
POST /ai/analyze
Content-Type: application/json
{"personName": "张三"}

# 医学知识问答
POST /ai/ask-rag
Content-Type: application/json
{"question": "白细胞升高的原因"}
```

## 医学知识库

系统内置四个医学知识库文件：

- `medical_knowledge.txt`: 基础体检指标知识
- `medical_knowledge_liver.txt`: 肝功能专业知识
- `medical_knowledge_cardiovascular.txt`: 心血管疾病知识
- `medical_knowledge_liver.txt`: 糖尿病专业知识

## 开发指南

### 项目结构

```
src/main/java/com/example/demo/
├── controller/          # REST控制器
├── service/            # 业务服务层
├── mcp/               # MCP协议实现
├── entity/            # 数据实体
└── HealthCheckMcpApplication.java

src/main/resources/
├── static/            # 静态Web资源
├── medical_knowledge*.txt  # 医学知识库
└── application.yaml   # 配置文件
```

### 添加新的医学知识

1. 在 `src/main/resources/` 下创建新的知识库文件
2. 更新 `KnowledgeBaseService.KNOWLEDGE_BASE_FILES` 数组
3. 重启服务或调用 `/knowledge/reload` 接口

### 扩展MCP工具

1. 在 `McpTools` 类中添加新方法
2. 在 `McpProtocol.handleToolsList()` 中注册工具
3. 更新工具调用路由逻辑

## 测试示例

```bash
# 查询张三的体检数据
curl -X POST http://localhost:8081/ai/health-check \
  -H "Content-Type: application/json" \
  -d '{"personName": "张三"}'

# 分析体检报告
curl -X POST http://localhost:8081/ai/analyze \
  -H "Content-Type: application/json" \
  -d '{"personName": "张三"}'

# 医学知识问答
curl -X POST http://localhost:8081/ai/ask-rag \
  -H "Content-Type: application/json" \
  -d '{"question": "白细胞计数正常范围是多少？"}'
```

## 更新日志

### v1.0.0
- 实现基础MCP协议支持
- 集成智谱AI模型
- 添加医学知识库RAG检索
- 提供Web交互界面

