package com.dochub.workbench.modelconfig.support;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

/** Small seam around the provider call so a candidate can be tested before it is activated. */
@FunctionalInterface
public interface ChatModelConnectionTester {
    void test(ChatModel model, boolean toolCallingSupported);

    static ChatModelConnectionTester defaultTester() {
        return (model, toolCallingSupported) -> model.call(new Prompt("ping"));
    }
}
