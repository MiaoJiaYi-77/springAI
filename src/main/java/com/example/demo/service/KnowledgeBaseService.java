package com.example.demo.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 医学知识库服务
 * 
 * 这是RAG（Retrieval-Augmented Generation）系统的核心组件，负责：
 * 1. 加载和管理医学知识库文件
 * 2. 将知识内容向量化并存储
 * 3. 提供语义搜索功能
 * 4. 支持知识库的动态更新
 * 
 * RAG工作原理：
 * 1. 预处理：将医学文档分割成小块，生成向量嵌入
 * 2. 存储：将文档块和向量存储在向量数据库中
 * 3. 检索：根据用户问题搜索相关文档块
 * 4. 生成：将检索到的内容作为上下文，生成回答
 * 
 * 知识库文件结构：
 * - medical_knowledge.txt: 基础体检指标知识
 * - medical_knowledge_liver.txt: 肝功能专业知识  
 * - medical_knowledge_cardiovascular.txt: 心血管疾病知识
 * - medical_knowledge_diabetes.txt: 糖尿病专业知识
 * 
 * @author MJY
 * @version 1.0
 */
@Service  // Spring服务层组件
public class KnowledgeBaseService {

    // 日志记录器，用于记录知识库操作过程
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private static final String[] KNOWLEDGE_BASE_FILES = {
        "medical_knowledge.txt",              // 基础医学知识
        "medical_knowledge_liver.txt",        // 肝脏疾病知识
        "medical_knowledge_cardiovascular.txt", // 心血管疾病知识
        "medical_knowledge_diabetes.txt"      // 糖尿病知识
    };

    // Spring AI向量存储，用于存储和检索文档向量
    @Autowired
    private VectorStore vectorStore;

    // 嵌入模型，用于将文本转换为向量表示
    @Autowired
    private EmbeddingModel embeddingModel;

    /**
     * 服务初始化方法
     * 
     * 在Spring容器启动后自动执行，负责：
     * 1. 加载所有医学知识库文件
     * 2. 处理和分割文档内容
     * 3. 生成向量嵌入
     * 4. 存储到向量数据库
     * 
     * 使用@PostConstruct确保在依赖注入完成后执行
     */
    @PostConstruct
    public void initializeKnowledgeBase() {
        try {
            logger.info("开始初始化医学知识库...");
            
            // 记录初始化开始时间，用于性能监控
            long startTime = System.currentTimeMillis();
            
            // 遍历所有知识库文件
            for (String fileName : KNOWLEDGE_BASE_FILES) {
                try {
                    // 加载单个知识库文件
                    loadKnowledgeFile(fileName);
                    logger.info("成功加载知识库文件: {}", fileName);
                } catch (Exception e) {
                    // 单个文件加载失败不应该影响其他文件
                    logger.error("加载知识库文件失败: {}", fileName, e);
                }
            }
            
            // 计算并记录初始化耗时
            long endTime = System.currentTimeMillis();
            logger.info("医学知识库初始化完成，耗时: {}ms", endTime - startTime);
            
        } catch (Exception e) {
            // 记录初始化失败的错误
            logger.error("医学知识库初始化失败", e);
        }
    }

    /**
     * 加载单个知识库文件
     * 
     * 处理流程：
     * 1. 从classpath读取文件内容
     * 2. 使用高级策略分割文档
     * 3. 为每个文档片段添加元数据
     * 4. 存储到向量数据库
     * 
     * @param fileName 知识库文件名
     * @throws IOException 文件读取异常
     */
    private void loadKnowledgeFile(String fileName) throws IOException {
        // 使用ClassPathResource读取classpath下的文件
        ClassPathResource resource = new ClassPathResource(fileName);
        
        // 检查文件是否存在
        if (!resource.exists()) {
            logger.warn("知识库文件不存在: {}", fileName);
            return;
        }

        // 读取文件内容，使用UTF-8编码确保中文正确处理
        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        
        // 验证文件内容不为空
        if (content.trim().isEmpty()) {
            logger.warn("知识库文件内容为空: {}", fileName);
            return;
        }

        // 使用高级分割策略处理文档内容
        List<Document> documents = splitContentAdvanced(content, fileName);
        
        // 记录分割结果
        logger.info("文件 {} 分割为 {} 个文档片段", fileName, documents.size());

        // 将文档片段添加到向量存储
        if (!documents.isEmpty()) {
            vectorStore.add(documents);
            logger.debug("已将 {} 个文档片段添加到向量存储", documents.size());
        }
    }

