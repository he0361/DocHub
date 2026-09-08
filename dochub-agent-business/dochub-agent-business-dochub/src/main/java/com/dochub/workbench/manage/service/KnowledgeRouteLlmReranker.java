package com.dochub.workbench.manage.service;

import com.dochub.workbench.manage.model.classify.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeRouteLlmReranker {
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ObjectMapper objectMapper;

    public KnowledgeRouteLlmReranker(ObjectProvider<ChatModel> chatModelProvider, ObjectMapper objectMapper) {
        this.chatModelProvider = chatModelProvider;
        this.objectMapper = objectMapper;
    }

    public LlmRouteAssessment assess(ClassificationMaterial material, List<RouteCandidate> candidates) {
        ChatModel model = chatModelProvider.getIfAvailable();
        RouteProposal fallback = new RouteProposal("", "", "", "", "", "", "");
        if (model == null) return LlmRouteAssessment.unavailable(fallback, "chat_model_unavailable");
        try {
            String prompt = "你是保守的知识域归类器。多个关于同一项目的文档通常共享一个知识域。必须逐一比较所有候选；" +
                "LLM置信度只是证据。仅返回严格JSON。\n文档：" + material.titleAndSummary() + "\n内容：" + material.semanticText() +
                "\n候选：" + objectMapper.writeValueAsString(candidates) +
                "\n格式：{\"matchType\":\"existing|new\",\"selectedScopeCode\":\"\",\"confidence\":0.0," +
                "\"scope\":{\"code\":\"\",\"name\":\"\",\"description\":\"\"}," +
                "\"topic\":{\"matchType\":\"existing|new|none\",\"code\":\"\",\"name\":\"\",\"description\":\"\"}," +
                "\"businessCategory\":\"\",\"reason\":\"\"}";
            JsonNode root = parse(ChatClient.builder(model).build().prompt().user(prompt).call().content());
            if (root == null) return LlmRouteAssessment.unavailable(fallback, "malformed_json");
            JsonNode scope = root.path("scope"), topic = root.path("topic");
            RouteProposal proposal = new RouteProposal(text(scope, "code"), text(scope, "name"), text(scope, "description"),
                text(topic, "code"), text(topic, "name"), text(topic, "description"), text(root, "businessCategory"));
            return new LlmRouteAssessment("new".equalsIgnoreCase(text(root, "matchType")), text(root, "selectedScopeCode"),
                proposal, clamp(root.path("confidence").asDouble(0)), text(root, "reason"),
                "new".equalsIgnoreCase(text(topic, "matchType")));
        } catch (Exception exception) {
            return LlmRouteAssessment.unavailable(fallback, "reranker_error:" + exception.getClass().getSimpleName());
        }
    }

    private JsonNode parse(String value) {
        if (value == null) return null;
        int first = value.indexOf('{'), last = value.lastIndexOf('}');
        if (first < 0 || last <= first) return null;
        try { return objectMapper.readTree(value.substring(first, last + 1)); } catch (Exception ignored) { return null; }
    }
    private String text(JsonNode node, String field) { return node.path(field).asText("").trim(); }
    private double clamp(double value) { return Math.max(0, Math.min(1, value)); }
}
