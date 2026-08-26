package com.dochub.workbench.modelconfig.support;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

/** Prevents reasoning-only families from being configured in the latency-sensitive chat slot. */
public final class ChatModelPolicyValidator {

    private static final List<String> REASONING_ONLY_PATTERNS = List.of("qwq", "deepseek-r1", "thinking-only");

    public void validate(String modelName) {
        String normalized = modelName == null ? "" : modelName.toLowerCase(Locale.ROOT);
        if (REASONING_ONLY_PATTERNS.stream().anyMatch(normalized::contains)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择支持非推理模式的对话模型");
        }
    }

    public List<String> rejectedReasoningPatterns() {
        return REASONING_ONLY_PATTERNS;
    }
}
