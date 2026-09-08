package com.dochub.workbench.chatagent.service;

import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dochub.workbench.chatagent.data.GraphCheckpoint;
import com.dochub.workbench.chatagent.data.GraphThread;
import com.dochub.workbench.chatagent.mapper.GraphCheckpointMapper;
import com.dochub.workbench.chatagent.mapper.GraphThreadMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatCheckpointManagerTest {

    @Test
    void clearingConversationDeletesBaseAndAllExchangeChildThreads() {
        GraphCheckpointMapper checkpointMapper = mock(GraphCheckpointMapper.class);
        GraphThreadMapper threadMapper = mock(GraphThreadMapper.class);
        GraphThread base = new GraphThread("base-id", "conv", false);
        GraphThread child = new GraphThread("child-id", "conv:exchange:9", false);
        when(threadMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(base, child));
        when(checkpointMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);
        ChatCheckpointManager manager = new ChatCheckpointManager(mock(MysqlSaver.class), checkpointMapper, threadMapper);

        int removed = manager.clearConversation("conv");

        assertThat(removed).isEqualTo(3);
        verify(checkpointMapper).delete(any(LambdaQueryWrapper.class));
        verify(threadMapper).delete(any(LambdaQueryWrapper.class));
    }
}
