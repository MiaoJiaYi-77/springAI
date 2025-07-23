package com.example.demo.controller;

import com.example.demo.service.KnowledgeBaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 知识库管理控制器
 */
@RestController
@RequestMapping("/knowledge")
@CrossOrigin(origins = "*")
public class KnowledgeBaseController {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    /**
     * 获取知识库统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        Map<String, Object> stats = knowledgeBaseService.getKnowledgeBaseStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * 重新加载知识库
     */
    @PostMapping("/reload")
    public ResponseEntity<?> reloadKnowledgeBase() {
        String result = knowledgeBaseService.reloadKnowledgeBase();
        return ResponseEntity.ok(Map.of(
            "message", result,
            "timestamp", System.currentTimeMillis()
        ));
    }
}