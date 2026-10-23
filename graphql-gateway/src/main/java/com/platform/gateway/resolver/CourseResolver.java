package com.platform.gateway.resolver;

import com.platform.gateway.client.DomainClient;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;

/**
 * Course 域 Resolver — chapters/knowledgePoints/resources 按 courseId 批量装载。
 */
@Controller
public class CourseResolver {

    private final DomainClient client;

    public CourseResolver(DomainClient client) {
        this.client = client;
    }

    @QueryMapping
    public Map<String, Object> course(@Argument String id) {
        return client.getMap("course", "/courses/" + id);
    }

    @QueryMapping
    public List<Map<String, Object>> courses(@Argument String tenantId, @Argument String subject,
                                             @Argument Integer page, @Argument Integer size) {
        // course 返回 ApiResponse{ success, data:{ content:[...] } } — 解包(同 TenantResolver)
        Map<String, Object> resp = client.getMap("course", "/courses?tenantId=" + tenantId
                + (subject == null ? "" : "&subject=" + subject)
                + "&page=" + or(page, 0) + "&size=" + or(size, 20));
        return TenantResolver.unwrapContent(resp);
    }

    /** Course.chapters 批量解析(防 N+1) */
    @BatchMapping
    public Map<String, List<Map<String, Object>>> chapters(List<Map<String, Object>> courses) {
        return batchByCourse(courses, "/chapters?courseId=");
    }

    /** Course.knowledgePoints 批量解析 */
    @BatchMapping
    public Map<String, List<Map<String, Object>>> knowledgePoints(List<Map<String, Object>> courses) {
        return batchByCourse(courses, "/knowledge-points?courseId=");
    }

    /** Course.resources 批量解析 */
    @BatchMapping
    public Map<String, List<Map<String, Object>>> resources(List<Map<String, Object>> courses) {
        return batchByCourse(courses, "/resources?courseId=");
    }

    /** KT 掌握度内联解析: KnowledgePoint.mastery(studentId) */
    @SchemaMapping(typeName = "KnowledgePoint", field = "mastery")
    public Double mastery(Map<String, Object> kp, @Argument String studentId) {
        try {
            Map<String, Object> m = client.getMap("kt",
                    "/kt/mastery?studentId=" + studentId + "&skillId=" + kp.get("code"));
            Object mastery = m.get("mastery");
            return mastery instanceof Number n ? n.doubleValue() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, List<Map<String, Object>>> batchByCourse(List<Map<String, Object>> courses,
                                                                 String pathTemplate) {
        if (courses == null || courses.isEmpty()) return Map.of();
        Map<String, List<Map<String, Object>>> result = new java.util.HashMap<>();
        for (Map<String, Object> c : courses) {
            String courseId = String.valueOf(c.get("id"));
            try {
                result.put(courseId, client.getList("course", pathTemplate + courseId));
            } catch (Exception e) {
                result.put(courseId, List.of());
            }
        }
        return result;
    }

    private int or(Integer v, int d) {
        return v == null ? d : v;
    }
}
