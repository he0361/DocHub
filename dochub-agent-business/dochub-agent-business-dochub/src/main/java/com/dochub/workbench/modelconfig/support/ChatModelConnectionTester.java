package com.dochub.workbench.modelconfig.support;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.function.FunctionToolCallback;

/** Small seam around the provider call so a candidate can be tested before it is activated. */
@FunctionalInterface
public interface ChatModelConnectionTester {
    void test(ChatModel model, boolean toolCallingSupported);

    static ChatModelConnectionTester defaultTester() {
        return (model, toolCallingSupported) -> {
            if (!toolCallingSupported) {
                model.call(new Prompt("ping"));
                return;
            }
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                .toolChoice("required")
                .internalToolExecutionEnabled(false)
                .toolCallbacks(FunctionToolCallback.builder("dochub_connection_probe", () -> "ok")
                    .description("Deterministic connection capability probe")
                    .inputSchema("{\"type\":\"object\",\"properties\":{}}")
                    .build())
                .build();
            ChatResponse response = model.call(new Prompt("Call dochub_connection_probe exactly once.", options));
            if (response == null || !response.hasToolCalls() || response.getResult() == null
                || response.getResult().getOutput() == null || !response.getResult().getOutput().hasToolCalls()) {
                throw new IllegalStateException("工具调用协议测试失败");
            }
        };
    }
}
