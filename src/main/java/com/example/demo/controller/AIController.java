package com.example.demo.controller;

import com.example.demo.mcp.McpTools;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/ai")
@CrossOrigin(origins = "*")
public class AIController {

    @Autowired
    private McpTools mcpTools;

    /**
     * 获取体检信息
     */
    @PostMapping("/health-check")
    public ResponseEntity<?> getHealthCheck(@RequestBody Map<String, String> request) {
        try {
            String personName = request.get("personName");
            if (personName == null || personName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "用户姓名不能为空"));
            }

            String result = mcpTools.getHealthCheck(personName);
            return ResponseEntity.ok(Map.of(
                "answer", result,
                "timestamp", System.currentTimeMillis()
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 分析体检报告 - 基于知识库
     */
    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeHealthCheck(@RequestBody Map<String, String> request) {
        try {
            String personName = request.get("personName");
            if (personName == null || personName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "用户姓名不能为空"));
            }

            String analysis = mcpTools.analyzeHealthCheckWithRAG(personName);
            return ResponseEntity.ok(Map.of(
                "answer", analysis,
                "timestamp", System.currentTimeMillis()
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * RAG问答 - 返回答案和召回的文本块
     */
    @PostMapping("/ask-rag")
    public ResponseEntity<?> askWithRAG(@RequestBody Map<String, String> request) {
        try {
            String question = request.get("question");
            if (question == null || question.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "问题不能为空"));
            }

            Map<String, Object> result = mcpTools.askWithRAG(question);
            result.put("question", question);
            result.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 简单问答
     */
    @PostMapping("/ask")
    public ResponseEntity<?> askSimple(@RequestBody Map<String, String> request) {
        try {
            String question = request.get("question");
            if (question == null || question.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "问题不能为空"));
            }

            String answer = mcpTools.askSimple(question);
            return ResponseEntity.ok(Map.of(
                "question", question,
                "answer", answer,
                "timestamp", System.currentTimeMillis()
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 服务状态
     */
    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        return ResponseEntity.ok(Map.of(
            "status", "active",
            "service", "简化AI服务",
            "endpoints", Map.of(
                "/ai/health-check", "获取体检信息(MCP)",
                "/ai/analyze", "分析体检报告(RAG)",
                "/ai/ask-rag", "RAG问答",
                "/ai/ask", "简单问答"
            ),
            "timestamp", System.currentTimeMillis()
        ));
    }
}
