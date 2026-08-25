package com.dochub.workbench.chatagent.rag.executor;

import com.dochub.workbench.chatagent.rag.model.ExecutionMode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 对话执行器注册表
 * @author: zhangjihe
 **/

@Component
public class ConversationExecutorRegistry {

    private final Map<ExecutionMode, ConversationExecutor> executorMap = new EnumMap<>(ExecutionMode.class);

    public ConversationExecutorRegistry(List<ConversationExecutor> executors) {
        for (ConversationExecutor executor : executors) {
            executorMap.put(executor.mode(), executor);
        }
    }

    public ConversationExecutor get(ExecutionMode mode) {
        ConversationExecutor executor = executorMap.get(mode);
        if (executor == null) {
            throw new IllegalStateException("未找到执行模式对应的执行器: " + mode);
        }
        return executor;
    }
}