    /**
     * 高级内容分割策略
     * 
     * 相比简单的段落分割，这个方法提供更智能的分割：
     * 1. 优先按三级标题（###）分割
     * 2. 如果没有三级标题，按二级标题（##）分割
     * 3. 对过长的段落进一步细分
     * 4. 保持语义完整性
     * 
     * 分割原则：
     * - 每个片段长度适中（50-1000字符）
     * - 保持主题完整性
     * - 添加丰富的元数据信息
     * 
     * @param content 完整的文档内容
     * @param fileName 文件名，用于元数据
     * @return 分割后的文档片段列表
     */
    private List<Document> splitContentAdvanced(String content, String fileName) {
        List<Document> documents = new ArrayList<>();
        
        // 确定知识库类型，用于分类和检索优化
        String knowledgeType = getKnowledgeType(fileName);

        // 第一步：尝试按三级标题分割（### 标题）
        // 三级标题通常表示具体的知识点或概念
        String[] sections = content.split("(?=^###\\s)", Pattern.MULTILINE);

        for (int i = 0; i < sections.length; i++) {
            String section = sections[i].trim();
            
            // 过滤太短的片段，避免无意义的内容
            if (section.isEmpty() || section.length() < 50) continue;

            // 对于过长的段落，进一步细分
            if (section.length() > 1000) {
                List<Document> subDocs = splitLongSection(section, fileName, knowledgeType, i);
                documents.addAll(subDocs);
            } else {
                // 创建单个文档片段
                Map<String, Object> metadata = createMetadata(fileName, knowledgeType, i, section);
                documents.add(new Document(section, metadata));
            }
        }

        // 第二步：如果没有三级标题，尝试按二级标题分割（## 标题）
        if (documents.isEmpty()) {
            sections = content.split("(?=^##\\s)", Pattern.MULTILINE);
            
            for (int i = 0; i < sections.length; i++) {
                String section = sections[i].trim();
                if (section.isEmpty() || section.length() < 50) continue;

                if (section.length() > 1000) {
                    List<Document> subDocs = splitLongSection(section, fileName, knowledgeType, i);
                    documents.addAll(subDocs);
                } else {
                    Map<String, Object> metadata = createMetadata(fileName, knowledgeType, i, section);
                    documents.add(new Document(section, metadata));
                }
            }
        }

        return documents;
    }

    /**
     * 分割过长的文档段落
     * 
     * 对于超过1000字符的长段落，需要进一步细分以：
     * 1. 提高检索精度
     * 2. 减少无关信息干扰
     * 3. 适应AI模型的上下文窗口限制
     * 
     * 分割策略：
     * - 按段落（双换行）分割
     * - 控制每个子文档在800字符以内
     * - 保持段落完整性
     * - 继承父段落的元数据
     * 
     * @param section 需要分割的长段落
     * @param fileName 文件名
     * @param knowledgeType 知识库类型
     * @param sectionIndex 段落索引
     * @return 分割后的子文档列表
     */
    private List<Document> splitLongSection(String section, String fileName, String knowledgeType, int sectionIndex) {
        List<Document> subDocs = new ArrayList<>();
        
        // 提取段落标题（如果存在）
        String sectionTitle = extractTitle(section);
        
        // 按段落分割
        String[] paragraphs = section.split("\n\n");
        StringBuilder currentChunk = new StringBuilder();

        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.isEmpty()) continue;

