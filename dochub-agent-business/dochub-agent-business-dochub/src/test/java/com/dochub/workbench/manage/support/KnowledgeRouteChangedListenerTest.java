package com.dochub.workbench.manage.support;

import com.dochub.workbench.manage.service.KnowledgeRouteIndexService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeRouteChangedListenerTest {

    @Test
    void committedMergeIsNotReportedAsFailedWhenIndexRefreshWillRetryLater() {
        KnowledgeRouteIndexService indexService = mock(KnowledgeRouteIndexService.class);
        doThrow(new IllegalStateException("elasticsearch unavailable")).when(indexService).refreshNow();
        @SuppressWarnings("unchecked")
        ObjectProvider<KnowledgeRouteIndexService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(indexService);
        KnowledgeRouteChangedListener listener = new KnowledgeRouteChangedListener(provider);

        assertThatCode(() -> listener.onRouteChanged(new KnowledgeRouteChangedEvent("scope-merge")))
            .doesNotThrowAnyException();
    }
}
