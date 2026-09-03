package com.dochub.workbench.manage.support;

import com.dochub.workbench.manage.service.KnowledgeRouteIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class KnowledgeRouteChangedListener {
    private final ObjectProvider<KnowledgeRouteIndexService> routeIndexServiceProvider;

    public KnowledgeRouteChangedListener(ObjectProvider<KnowledgeRouteIndexService> routeIndexServiceProvider) {
        this.routeIndexServiceProvider = routeIndexServiceProvider;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRouteChanged(KnowledgeRouteChangedEvent ignored) {
        KnowledgeRouteIndexService service = routeIndexServiceProvider.getIfAvailable();
        if (service != null) {
            try {
                service.refreshNow();
            }
            catch (RuntimeException exception) {
                log.warn("知识域合并已提交，但路由索引立即刷新失败，后续查询将自动重试: reason={}",
                    ignored.reason(), exception);
            }
        }
    }
}
