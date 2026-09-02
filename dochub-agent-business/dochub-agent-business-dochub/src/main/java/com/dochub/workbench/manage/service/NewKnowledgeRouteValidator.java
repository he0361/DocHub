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
public class NewKnowledgeRouteValidator {
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ObjectMapper objectMapper;

    public NewKnowledgeRouteValidator(ObjectProvider<ChatModel> chatModelProvider, ObjectMapper objectMapper) {
        this.chatModelProvider = chatModelProvider;
        this.objectMapper = objectMapper;
    }

    public NewRouteValidation validate(ClassificationMaterial material, RouteProposal proposal, List<RouteCandidate> candidates) {
        ChatModel model = chatModelProvider.getIfAvailable();
        if (model == null) return new NewRouteValidation(false, 0, "chat_model_unavailable");
        try {
            String prompt = "你是独立的保守复核器。判断提案是否真正超出所有现有知识域；同一项目的不同文档应优先复用。" +
                "必须给出其不能归入最佳候选的具体理由。只返回{\"accepted\":false,\"confidence\":0.0,\"reason\":\"\"}。\n文档：" +
                material.titleAndSummary() + "\n提案：" + objectMapper.writeValueAsString(proposal) + "\n最佳候选：" +
                objectMapper.writeValueAsString(candidates.stream().limit(5).toList());
            String content = ChatClient.builder(model).build().prompt().user(prompt).call().content();
            int first = content == null ? -1 : content.indexOf('{'), last = content == null ? -1 : content.lastIndexOf('}');
            if (first < 0 || last <= first) return new NewRouteValidation(false, 0, "malformed_json");
            JsonNode root = objectMapper.readTree(content.substring(first, last + 1));
            return new NewRouteValidation(root.path("accepted").asBoolean(false), clamp(root.path("confidence").asDouble(0)),
                root.path("reason").asText(""));
        } catch (Exception exception) {
            return new NewRouteValidation(false, 0, "validator_error:" + exception.getClass().getSimpleName());
        }
    }
    private double clamp(double value) { return Math.max(0, Math.min(1, value)); }
}