            // 如果当前块加上新段落超过800字符，则创建新文档
            if (currentChunk.length() > 0 && currentChunk.length() + paragraph.length() > 800) {
                // 确保当前块有足够的内容
                if (currentChunk.length() > 50) {
                    Map<String, Object> metadata = createMetadata(fileName, knowledgeType, sectionIndex, currentChunk.toString());
                    
                    // 如果有段落标题，添加到元数据中
                    if (sectionTitle != null) {
                        metadata.put("parentTitle", sectionTitle);
                    }
                    
                    subDocs.add(new Document(currentChunk.toString(), metadata));
                }
                // 重置当前块
                currentChunk = new StringBuilder();
            }

            // 添加段落到当前块
            if (currentChunk.length() > 0) {
                currentChunk.append("\n\n");
            }
            currentChunk.append(paragraph);
        }

        // 处理最后一个块
        if (currentChunk.length() > 50) {
            Map<String, Object> metadata = createMetadata(fileName, knowledgeType, sectionIndex, currentChunk.toString());
            if (sectionTitle != null) {
                metadata.put("parentTitle", sectionTitle);
            }
            subDocs.add(new Document(currentChunk.toString(), metadata));
        }

        return subDocs;
    }

    /**
     * 创建文档元数据
     * 
     * 元数据对于RAG系统非常重要，它们用于：
     * 1. 文档分类和过滤
     * 2. 检索结果排序
     * 3. 上下文信息提供
     * 4. 调试和监控
     * 
     * @param fileName 源文件名
     * @param knowledgeType 知识库类型
     * @param chunkIndex 片段索引
     * @param content 文档内容
     * @return 元数据Map
     */
    private Map<String, Object> createMetadata(String fileName, String knowledgeType, int chunkIndex, String content) {
        Map<String, Object> metadata = new HashMap<>();
        
        // 基础元数据
        metadata.put("source", fileName);                           // 来源文件
        metadata.put("type", knowledgeType);                       // 知识库类型
        metadata.put("chunk", chunkIndex);                         // 片段序号
        metadata.put("id", knowledgeType + "_doc_" + chunkIndex);  // 唯一标识
        
        // 内容特征元数据
        metadata.put("length", content.length());                 // 内容长度
        metadata.put("timestamp", System.currentTimeMillis());    // 创建时间
        
        // 提取标题信息
        String title = extractTitle(content);
        if (title != null) {
            metadata.put("title", title);
        }
        
        // 内容类型判断
        if (content.contains("Q:") || content.contains("问:")) {
            metadata.put("contentType", "FAQ");  // 问答类型
        } else if (content.contains("正常参考值") || content.contains("参考范围")) {
            metadata.put("contentType", "REFERENCE");  // 参考值类型
        } else if (content.contains("建议") || content.contains("注意")) {
            metadata.put("contentType", "ADVICE");  // 建议类型
        } else {
            metadata.put("contentType", "GENERAL");  // 一般类型
        }
        
        return metadata;
    }

    /**
     * 根据文件名确定知识库类型
     * 
     * 知识库类型用于：
     * 1. 检索时的领域过滤
     * 2. 结果排序和优先级
     * 3. 专业术语处理
     * 
     * @param fileName 文件名
     * @return 知识库类型标识
     */
    private String getKnowledgeType(String fileName) {
        if (fileName.contains("liver")) {
            return "LIVER";           // 肝脏疾病
        } else if (fileName.contains("cardiovascular")) {
            return "CARDIOVASCULAR";  // 心血管疾病
        } else if (fileName.contains("diabetes")) {
            return "DIABETES";        // 糖尿病
        } else {
            return "GENERAL";         // 一般医学知识
        }
    }

    /**
     * 从文档内容中提取标题
     * 
     * 标题提取规则：
     * 1. 优先提取Markdown格式标题（#、##、###）
     * 2. 提取第一行作为标题（如果符合标题特征）
     * 3. 清理标题格式，去除标记符号
     * 
     * @param content 文档内容
     * @return 提取的标题，如果没有则返回null
     */
    private String extractTitle(String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            
            // 检查Markdown标题格式
            if (line.startsWith("#")) {
                // 移除#符号和多余空格，返回纯标题文本
                return line.replaceAll("^#+\\s*", "").trim();
            }
            
            // 检查是否为标题行（短且不包含句号）
            if (line.length() > 0 && line.length() < 100 && !line.contains("。") && !line.contains(".")) {
                return line;
            }
        }
        
        return null;
    }

    /**
     * 搜索医学知识库
     * 
     * 这是RAG系统的核心检索方法，实现：
     * 1. 医学相关性预过滤
     * 2. 向量相似度搜索
     * 3. 相关性评分和排序
     * 4. 结果数量控制
     * 
     * 检索策略：
     * - 首先判断是否为医学相关问题
     * - 使用向量相似度搜索找到候选文档
     * - 通过自定义评分算法过滤和排序
     * - 限制返回结果数量，避免信息过载
     * 
     * @param query 用户查询问题
     * @return 搜索结果Map，包含results、count、message等字段
     */
    public Map<String, Object> searchKnowledge(String query) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            logger.info("搜索知识库: {}", query);

            // 第一步：预过滤，检查是否为医学相关问题
            // 这可以避免对非医学问题进行不必要的检索
            if (!isMedicalRelatedQuery(query)) {
                logger.info("非医学相关问题，跳过知识库检索: {}", query);
                result.put("results", new ArrayList<>());
                result.put("count", 0);
                result.put("message", "该问题不属于医学知识范畴");
                return result;
            }

            // 第二步：执行向量相似度搜索
            // 使用嵌入模型将查询转换为向量，然后在向量空间中搜索相似文档
            List<Document> docs = vectorStore.similaritySearch(query);

            // 第三步：相关性过滤和排序
            // 使用自定义评分算法进一步筛选结果
            List<Document> relevantDocs = docs.stream()
                .filter(doc -> {
                    double score = calculateRelevanceScore(doc, query);
                    logger.debug("文档相关性得分: {} - {}", score, 
                        doc.getText().substring(0, Math.min(50, doc.getText().length())));
                    return score > 30.0; // 相关性阈值：30分
                })
                .sorted((a, b) -> Double.compare(
                    calculateRelevanceScore(b, query),  // 降序排列
                    calculateRelevanceScore(a, query)
                ))
                .limit(5)  // 限制返回最多5个结果
                .collect(Collectors.toList());

            // 第四步：构建返回结果
            List<Map<String, Object>> resultList = new ArrayList<>();
            for (Document doc : relevantDocs) {
                Map<String, Object> docResult = new HashMap<>();
                docResult.put("content", doc.getText());
                docResult.put("metadata", doc.getMetadata());
                docResult.put("score", calculateRelevanceScore(doc, query));
                resultList.add(docResult);
            }

            result.put("results", resultList);
            result.put("count", resultList.size());
            result.put("query", query);
            
            logger.info("知识库搜索完成，返回 {} 个相关文档", resultList.size());

        } catch (Exception e) {
            logger.error("知识库搜索异常", e);
            result.put("results", new ArrayList<>());
            result.put("count", 0);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 判断查询是否与医学相关
     * 
     * 通过关键词匹配判断用户问题是否属于医学领域：
     * 1. 检查医学术语和指标名称
     * 2. 检查疾病和症状关键词
     * 3. 检查体检相关词汇
     * 
     * 这个预过滤步骤可以：
     * - 提高检索效率
     * - 避免无关结果
     * - 改善用户体验
     * 
     * @param query 用户查询
     * @return 是否为医学相关问题
     */
    private boolean isMedicalRelatedQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }
        
        String queryLower = query.toLowerCase();
        
        // 定义医学相关关键词列表
        String[] medicalKeywords = {
            // 体检指标
            "白细胞", "血压", "血糖", "胆固醇", "肝功能", "肾功能", "心电图",
            "中性粒细胞", "淋巴细胞", "血红蛋白", "血小板", "尿酸", "甘油三酯",
            
            // 疾病名称
            "高血压", "糖尿病", "肝炎", "心脏病", "冠心病", "脂肪肝", "肾病",
            "贫血", "感染", "炎症", "肿瘤", "癌症",
            
            // 症状和体征
            "发热", "疼痛", "咳嗽", "胸闷", "头晕", "乏力", "水肿", "黄疸",
            
            // 医学术语
            "诊断", "治疗", "药物", "检查", "化验", "体检", "健康", "医学",
            "临床", "病理", "生理", "解剖", "免疫", "代谢"
        };
        
        // 检查是否包含医学关键词
        for (String keyword : medicalKeywords) {
            if (queryLower.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 计算文档与查询的相关性得分
     * 
     * 自定义评分算法，综合考虑：
     * 1. 关键词匹配程度
     * 2. 文档类型权重
     * 3. 内容结构特征
     * 4. 语义相关性
     * 
     * 评分规则：
     * - 精确关键词匹配：100分
     * - FAQ格式文档：80分
     * - 参考值类型：60分
     * - 标题匹配：50分
     * - 内容长度适中：20分
     * 
     * @param doc 文档对象
     * @param query 查询字符串
     * @return 相关性得分（0-500分）
     */
    private double calculateRelevanceScore(Document doc, String query) {
        String content = doc.getText().toLowerCase();
        String queryLower = query.toLowerCase();
        double score = 0.0;

        // 1. 精确关键词匹配（最高权重）
        String[] queryKeywords = {
            "血压", "测量", "时间", "黄金时间", "白细胞", "升高", 
            "心电图", "异常", "心脏病", "胆固醇", "肝功能", "血糖"
        };
        
        for (String keyword : queryKeywords) {
            if (queryLower.contains(keyword.toLowerCase()) && 
                content.contains(keyword.toLowerCase())) {
                score += 100.0; // 关键词匹配得高分
            }
        }

        // 2. 文档格式类型加分
        if (content.contains("q:") || content.contains("问:") || 
            content.contains("q1:") || content.contains("q2:")) {
            score += 80.0; // FAQ格式文档
        }

        if (content.contains("a:") || content.contains("答:")) {
            score += 80.0; // 包含答案的文档
        }

        // 3. 参考值和标准信息加分
        if (content.contains("正常参考值") || content.contains("正常范围") || 
            content.contains("参考范围")) {
            score += 60.0;
        }

        // 4. 标题相关性
        Map<String, Object> metadata = doc.getMetadata();
        String title = (String) metadata.get("title");
        if (title != null && queryLower.contains(title.toLowerCase())) {
            score += 50.0;
        }

        // 5. 内容长度适中性
        int contentLength = content.length();
        if (contentLength >= 100 && contentLength <= 800) {
            score += 20.0; // 长度适中的文档更有价值
        }

        // 6. 专业术语密度
        String[] medicalTerms = {"升高", "降低", "异常", "正常", "建议", "注意", "可能"};
        int termCount = 0;
        for (String term : medicalTerms) {
            if (content.contains(term)) {
                termCount++;
            }
        }
        score += termCount * 10.0; // 每个专业术语加10分

        return score;
    }

    /**
     * 重新加载知识库
     * 
     * 提供动态更新知识库的能力，用于：
     * 1. 开发阶段的快速迭代
     * 2. 生产环境的知识库更新
     * 3. 故障恢复和重置
     * 
     * 注意：这个操作会清空现有的向量存储，请谨慎使用
     * 
     * @return 操作结果信息
     */
    public String reloadKnowledgeBase() {
        try {
            logger.info("开始重新加载知识库...");
            
            // 注意：这里假设向量存储支持清空操作
            // 实际实现可能需要根据具体的向量存储类型调整
            
            // 重新初始化知识库
            initializeKnowledgeBase();
            
            return "知识库重新加载成功";
            
        } catch (Exception e) {
            logger.error("重新加载知识库失败", e);
            return "知识库重新加载失败: " + e.getMessage();
        }
    }

    /**
     * 获取知识库统计信息
     */
    public Map<String, Object> getKnowledgeBaseStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalFiles", KNOWLEDGE_BASE_FILES.length);
        stats.put("files", List.of(KNOWLEDGE_BASE_FILES));
        stats.put("types", List.of("心血管疾病", "糖尿病", "肝功能"));
        stats.put("vectorStoreEnabled", vectorStore != null);
        return stats;
    }
}
