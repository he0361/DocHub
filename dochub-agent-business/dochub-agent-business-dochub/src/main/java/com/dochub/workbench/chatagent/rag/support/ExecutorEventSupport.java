package com.dochub.workbench.chatagent.rag.support;

import cn.hutool.core.util.StrUtil;
import com.dochub.workbench.chatagent.service.TaskInfo;
import com.dochub.workbench.chatagent.support.SinkEmitHelper;
import com.dochub.workbench.chatagent.support.StreamEventWriter;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 支撑组件
 * @author: zhangjihe
 **/

public final class ExecutorEventSupport {

    private ExecutorEventSupport() {
    }

    public static void publishThinking(TaskInfo taskInfo, StreamEventWriter writer, String content) {
        if (taskInfo == null || writer == null || StrUtil.isBlank(content)) {
            return;
        }
        taskInfo.thinkingSteps().add(content);
        SinkEmitHelper.emitNext(taskInfo.sink(), writer.thinking(content, taskInfo.eventMetadata()));
    }

    public static void publishStatus(TaskInfo taskInfo, StreamEventWriter writer, String content) {
        if (taskInfo == null || writer == null || StrUtil.isBlank(content)) {
            return;
        }
        SinkEmitHelper.emitNext(taskInfo.sink(), writer.status(content, taskInfo.eventMetadata()));
    }
}
