package com.dochub.workbench.modelconfig.support;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatModelPolicyValidatorTest {

    private final ChatModelPolicyValidator validator = new ChatModelPolicyValidator();

    @ParameterizedTest
    @ValueSource(strings = {"qwq-32b", "deepseek-r1", "example-thinking-only"})
    void knownReasoningOnlyModelsAreRejectedForLowLatencyChat(String modelName) {
        assertThatThrownBy(() -> validator.validate(modelName))
            .hasMessageContaining("请选择支持非推理模式的对话模型");
    }
}
