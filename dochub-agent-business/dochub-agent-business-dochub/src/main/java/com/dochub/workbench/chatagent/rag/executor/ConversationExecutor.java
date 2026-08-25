package com.dochub.workbench.chatagent.rag.executor;

import com.dochub.workbench.chatagent.rag.model.ExecutionMode;
import com.dochub.workbench.chatagent.service.TaskInfo;
import reactor.core.publisher.Flux;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 统一对话执行器抽象
 * @author: zhangjihe
 **/

public interface ConversationExecutor {

    ExecutionMode mode();

    Flux<String> execute(TaskInfo taskInfo);
}
