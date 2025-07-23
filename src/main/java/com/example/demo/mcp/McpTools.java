package com.example.demo.mcp;

import com.example.demo.service.HealthCheckService;
import com.example.demo.service.KnowledgeBaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.example.demo.model.HealthCheck;

@Component
public class McpTools {
    
    private static final Logger logger = LoggerFactory.getLogger(McpTools.class);
    
    @Autowired
    private HealthCheckService healthCheckService;
    
    @Autowired
    private KnowledgeBaseService knowledgeBaseService;
    
    @Autowired
    private ChatModel chatModel;
    
    /**
     * 获取体检信息 - MCP工具实现
     */
    public String getHealthCheck(String personName) {
        try {
            HealthCheck data = healthCheckService.getHealthCheckByPersonName(personName);
            if (data != null) {
                StringBuilder formatted = new StringBuilder();
                formatted.append(personName).append(" 的体检报告\n\n");
                formatted.append("基本信息：\n");
                formatted.append("• 姓名：").append(data.getPersonName()).append("\n");
                formatted.append("• 性别：").append(data.getGender()).append("\n");
                formatted.append("• 年龄：").append(data.getAge()).append("岁\n");
                formatted.append("• 检查日期：").append(data.getCheckDate()).append("\n\n");
                
                formatted.append("血液指标：\n");
                formatted.append("• 白细胞计数：").append(data.getWbcCount()).append(" × 10⁹/L\n");
                formatted.append("• 中性粒细胞百分比：").append(data.getNeutrophilPct()).append("%\n");
                formatted.append("• 淋巴细胞百分比：").append(data.getLymphocytePct()).append("%\n");
                formatted.append("• 单核细胞百分比：").append(data.getMonocytePct()).append("%\n");
                formatted.append("• 中性粒细胞计数：").append(data.getNeutrophilCount()).append(" × 10⁹/L\n");
                
                return formatted.toString();
            } else {
                return "未找到用户 " + personName + " 的体检数据";
            }
        } catch (Exception e) {
            logger.error("获取体检数据异常", e);
            return "获取体检数据异常: " + e.getMessage();
        }
    }
    
    /**
     * 基于知识库分析体检报告 - MCP工具实现
     */
    public String analyzeHealthCheckWithRAG(String personName) {
        try {
            HealthCheck healthData = healthCheckService.getHealthCheckByPersonName(personName);
            if (healthData == null) {
                return "未找到用户 " + personName + " 的体检数据";
            }
            
            String analysisPrompt = String.format(
                "请基于医学知识库分析以下体检数据：\n\n" +
                "患者：%s（%s，%d岁）\n" +
                "检查日期：%s\n\n" +
                "血液指标：\n" +
                "• 白细胞计数：%.2f × 10⁹/L\n" +
                "• 中性粒细胞百分比：%.1f%%\n" +
                "• 淋巴细胞百分比：%.1f%%\n" +
                "• 单核细胞百分比：%.1f%%\n" +
                "• 中性粒细胞计数：%.2f × 10⁹/L\n\n" +
                "请分析各项指标是否正常，如有异常请说明可能原因和建议。",
                healthData.getPersonName(), healthData.getGender(), healthData.getAge(),
                healthData.getCheckDate(), healthData.getWbcCount(), 
                healthData.getNeutrophilPct(), healthData.getLymphocytePct(),
                healthData.getMonocytePct(), healthData.getNeutrophilCount()
            );
            
            Map<String, Object> searchResult = knowledgeBaseService.searchKnowledge(analysisPrompt);
            
            if (searchResult != null && (Integer) searchResult.getOrDefault("count", 0) > 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> results = (List<Map<String, Object>>) searchResult.get("results");
                
                StringBuilder knowledgeContext = new StringBuilder();
                for (Map<String, Object> result : results) {
                    knowledgeContext.append(result.get("content")).append("\n\n");
                }
                
                String finalPrompt = String.format(
                    "基于以下医学知识库内容：\n%s\n\n请专业分析体检数据：\n%s\n\n" +
                    "请给出：1）各指标是否正常 2）异常指标的可能原因 3）健康建议",
                    knowledgeContext.toString(), analysisPrompt
                );
                
                ChatResponse response = chatModel.call(new Prompt(finalPrompt));
                String analysis = cleanAIResponse(response.getResult().getOutput().toString());
                
                return String.format("%s 的体检报告分析\n\n%s\n\n 本分析基于医学知识库检索到的 %d 个相关文档",
                    personName, analysis, (Integer) searchResult.get("count"));
            } else {
                String basicPrompt = String.format("请分析以下体检数据并给出健康建议：\n%s", analysisPrompt);
                ChatResponse response = chatModel.call(new Prompt(basicPrompt));
                String analysis = cleanAIResponse(response.getResult().getOutput().toString());
                return personName + "的体检报告分析\n\n" + analysis +
                       "\n\n基于AI通用医学知识分析";
            }
            
        } catch (Exception e) {
            logger.error("分析体检数据异常", e);
            return "分析体检数据异常: " + e.getMessage();
        }
    }
    
