package com.dochub.workbench.manage.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dochub.workbench.manage.data.DochubDocumentTask;
import com.dochub.workbench.manage.mapper.DochubDocumentTaskMapper;
import com.dochub.workbench.manage.mq.DocumentKafkaProducer;
import com.dochub.workbench.manage.mq.message.DocumentIndexBuildMessage;
import lombok.extern.slf4j.Slf4j;
import org.javaup.enums.BusinessStatus;
import org.javaup.enums.DocumentTaskStatusEnum;
import org.javaup.enums.DocumentTaskTypeEnum;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 文枢 DocHub 索引构建任务自愈补偿。
 *
 * <p>与解析任务类似，Kafka 抖动/应用中断等场景下，部分索引构建任务的投递消息可能丢失
 * （offset 已提交但业务处理未真正执行），任务永远停在 NEW（"切块执行"），文档则卡在"构建中"。
 * 本任务定期扫描"超过阈值仍停留在 NEW 且重试未超上限"的索引构建任务，重新投递 Kafka 消息，
 * 让卡住的文档自动恢复构建，无需人工干预。</p>
 */
@Slf4j
@Component
public class DocumentIndexBuildRecoveryTask {

    /** 任务在 NEW 状态超过该时长（分钟）即视为卡住 */
    private static final long STUCK_THRESHOLD_MINUTES = 2L;

    /** 单任务最大重投次数，防止对永久坏任务无限重投 */
    private static final int MAX_RETRY_COUNT = 3;

    private final DochubDocumentTaskMapper taskMapper;

    private final DocumentKafkaProducer kafkaProducer;

    public DocumentIndexBuildRecoveryTask(DochubDocumentTaskMapper taskMapper,
                                          DocumentKafkaProducer kafkaProducer) {
        this.taskMapper = taskMapper;
        this.kafkaProducer = kafkaProducer;
    }

    /**
     * 每 5 分钟扫描一次；启动后延迟 1 分钟执行。
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void recoverStuckIndexBuildTasks() {
        Date threshold = new Date(System.currentTimeMillis() - STUCK_THRESHOLD_MINUTES * 60 * 1000L);
        List<DochubDocumentTask> stuckTasks = taskMapper.selectList(new LambdaQueryWrapper<DochubDocumentTask>()
            .eq(DochubDocumentTask::getTaskType, DocumentTaskTypeEnum.BUILD_INDEX.getCode())
            .eq(DochubDocumentTask::getTaskStatus, DocumentTaskStatusEnum.NEW.getCode())
            .eq(DochubDocumentTask::getStatus, BusinessStatus.YES.getCode())
            .lt(DochubDocumentTask::getEditTime, threshold)
            .lt(DochubDocumentTask::getRetryCount, MAX_RETRY_COUNT));

        if (stuckTasks.isEmpty()) {
            return;
        }
        log.info("索引构建自愈：发现 {} 个卡住的索引构建任务，开始重新投递。", stuckTasks.size());
        for (DochubDocumentTask task : stuckTasks) {
            try {
                kafkaProducer.sendIndexBuild(new DocumentIndexBuildMessage(
                    task.getDocumentId(), task.getId(), task.getPlanId()));
                task.setRetryCount((task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1);
                taskMapper.updateById(task);
                log.info("索引构建自愈：已重新投递索引构建消息 documentId={}, taskId={}, retry={}",
                    task.getDocumentId(), task.getId(), task.getRetryCount());
            }
            catch (Exception exception) {
                log.warn("索引构建自愈：重新投递失败 documentId={}, taskId={}, error={}",
                    task.getDocumentId(), task.getId(), exception.getMessage());
            }
        }
    }
}
