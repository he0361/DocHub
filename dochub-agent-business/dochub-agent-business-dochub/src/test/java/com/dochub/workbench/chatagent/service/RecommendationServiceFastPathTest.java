package com.dochub.workbench.chatagent.service;

import com.dochub.workbench.chatagent.config.ChatAgentProperties;
import com.dochub.workbench.chatagent.rag.model.ExecutionMode;
import com.dochub.workbench.prompt.PromptTemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class RecommendationServiceFastPathTest {

    @Test
    void directChatDoesNotStartASecondRecommendationModelCall() {
        ChatAgentProperties properties = new ChatAgentProperties();
        properties.setRecommendationEnabled(true);
        ExecutorService executor = mock(ExecutorService.class);
        ObservedChatModelService observed = mock(ObservedChatModelService.class);
        RecommendationService service = new RecommendationService(
            properties, new ObjectMapper(), executor, observed, mock(PromptTemplateService.class));

        assertThat(service.generateRecommendations(
            "question", "answer", List.of(), ExecutionMode.DIRECT_CHAT, null)).isEmpty();

        verifyNoInteractions(executor, observed);
    }
}
