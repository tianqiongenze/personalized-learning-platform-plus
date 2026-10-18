package com.platform.ai.generation;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 6 类教学资源生成 + 向量检索(RAG)
 * 资源类型对应申报书: 练习题组/讲解文档/实训任务书/微课脚本/微课视频脚本/知识点讲解
 * 简单单轮生成走本服务(Spring AI); 复杂多智能体工作流走 agent-service(LangGraph)。
 */
@Service
public class GenerationService {

    private static final Map<String, String> TEMPLATES = Map.ofEntries(
        Map.entry("EXERCISE_SET", """
            生成《%s》差异化练习题组, 共 8 题(基础2/进阶4/挑战2), 每题含题干/答案/考查点/解题思路。
            题目情境须来自以下产业场景: %s。参考资料: %s"""),
        Map.entry("EXPLANATION_DOC", "生成《%s》个性化讲解文档(Markdown): 导入情境、概念解析、例题、常见误区、小结。产业情境: %s。参考资料: %s"),
        Map.entry("TRAINING_TASK", "生成《%s》实训任务书: 真实产线任务背景、目标、任务分解、安全须知、评分标准。产业场景: %s。参考资料: %s"),
        Map.entry("MICRO_LESSON_SCRIPT", "生成《%s》8 分钟微课逐字脚本(时间轴/口播/画面提示)。产业情境: %s。参考资料: %s"),
        Map.entry("MICRO_VIDEO_SCRIPT", "生成《%s》微课视频分镜脚本表(镜头/画面/口播/时长)。产业情境: %s。参考资料: %s"),
        Map.entry("KNOWLEDGE_POINT_TALK", "面向高职学生, 用生活类比口语化讲解《%s》。产业情境: %s。参考资料: %s"));

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final RestClient reranker;
    private final LlmExactCache llmCache;
    private static final String SYSTEM_PROMPT =
            "你是职业教育教学资源生成引擎。所有内容必须深度嵌入重庆'33618'产业集群的真实产业场景, 语言适配高职学生。";

    public GenerationService(ChatClient.Builder builder, VectorStore vectorStore,
                             @Value("${app.reranker.url}") String rerankerUrl,
                             LlmExactCache llmCache) {
        this.chatClient = builder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
        this.vectorStore = vectorStore;
        this.llmCache = llmCache;
        this.reranker = RestClient.builder()
                .baseUrl(rerankerUrl)
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout(1000);
                    setReadTimeout(5000);
                }})
                .build();
    }

    /** 向量语义检索(RAG 前置) */
    public List<Document> search(String query, int topK) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(query).topK(topK).build());
    }

    /** 单轮生成: 简单模板资源。返回内容 + cache_hit 标记(观测缓存命中率) */
    public GenerationResult generateWithMeta(String resourceType, String knowledgePoint,
                                             String industryTags, String requirements) {
        String template = TEMPLATES.getOrDefault(resourceType, TEMPLATES.get("KNOWLEDGE_POINT_TALK"));
        List<Document> ctx = search(knowledgePoint + " " + industryTags, 6);
        String context = ctx == null ? "" :
                ctx.stream().map(Document::getText).reduce("", (a, b) -> a + "\n---\n" + b);
        String prompt = template.formatted(knowledgePoint, industryTags, context);
        if (requirements != null && !requirements.isBlank()) {
            prompt = prompt + "\n补充要求: " + requirements;
        }
        // L1 精确缓存: 相同 (资源类型, 知识点, 产业标签, 要求) 的重生成直接命中
        String cached = llmCache.get(resourceType, SYSTEM_PROMPT, prompt);
        if (cached != null) {
            return new GenerationResult(cached, true);
        }
        String content = chatClient.prompt().user(prompt).call().content();
        llmCache.put(resourceType, SYSTEM_PROMPT, prompt, content);
        return new GenerationResult(content, false);
    }

    public record GenerationResult(String content, boolean cacheHit) {}

    /** 兼容旧签名 */
    public String generate(String resourceType, String knowledgePoint,
                           String industryTags, String requirements) {
        return generateWithMeta(resourceType, knowledgePoint, industryTags, requirements).content();
    }

    /** 资源入库后异步建向量索引(供后续 RAG 检索) */
    public void indexResource(String resourceId, String title, String content, Map<String, Object> meta) {
        meta = meta == null ? Map.of() : meta;
        Map<String, Object> m = new java.util.HashMap<>(meta);
        m.put("resourceId", resourceId);
        vectorStore.add(List.of(new Document(resourceId, title + "\n" + content, m)));
    }
}
