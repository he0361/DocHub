package com.dochub.workbench.skill.router;

import com.baidu.fsg.uid.UidGenerator;
import com.dochub.workbench.prompt.PromptTemplateService;
import com.dochub.workbench.skill.config.SkillProperties;
import com.dochub.workbench.skill.mapper.DocSkillUsageMapper;
import com.dochub.workbench.skill.model.SkillDefinition;
import com.dochub.workbench.skill.model.SkillMatchResult;
import com.dochub.workbench.skill.registry.SkillRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillSceneRouterTest {

    @Test
    void noCandidateReturnsNoMatchWithoutCallingModel() {
        Fixture fixture = fixture(skill("weekly-report", "周报"));

        assertThat(fixture.router.route("你好")).isNull();
        verify(fixture.chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void strongRuleMatchSkipsLlm() {
        Fixture fixture = fixture(skill("weekly-report", "周报,项目周报"));

        SkillMatchResult result = fixture.router.route("请生成项目周报");

        assertThat(result).isNotNull();
        assertThat(result.getSkill().getName()).isEqualTo("weekly-report");
        verify(fixture.chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void closeCandidatesUseLlmOnceAndCannotSelectOutsideCandidateSet() {
        Fixture fixture = fixture(skill("report", "报告"), skill("compare", "方案"));
        when(fixture.chatModel.call(any(Prompt.class))).thenReturn(response("{\"skillName\":\"report\",\"reason\":\"fit\"}"));

        SkillMatchResult result = fixture.router.route("比较这两个方案并给出报告");

        assertThat(result).isNotNull();
        assertThat(result.getSkill().getName()).isEqualTo("report");
        verify(fixture.chatModel).call(any(Prompt.class));
    }

    private Fixture fixture(SkillDefinition... skills) {
        SkillProperties properties = new SkillProperties();
        SkillRegistry registry = new SkillRegistry();
        for (SkillDefinition skill : skills) {
            registry.register(skill);
        }
        ChatModel chatModel = mock(ChatModel.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chatModel);
        PromptTemplateService promptService = mock(PromptTemplateService.class);
        when(promptService.render(anyString(), anyMap())).thenReturn("route");
        return new Fixture(new SkillSceneRouter(
            properties, registry, mock(DocSkillUsageMapper.class), mock(UidGenerator.class), provider,
            promptService, new ObjectMapper()), chatModel);
    }

    private SkillDefinition skill(String name, String tags) {
        return SkillDefinition.builder()
            .id(1L)
            .name(name)
            .displayName(name)
            .description(name)
            .whenToUse(tags)
            .tags(tags)
            .runState(1)
            .build();
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private record Fixture(SkillSceneRouter router, ChatModel chatModel) {
    }
}
