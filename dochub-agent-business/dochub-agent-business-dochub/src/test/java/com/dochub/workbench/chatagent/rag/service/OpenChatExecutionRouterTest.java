package com.dochub.workbench.chatagent.rag.service;

import com.dochub.workbench.chatagent.rag.model.ExecutionMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class OpenChatExecutionRouterTest {

    private final OpenChatExecutionRouter router = new OpenChatExecutionRouter();

    @ParameterizedTest
    @ValueSource(strings = {"你好", "解释一下依赖注入", "帮我润色这段话", "Java 的 record 是什么", "现在完成这段代码"})
    void ordinaryQuestionsUseDirectChat(String question) {
        assertThat(router.route(question, null).mode()).isEqualTo(ExecutionMode.DIRECT_CHAT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"搜索今天的 AI 新闻", "查一下当前上海天气", "联网看看最新版本", "请浏览官网查证"})
    void currentOrSearchQuestionsUseReact(String question) {
        assertThat(router.route(question, null).mode()).isEqualTo(ExecutionMode.REACT_AGENT);
    }

    @Test
    void planModeMustBeExplicit() {
        assertThat(router.route("设计一个迁移方案", "PLAN_AND_EXECUTE").mode())
            .isEqualTo(ExecutionMode.PLAN_AND_EXECUTE);
        assertThat(router.route("设计一个迁移方案", null).mode())
            .isEqualTo(ExecutionMode.DIRECT_CHAT);
    }

    @Test
    void explicitDirectAndReactModesAreHonoured() {
        assertThat(router.route("你好", "REACT_AGENT").mode()).isEqualTo(ExecutionMode.REACT_AGENT);
        assertThat(router.route("搜索今天新闻", "DIRECT_CHAT").mode()).isEqualTo(ExecutionMode.DIRECT_CHAT);
    }
}