    /**
     * RAG问答 - 返回答案和召回的文本块
     */
    public Map<String, Object> askWithRAG(String question) {
        Map<String, Object> result = new HashMap<>();
        try {
            logger.info("RAG问答开始，问题: {}", question);
            
            Map<String, Object> searchResult = knowledgeBaseService.searchKnowledge(question);
            logger.info("知识库检索结果: count={}, results={}", 
                searchResult.getOrDefault("count", 0), 
                searchResult.getOrDefault("message", ""));
            
            if (searchResult != null && (Integer) searchResult.getOrDefault("count", 0) > 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> sources = (List<Map<String, Object>>) searchResult.get("results");
                
                StringBuilder context = new StringBuilder();
                for (int i = 0; i < sources.size(); i++) {
                    Map<String, Object> source = sources.get(i);
                    context.append("【文档").append(i + 1).append("】\n");
                    context.append(source.get("content")).append("\n\n");
                }
                
                String ragPrompt = String.format(
                    "你是一位专业的医疗助手。请基于以下医学知识库信息回答用户的问题。\n\n" +
                    "医学知识库内容：\n%s\n\n" +
                    "用户问题：%s\n\n" +
                    "请基于上述知识库内容给出专业回答：", 
                    context.toString(), question
                );
                
                ChatResponse response = chatModel.call(new Prompt(ragPrompt));
                String answer = cleanAIResponse(response.getResult().getOutput().toString());
                
                result.put("answer", answer);
                result.put("sources", sources);
                result.put("retrievedCount", sources.size());
                result.put("mode", "RAG");
            } else {
                ChatResponse response = chatModel.call(new Prompt(question));
                String answer = cleanAIResponse(response.getResult().getOutput().toString());
                result.put("answer", answer);
                result.put("sources", List.of());
                result.put("retrievedCount", 0);
                result.put("mode", "DIRECT");
            }
        } catch (Exception e) {
            logger.error("RAG问答异常", e);
            result.put("answer", "处理问题时出现错误: " + e.getMessage());
            result.put("sources", List.of());
            result.put("retrievedCount", 0);
            result.put("mode", "ERROR");
        }
        return result;
    }
    
    /**
     * 简单问答
     */
    public String askSimple(String question) {
        try {
            ChatResponse response = chatModel.call(new Prompt(question));
            return cleanAIResponse(response.getResult().getOutput().toString());
        } catch (Exception e) {
            logger.error("简单问答异常", e);
            return "AI服务暂时不可用: " + e.getMessage();
        }
    }
    
    /**
     * 医学知识问答 - MCP工具实现
     */
    public String askMedicalQuestion(String question) {
        try {
            logger.info("医学知识问答: {}", question);
            
            Map<String, Object> ragResult = askWithRAG(question);
            String answer = (String) ragResult.get("answer");
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sources = (List<Map<String, Object>>) ragResult.get("sources");
            Integer retrievedCount = (Integer) ragResult.get("retrievedCount");
            String mode = (String) ragResult.get("mode");
            
            if (sources != null && !sources.isEmpty()) {
                StringBuilder result = new StringBuilder();
                result.append(answer).append("\n\n");
                result.append("知识库检索结果 (共").append(retrievedCount).append("个文本块)：\n");
                
                for (int i = 0; i < sources.size(); i++) {
                    Map<String, Object> source = sources.get(i);
                    result.append("\n【文档").append(i + 1).append("】");
                    
                    if (source.get("knowledgeBaseFile") != null) {
                        result.append(" 来源：").append(source.get("knowledgeBaseFile"));
                    }
                    if (source.get("knowledgeBaseType") != null) {
                        result.append(" (").append(source.get("knowledgeBaseType")).append(")");
                    }
                    if (source.get("score") != null) {
                        result.append(" 相关度：").append(String.format("%.2f", source.get("score")));
                    }
                    
                    String content = (String) source.get("content");
                    if (content != null && content.length() > 150) {
                        result.append("\n").append(content.substring(0, 150)).append("...");
                    } else if (content != null) {
                        result.append("\n").append(content);
                    }
                }
                
                result.append("\n\n以上回答基于医学知识库检索 (").append(mode).append("模式)");
                return result.toString();
            } else {
                return answer + "\n\n基于AI通用知识回答 (" + mode + "模式)";
            }
            
        } catch (Exception e) {
            logger.error("医学知识问答异常", e);
            return "医学知识问答异常: " + e.getMessage();
        }
    }
    
