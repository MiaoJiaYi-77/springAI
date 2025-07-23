package com.example.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

@Configuration
public class VectorStoreConfig {

    private static final Logger logger = LoggerFactory.getLogger(VectorStoreConfig.class);

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        logger.info("配置简单内存向量存储");
        return new SimpleInMemoryVectorStore(embeddingModel);
    }
    
    // 简化的内存向量存储
    public static class SimpleInMemoryVectorStore implements VectorStore {
        
        private final EmbeddingModel embeddingModel;
        private final List<DocumentWithEmbedding> storage = new ArrayList<>();
        
        public SimpleInMemoryVectorStore(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
        }
        
        @Override
        public void add(List<Document> documents) {
            for (Document doc : documents) {
                try {
                    String content = doc.getText();
                    if (content != null && !content.trim().isEmpty()) {
                        float[] embedding = embeddingModel.embed(content);
                        storage.add(new DocumentWithEmbedding(doc, embedding));
                        logger.debug("添加文档到向量存储: {}", content.substring(0, Math.min(50, content.length())));
                    }
                } catch (Exception e) {
                    logger.warn("添加文档失败: {}", e.getMessage());
                }
            }
            logger.info("向量存储中共有 {} 个文档", storage.size());
        }

        @Override
        public void delete(List<String> idList) {
            try {
                storage.removeIf(item -> {
                    String docId = item.document.getMetadata().get("id") != null ?
                            item.document.getMetadata().get("id").toString() :
                            item.document.toString();
                    return idList.contains(docId);
                });
            } catch (Exception e) {
                logger.error("删除文档失败", e);
                // 你可以考虑抛出 RuntimeException 或记录错误，根据需求处理异常
                throw new RuntimeException("删除文档失败", e);
            }
        }


        @Override
        public void delete(Filter.Expression filterExpression) {
            logger.warn("暂不支持基于过滤器的删除操作");
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            try {
                String query = request.getQuery();
                int topK = request.getTopK();
                
                if (storage.isEmpty()) {
                    logger.warn("向量存储为空，无法进行相似度搜索");
                    return new ArrayList<>();
                }
                
                float[] queryEmbedding = embeddingModel.embed(query);
                
                return storage.stream()
                        .map(item -> new ScoredDocument(item.document, 
                            cosineSimilarity(queryEmbedding, item.embedding)))
                        .sorted((a, b) -> Double.compare(b.score, a.score))
                        .limit(topK > 0 ? topK : 5)
                        .map(scored -> scored.document)
                        .collect(Collectors.toList());
                        
            } catch (Exception e) {
                logger.error("相似度搜索失败", e);
                return new ArrayList<>();
            }
        }

        @Override
        public List<Document> similaritySearch(String query) {
            try {
                if (storage.isEmpty()) {
                    logger.warn("向量存储为空，无法进行相似度搜索");
                    return new ArrayList<>();
                }
                
                float[] queryEmbedding = embeddingModel.embed(query);
                
                return storage.stream()
                        .map(item -> new ScoredDocument(item.document, 
                            cosineSimilarity(queryEmbedding, item.embedding)))
                        .sorted((a, b) -> Double.compare(b.score, a.score))
                        .limit(5)
                        .map(scored -> scored.document)
                        .collect(Collectors.toList());
                        
            } catch (Exception e) {
                logger.error("相似度搜索失败", e);
                return new ArrayList<>();
            }
        }
        
        private double cosineSimilarity(float[] a, float[] b) {
            if (a.length != b.length) return 0.0;
            
            double dotProduct = 0.0;
            double normA = 0.0;
            double normB = 0.0;
            
            for (int i = 0; i < a.length; i++) {
                dotProduct += a[i] * b[i];
                normA += a[i] * a[i];
                normB += b[i] * b[i];
            }
            
            if (normA == 0.0 || normB == 0.0) return 0.0;
            
            return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
        }
        
        private static class DocumentWithEmbedding {
            final Document document;
            final float[] embedding;
            
            DocumentWithEmbedding(Document document, float[] embedding) {
                this.document = document;
                this.embedding = embedding;
            }
        }
        
        private static class ScoredDocument {
            final Document document;
            final double score;
            
            ScoredDocument(Document document, double score) {
                this.document = document;
                this.score = score;
            }
        }
    }
}