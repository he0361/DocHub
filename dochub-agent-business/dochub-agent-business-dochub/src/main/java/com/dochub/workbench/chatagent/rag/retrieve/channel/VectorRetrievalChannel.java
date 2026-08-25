package com.dochub.workbench.chatagent.rag.retrieve.channel;

import com.dochub.workbench.chatagent.rag.config.ChatRagProperties;
import com.dochub.workbench.chatagent.rag.model.ConversationExecutionPlan;
import com.dochub.workbench.chatagent.rag.service.DocumentRetrieveRequestFactory;
import com.dochub.workbench.manage.service.DocumentKnowledgeService;
import org.javaup.enums.RetrievalChannelEnum;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 向量检索通道
 * @author: zhangjihe
 **/

@Component
public class VectorRetrievalChannel implements RetrievalChannel {

    private final DocumentKnowledgeService documentKnowledgeService;
    private final ChatRagProperties properties;
    private final DocumentRetrieveRequestFactory documentRetrieveRequestFactory;

    public VectorRetrievalChannel(DocumentKnowledgeService documentKnowledgeService,
                                  ChatRagProperties properties,
                                  DocumentRetrieveRequestFactory documentRetrieveRequestFactory) {
        this.documentKnowledgeService = documentKnowledgeService;
        this.properties = properties;
        this.documentRetrieveRequestFactory = documentRetrieveRequestFactory;
    }

    @Override
    public String channelName() {
        return RetrievalChannelEnum.VECTOR.getName();
    }

    @Override
    public boolean supports(ConversationExecutionPlan plan) {

        return plan.getSelectedDocumentId() != null;
    }

    @Override
    public RetrievalChannelResult retrieve(String subQuestion, ConversationExecutionPlan plan) {

        List<Document> documentList = documentKnowledgeService.vectorSearch(
            documentRetrieveRequestFactory.build(subQuestion, plan, properties.getVectorTopK())
        );
        return new RetrievalChannelResult(
            channelName(), documentList
        );
    }
}
