package com.dochub.workbench.manage.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dochub.workbench.manage.data.DochubDocumentTask;
import com.dochub.workbench.manage.mapper.DochubDocumentTaskMapper;
import com.dochub.workbench.manage.mq.DocumentKafkaProducer;
import com.dochub.workbench.manage.mq.message.DocumentParseRouteMessage;
import lombok.extern.slf4j.Slf4j;
import org.javaup.enums.BusinessStatus;
import org.javaup.enums.DocumentTaskStatusEnum;
import org.javaup.enums.DocumentTaskTypeEnum;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 文枢 DocHub 解析任务自愈补偿。
 *
 * <p>Kafka 抖动/重启等异常场景下，部分解析任务的投递消息可能丢失，任务永远停在 NEW（"待解析"）。
 * 本任务定期扫描"超过阈值仍停留在 NEW 且重试未超上限"的解析任务，重新投递 Kafka 消息，
 * 让卡住的文档自动恢复解析，无需人工干预。</p>
 */
@Slf4j
@Component
public class DocumentParseRecoveryTask {

    /** 任务在 NEW 状态超过该时长（分钟）即视为卡住 */
    private static final long STUCK_THRESHOLD_MINUTES = 2L;

    /** 单任务最大重投次数，防止对永久坏任务无限重投 */
    private static final int MAX_RETRY_COUNT = 3;

    private final DochubDocumentTaskMapper taskMapper;
    private final DocumentKafkaProducer kafkaProducer;

    public DocumentParseRecoveryTask(DochubDocumentTaskMapper taskMapper,
                                     DocumentKafkaProducer kafkaProducer) {
        this.taskMapper = taskMapper;
        this.kafkaProducer = kafkaProducer;
    }

    /**
     * 每 5 分钟扫描一次；启动后延迟 1 分钟执行。
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void recoverStuckParseTasks() {
        Date threshold = new Date(System.currentTimeMillis() - STUCK_THRESHOLD_MINUTES * 60 * 1000L);
        List<DochubDocumentTask> stuckTasks = taskMapper.selectList(new LambdaQueryWrapper<DochubDocumentTask>()
            .eq(DochubDocumentTask::getTaskType, DocumentTaskTypeEnum.PARSE_ROUTE.getCode())
            .eq(DochubDocumentTask::getTaskStatus, DocumentTaskStatusEnum.NEW.getCode())
            .eq(DochubDocumentTask::getStatus, BusinessStatus.YES.getCode())
            .lt(DochubDocumentTask::getEditTime, threshold)
            .lt(DochubDocumentTask::getRetryCount, MAX_RETRY_COUNT));

        if (stuckTasks.isEmpty()) {
            return;
        }
        log.info("解析自愈：发现 {} 个卡住的解析任务，开始重新投递。", stuckTasks.size());
        for (DochubDocumentTask task : stuckTasks) {
            try {
                kafkaProducer.sendParseRoute(new DocumentParseRouteMessage(task.getDocumentId(), task.getId()));
                task.setRetryCount((task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1);
                taskMapper.updateById(task);
                log.info("解析自愈：已重新投递解析消息 documentId={}, taskId={}, retry={}",
                    task.getDocumentId(), task.getId(), task.getRetryCount());
            }
            catch (Exception exception) {
                log.warn("解析自愈：重新投递失败 documentId={}, taskId={}, error={}",
                    task.getDocumentId(), task.getId(), exception.getMessage());
            }
        }
    }
}
