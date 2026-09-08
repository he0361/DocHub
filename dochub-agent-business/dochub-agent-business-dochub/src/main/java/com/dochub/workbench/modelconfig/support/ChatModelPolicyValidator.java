package com.dochub.workbench.modelconfig.support;

import com.dochub.workbench.modelconfig.config.ModelConfigProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

/** Prevents reasoning-only families from being configured in the latency-sensitive chat slot. */
public final class ChatModelPolicyValidator {

    private final List<String> reasoningOnlyPatterns;

    public ChatModelPolicyValidator() {
        this(List.of("qwq", "deepseek-r1", "thinking-only"));
    }

    public ChatModelPolicyValidator(ModelConfigProperties properties) {
        this(properties.getReasoningOnlyPatterns());
    }

    public ChatModelPolicyValidator(List<String> reasoningOnlyPatterns) {
        this.reasoningOnlyPatterns = reasoningOnlyPatterns == null ? List.of() : reasoningOnlyPatterns.stream()
            .filter(pattern -> pattern != null && !pattern.isBlank()).map(pattern -> pattern.toLowerCase(Locale.ROOT)).toList();
    }

    public void validate(String modelName) {
        String normalized = modelName == null ? "" : modelName.toLowerCase(Locale.ROOT);
        if (reasoningOnlyPatterns.stream().anyMatch(normalized::contains)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择支持非推理模式的对话模型");
        }
    }

    public List<String> rejectedReasoningPatterns() {
        return reasoningOnlyPatterns;
    }
}