    /**
     * 清理AI回答中的技术信息和元数据
     * 
     * AI模型的原始响应通常包含大量技术元数据，如：
     * - AssistantMessage包装信息
     * - messageType、toolCalls等字段
     * - 各种括号和分隔符
     * 
     * 这个方法的目标是提取纯净的文本内容，提供更好的用户体验
     * 
     * 清理策略：
     * 1. 移除AssistantMessage包装
     * 2. 移除技术元数据字段
     * 3. 移除多余的括号和分隔符
     * 4. 规范化空白字符
     * 5. 如果仍包含技术信息，尝试提取纯文本部分
     * 
     * @param response AI模型的原始响应字符串
     * @return 清理后的纯文本内容
     */
    private String cleanAIResponse(String response) {
        // 空值检查，防止NullPointerException
        if (response == null) return "";
        
        // 第一步：移除AssistantMessage包装和各种技术格式
        // 这些是Spring AI框架添加的包装信息
        response = response.replaceAll("AssistantMessage\\s*\\[.*?\\]", "");
        response = response.replaceAll("AssistantMessage\\s*\\{.*?\\}", "");
        
        // 第二步：移除所有已知的技术元数据字段
        // 这些字段对最终用户没有意义，会影响阅读体验
        response = response.replaceAll("messageType\\s*=\\s*ASSISTANT[,\\s]*", "");
        response = response.replaceAll("toolCalls\\s*=\\s*\\[\\][,\\s]*", "");
        response = response.replaceAll("textContent\\s*=\\s*", "");
        response = response.replaceAll("metadata\\s*=\\s*\\{[^}]*\\}[,\\s]*", "");
        response = response.replaceAll("finishReason\\s*=\\s*STOP[,\\s]*", "");
        response = response.replaceAll("role\\s*=\\s*ASSISTANT[,\\s]*", "");
        response = response.replaceAll("id\\s*=\\s*[a-zA-Z0-9]+[,\\s]*", "");
        
        // 第三步：移除各种括号和分隔符
        // 清理开头和结尾的技术符号
        response = response.replaceAll("^[,\\s\\[\\]\\{\\}]+", "");
        response = response.replaceAll("[,\\s\\[\\]\\{\\}]+$", "");
        
        // 第四步：规范化空白字符
        // 将多个连续的空行合并为最多两个空行
        response = response.replaceAll("\\n\\s*\\n\\s*\\n+", "\n\n");
        // 移除开头和结尾的空白字符
        response = response.replaceAll("^\\s+", "");
        response = response.replaceAll("\\s+$", "");
        
        // 第五步：如果响应仍然包含技术信息，尝试提取纯文本内容
        // 这是一个备用策略，用于处理未预期的格式
        if (response.contains("=") || response.contains("[") || response.contains("{")) {
            // 按行分析，只保留不包含技术符号的行
            String[] lines = response.split("\n");
            StringBuilder cleanText = new StringBuilder();
            
            for (String line : lines) {
                line = line.trim();
                // 跳过包含技术符号的行和空行
                if (!line.contains("=") && !line.contains("[") && !line.contains("{") && line.length() > 0) {
                    cleanText.append(line).append("\n");
                }
            }
            
            // 如果成功提取到纯文本，使用提取的内容
            if (cleanText.length() > 0) {
                response = cleanText.toString().trim();
            }
        }
        
        return response;
    }
}
