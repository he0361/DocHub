package com.dochub.workbench.manage.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.dochub.workbench.manage.data.DochubDocument;
import com.dochub.workbench.manage.data.DochubDocumentChunk;
import com.dochub.workbench.manage.data.DochubDocumentParentBlock;
import com.dochub.workbench.manage.data.DochubDocumentStrategyPlan;
import com.dochub.workbench.manage.data.DochubDocumentStrategyStep;
import com.dochub.workbench.manage.data.DochubDocumentStructureNode;
import com.dochub.workbench.manage.data.DochubDocumentTask;
import com.dochub.workbench.manage.mapper.DochubDocumentChunkMapper;
import com.dochub.workbench.manage.mapper.DochubDocumentMapper;
import com.dochub.workbench.manage.mapper.DochubDocumentParentBlockMapper;
import com.dochub.workbench.manage.mapper.DochubDocumentStrategyPlanMapper;
import com.dochub.workbench.manage.mapper.DochubDocumentStrategyStepMapper;
import com.dochub.workbench.manage.mapper.DochubDocumentTaskMapper;
import com.dochub.workbench.manage.service.DocumentAsyncProcessService;
import com.dochub.workbench.manage.service.DocumentNavigationIndexService;
import com.dochub.workbench.manage.service.DocumentParserService;
import com.dochub.workbench.manage.service.DocumentProfileService;
import com.dochub.workbench.manage.service.DocumentStorageService;
import com.dochub.workbench.manage.service.DocumentStrategyService;
import com.dochub.workbench.manage.support.DocumentIndexBuildProgressService;
import com.dochub.workbench.manage.service.DocumentStructureGraphProjectionService;
import com.dochub.workbench.manage.service.DocumentStructureNodeService;
import com.dochub.workbench.manage.service.DocumentTaskLogService;
import com.dochub.workbench.manage.service.DocumentVectorGateway;
import com.dochub.workbench.manage.service.keyword.DocumentKeywordSearchGateway;
import com.dochub.workbench.manage.support.ChunkCandidate;
import com.dochub.workbench.manage.support.DocumentAnalysisResult;
import com.dochub.workbench.manage.support.DocumentStrategyPlanDraft;
import com.dochub.workbench.manage.support.DocumentStrategyStepDraft;
import com.dochub.workbench.manage.support.ParentBlockCandidate;
import org.javaup.enums.BusinessStatus;
import org.javaup.enums.DocumentChunkSourceTypeEnum;
import org.javaup.enums.DocumentFileTypeEnum;
import org.javaup.enums.DocumentIndexStatusEnum;
import org.javaup.enums.DocumentLogLevelEnum;
import org.javaup.enums.DocumentOperatorTypeEnum;
import org.javaup.enums.DocumentParseStatusEnum;
import org.javaup.enums.DocumentPlanSourceEnum;
import org.javaup.enums.DocumentPlanStatusEnum;
import org.javaup.enums.DocumentStrategyExecuteStatusEnum;
import org.javaup.enums.DocumentStrategyPipelineTypeEnum;
import org.javaup.enums.DocumentStrategyStatusEnum;
import org.javaup.enums.DocumentTaskEventTypeEnum;
import org.javaup.enums.DocumentTaskStageEnum;
import org.javaup.enums.DocumentTaskStatusEnum;
import org.javaup.enums.DocumentVectorStatusEnum;
import org.javaup.enums.DocumentVectorStoreTypeEnum;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 服务实现层
 * @author: zhangjihe
 **/

@Slf4j
@AllArgsConstructor
@Service
public class DocumentAsyncProcessServiceImpl implements DocumentAsyncProcessService {

    private final DochubDocumentMapper documentMapper;

    private final DochubDocumentStrategyPlanMapper planMapper;

    private final DochubDocumentStrategyStepMapper stepMapper;

    private final DochubDocumentTaskMapper taskMapper;

    private final DochubDocumentParentBlockMapper parentBlockMapper;

    private final DochubDocumentChunkMapper chunkMapper;

    private final DocumentStorageService storageService;

    private final DocumentParserService parserService;

    private final DocumentStrategyService strategyService;

    private final DocumentStructureNodeService structureNodeService;

    private final DocumentTaskLogService taskLogService;

    private final DocumentVectorGateway vectorGateway;

    private final ObjectProvider<DocumentKeywordSearchGateway> keywordSearchGatewayProvider;

    private final ObjectProvider<DocumentNavigationIndexService> navigationIndexServiceProvider;

    private final ObjectProvider<DocumentStructureGraphProjectionService> graphProjectionServiceProvider;

    private final DocumentProfileService documentProfileService;

    private final DocumentIndexBuildProgressService indexBuildProgressService;

    @Resource
    private UidGenerator uidGenerator;

    @Override

    public void handleParseRoute(Long documentId, Long taskId) {

        DochubDocument document = documentMapper.selectById(documentId);
        DochubDocumentTask task = taskMapper.selectById(taskId);
        if (document == null || task == null) {
            log.warn("解析任务对应的文档或任务不存在，documentId={}, taskId={}", documentId, taskId);
            return;
        }

        Date startTime = new Date();
        try {
            // 进入异步解析后，先把任务状态切成RUNNING，当前阶段设为“内容解析”。
            task.setTaskStatus(DocumentTaskStatusEnum.RUNNING.getCode());
            task.setCurrentStage(DocumentTaskStageEnum.CONTENT_PARSE.getCode());
            task.setStartTime(startTime);
            taskMapper.updateById(task);
            //文档主表的解析状态也同步到PARSING，方便前端查询时看到实时状态。
            document.setParseStatus(DocumentParseStatusEnum.PARSING.getCode());
            documentMapper.updateById(document);

            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.CONTENT_PARSE.getCode(),
                DocumentTaskEventTypeEnum.START.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "开始解析文档内容。",
                Map.of("objectName", document.getObjectName()));
            //重新从对象存储下载原始文件，确保异步链处理的是“上传后实际持久化成功”的那份文件。
            byte[] fileBytes = storageService.downloadObject(document.getObjectName());
            //parserService 会负责提取纯文本、结构节点候选、字符数、token数、质量等级等分析结果
            DocumentAnalysisResult analysisResult = parserService.parse(fileBytes, document.getOriginalFileName(),
                document.getMimeType(), DocumentFileTypeEnum.getRc(document.getFileType()));

            String parseTextPath = storageService.uploadParsedText(documentId, analysisResult.getParsedText());

            List<DochubDocumentStructureNode> structureNodes = structureNodeService.replaceDocumentNodes(
                documentId,
                taskId,
                analysisResult.getStructureNodes()
            );
            int structureNodeCount = structureNodes.size();
            syncNavigationArtifacts(documentId, taskId, structureNodes);
            documentProfileService.generateProfile(documentId, analysisResult, structureNodes);

            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.CONTENT_PARSE.getCode(),
                DocumentTaskEventTypeEnum.COMPLETE.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "文档解析完成。",
                Map.of(
                    "charCount", analysisResult.getCharCount(),
                    "tokenCount", analysisResult.getTokenCount(),
                    "structureLevel", analysisResult.getStructureLevel(),
                    "contentQualityLevel", analysisResult.getContentQualityLevel(),
                    "structureNodeCount", structureNodeCount
                ));

            task.setCurrentStage(DocumentTaskStageEnum.STRATEGY_ROUTE.getCode());
            taskMapper.updateById(task);

            DocumentStrategyPlanDraft planDraft = strategyService.recommendStrategy(document, analysisResult);
            Long planId = uidGenerator.getUid();
            int planVersion = getNextPlanVersion(documentId);

            DochubDocumentStrategyPlan plan = new DochubDocumentStrategyPlan();
            plan.setId(planId);
            plan.setDocumentId(documentId);
            plan.setPlanVersion(planVersion);
            plan.setPlanSource(DocumentPlanSourceEnum.SYSTEM_RECOMMEND.getCode());
            plan.setPlanStatus(DocumentPlanStatusEnum.WAIT_CONFIRM.getCode());
            plan.setStrategyCount(planDraft.getParentSteps().size() + planDraft.getChildSteps().size());
            plan.setStrategySnapshot(planDraft.getStrategySnapshot());
            plan.setRecommendReason(planDraft.getRecommendReason());
            plan.setStatus(BusinessStatus.YES.getCode());
            planMapper.insert(plan);

            for (int index = 0; index < planDraft.getParentSteps().size(); index++) {
                DocumentStrategyStepDraft draft = planDraft.getParentSteps().get(index);
                DochubDocumentStrategyStep step = new DochubDocumentStrategyStep();
                step.setId(uidGenerator.getUid());
                step.setPlanId(planId);
                step.setDocumentId(documentId);
                step.setPipelineType(draft.getPipelineType());
                step.setStepNo(index + 1);
                step.setStrategyType(draft.getStrategyType());
                step.setStrategyRole(draft.getStrategyRole());
                step.setSourceType(draft.getSourceType());
                step.setExecuteStatus(DocumentStrategyExecuteStatusEnum.WAIT_EXECUTE.getCode());
                step.setRecommendReason(draft.getRecommendReason());
                step.setStatus(BusinessStatus.YES.getCode());
                stepMapper.insert(step);
            }
            for (int index = 0; index < planDraft.getChildSteps().size(); index++) {
                DocumentStrategyStepDraft draft = planDraft.getChildSteps().get(index);
                DochubDocumentStrategyStep step = new DochubDocumentStrategyStep();
                step.setId(uidGenerator.getUid());
                step.setPlanId(planId);
                step.setDocumentId(documentId);
                step.setPipelineType(draft.getPipelineType());
                step.setStepNo(index + 1);
                step.setStrategyType(draft.getStrategyType());
                step.setStrategyRole(draft.getStrategyRole());
                step.setSourceType(draft.getSourceType());
                step.setExecuteStatus(DocumentStrategyExecuteStatusEnum.WAIT_EXECUTE.getCode());
                step.setRecommendReason(draft.getRecommendReason());
                step.setStatus(BusinessStatus.YES.getCode());
                stepMapper.insert(step);
            }

            document.setParseStatus(DocumentParseStatusEnum.PARSE_SUCCESS.getCode());
            document.setStrategyStatus(DocumentStrategyStatusEnum.RECOMMENDED.getCode());
            document.setCharCount(analysisResult.getCharCount());
            document.setTokenCount(analysisResult.getTokenCount());
            document.setStructureLevel(analysisResult.getStructureLevel());
            document.setContentQualityLevel(analysisResult.getContentQualityLevel());
            document.setParseTextPath(parseTextPath);
            document.setParseErrorMsg(null);
            document.setCurrentPlanId(planId);
            document.setLastParseTaskId(taskId);
            document.setStructureNodeCount(structureNodeCount);
            documentMapper.updateById(document);

            finishTaskSuccess(task, DocumentTaskStageEnum.STRATEGY_ROUTE.getCode(), startTime);
            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.STRATEGY_ROUTE.getCode(),
                DocumentTaskEventTypeEnum.RECOMMEND_STRATEGY.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "系统已生成推荐策略。",
                detail("planId", planId,
                    "strategySnapshot", planDraft.getStrategySnapshot(),
                    "parentStepCount", planDraft.getParentSteps().size(),
                    "childStepCount", planDraft.getChildSteps().size(),
                    "structureNodeCount", structureNodeCount,
                    "recommendReason", planDraft.getRecommendReason()));
        }
        catch (Exception exception) {
            log.error("异步解析文档失败，documentId={}, taskId={}", documentId, taskId, exception);

            document.setParseStatus(DocumentParseStatusEnum.PARSE_FAILED.getCode());
            document.setParseErrorMsg(exception.getMessage());
            documentMapper.updateById(document);

            failTask(task, startTime, exception, DocumentTaskStageEnum.CONTENT_PARSE.getCode());
            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.CONTENT_PARSE.getCode(),
                DocumentTaskEventTypeEnum.FAILED.getCode(),
                DocumentLogLevelEnum.ERROR.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "文档解析失败。",
                detail("error", exception.getMessage()));
        }
    }

    @Override
    public void handleIndexBuild(Long documentId, Long taskId, Long planId) {

        DochubDocument document = documentMapper.selectById(documentId);
        DochubDocumentTask task = taskMapper.selectById(taskId);
        DochubDocumentStrategyPlan plan = planMapper.selectById(planId);
        if (document == null || task == null || plan == null) {
            log.warn("索引任务对应的数据不存在，documentId={}, taskId={}, planId={}", documentId, taskId, planId);
            return;
        }

        // 幂等保护：只有 NEW 状态的任务才真正执行。
        // Kafka 可能重复投递（自愈重投、重启后重读），对已成功/已失败/正在执行的任务直接跳过，避免重复切块入库。
        if (!DocumentTaskStatusEnum.NEW.getCode().equals(task.getTaskStatus())) {
            log.info("索引任务已处理或正在执行，跳过本次消费。taskId={}, taskStatus={}", taskId, task.getTaskStatus());
            return;
        }

        Date startTime = new Date();

        List<DochubDocumentStrategyStep> stepList = listSteps(planId);
        try {

            task.setTaskStatus(DocumentTaskStatusEnum.RUNNING.getCode());
            task.setCurrentStage(DocumentTaskStageEnum.CHUNK_EXECUTE.getCode());
            task.setStartTime(startTime);
            taskMapper.updateById(task);

            document.setIndexStatus(DocumentIndexStatusEnum.BUILDING.getCode());
            documentMapper.updateById(document);

            updateStepExecuteStatus(planId, DocumentStrategyExecuteStatusEnum.EXECUTING.getCode());

            indexBuildProgressService.start(documentId, taskId);

            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.CHUNK_EXECUTE.getCode(),
                DocumentTaskEventTypeEnum.START.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "开始执行切块流水线。",
                Map.of("strategySnapshot", plan.getStrategySnapshot()));

            String parsedText = storageService.downloadText(document.getParseTextPath());

            List<ParentBlockCandidate> parentBlockCandidateList = strategyService.buildParentBlocks(document, plan, stepList, parsedText);

            updateStepExecuteStatus(planId, DocumentStrategyExecuteStatusEnum.EXECUTE_SUCCESS.getCode());

            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.CHUNK_EXECUTE.getCode(),
                DocumentTaskEventTypeEnum.COMPLETE.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "切块执行完成。",
                Map.of(
                    "parentCount", parentBlockCandidateList.size(),
                    "childCount", countChildCandidates(parentBlockCandidateList)
                ));

            indexBuildProgressService.reportStage(documentId, taskId, DocumentTaskStageEnum.CHUNK_EXECUTE, 65,
                "切块执行完成：父块 " + parentBlockCandidateList.size()
                    + "，子块 " + countChildCandidates(parentBlockCandidateList));

            task.setCurrentStage(DocumentTaskStageEnum.CHUNK_POST_PROCESS.getCode());
            taskMapper.updateById(task);

            List<ParentBlockCandidate> finalParentBlockList = parentBlockCandidateList.stream()
                .filter(item -> item != null
                    && StrUtil.isNotBlank(item.getText())
                    && item.getChildChunks() != null
                    && item.getChildChunks().stream().anyMatch(child -> StrUtil.isNotBlank(child.getText())))
                .toList();

            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.CHUNK_POST_PROCESS.getCode(),
                DocumentTaskEventTypeEnum.COMPLETE.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "切块后处理完成。",
                Map.of(
                    "parentCount", finalParentBlockList.size(),
                    "childCount", countChildCandidates(finalParentBlockList)
                ));

            indexBuildProgressService.reportStage(documentId, taskId, DocumentTaskStageEnum.CHUNK_POST_PROCESS, 70,
                "切块后处理完成");

            ParentChildEntityBundle entityBundle = buildParentChildEntities(documentId, taskId, planId, finalParentBlockList);
            List<DochubDocumentParentBlock> parentBlockEntityList = entityBundle.parentBlocks();
            List<DochubDocumentChunk> chunkEntityList = entityBundle.childChunks();

            for (DochubDocumentParentBlock parentBlock : parentBlockEntityList) {
                parentBlockMapper.insert(parentBlock);
            }
            for (DochubDocumentChunk chunk : chunkEntityList) {
                chunkMapper.insert(chunk);
            }

            task.setCurrentStage(DocumentTaskStageEnum.VECTORIZE.getCode());
            taskMapper.updateById(task);

            indexBuildProgressService.reportStage(documentId, taskId, DocumentTaskStageEnum.VECTORIZE, 72, "开始向量化");

            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.VECTORIZE.getCode(),
                DocumentTaskEventTypeEnum.START.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "开始执行向量化。",
                detail("chunkCount", chunkEntityList.size(),
                    "embeddingBatchSize", DefaultDocumentVectorGateway.EMBEDDING_BATCH_SIZE_LIMIT,
                    "embeddingBatchCount",
                    (chunkEntityList.size() + DefaultDocumentVectorGateway.EMBEDDING_BATCH_SIZE_LIMIT - 1)
                        / DefaultDocumentVectorGateway.EMBEDDING_BATCH_SIZE_LIMIT,
                    "vectorStoreType", DocumentVectorStoreTypeEnum.QDRANT.getMsg(),
                    "parentCount", parentBlockEntityList.size()));

            vectorGateway.vectorize(chunkEntityList);

            indexBuildProgressService.reportStage(documentId, taskId, DocumentTaskStageEnum.VECTORIZE, 88, "向量化完成");

            // 关键词通道：独立阶段写入 Elasticsearch，与向量通道（Qdrant）分开
            task.setCurrentStage(DocumentTaskStageEnum.KEYWORD_INDEX.getCode());
            taskMapper.updateById(task);
            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.KEYWORD_INDEX.getCode(),
                DocumentTaskEventTypeEnum.START.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "开始同步关键词索引到 Elasticsearch。",
                detail("chunkCount", chunkEntityList.size()));

            DocumentKeywordSearchGateway keywordSearchGateway = keywordSearchGatewayProvider.getIfAvailable();
            if (keywordSearchGateway != null) {
                keywordSearchGateway.indexChunks(chunkEntityList);
            }
            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.KEYWORD_INDEX.getCode(),
                DocumentTaskEventTypeEnum.COMPLETE.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "关键词索引同步完成。",
                detail("chunkCount", chunkEntityList.size()));

            indexBuildProgressService.reportStage(documentId, taskId, DocumentTaskStageEnum.KEYWORD_INDEX, 95,
                "关键词索引完成");

            for (DochubDocumentChunk chunk : chunkEntityList) {
                chunkMapper.updateById(chunk);
            }

            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.VECTORIZE.getCode(),
                DocumentTaskEventTypeEnum.COMPLETE.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "向量化完成。",
                detail("chunkCount", chunkEntityList.size(),
                    "embeddingBatchSize", DefaultDocumentVectorGateway.EMBEDDING_BATCH_SIZE_LIMIT,
                    "embeddingBatchCount",
                    (chunkEntityList.size() + DefaultDocumentVectorGateway.EMBEDDING_BATCH_SIZE_LIMIT - 1)
                        / DefaultDocumentVectorGateway.EMBEDDING_BATCH_SIZE_LIMIT,
                    "vectorStoreType", DocumentVectorStoreTypeEnum.QDRANT.getMsg(),
                    "parentCount", parentBlockEntityList.size()));

            task.setCurrentStage(DocumentTaskStageEnum.STORE_COMPLETE.getCode());
            taskMapper.updateById(task);

            plan.setPlanStatus(DocumentPlanStatusEnum.EXECUTED.getCode());
            planMapper.updateById(plan);

            document.setIndexStatus(DocumentIndexStatusEnum.BUILD_SUCCESS.getCode());
            document.setLastIndexTaskId(taskId);
            documentMapper.updateById(document);

            indexBuildProgressService.finish(documentId, taskId);

            finishTaskSuccess(task, DocumentTaskStageEnum.STORE_COMPLETE.getCode(), startTime);
            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.STORE_COMPLETE.getCode(),
                DocumentTaskEventTypeEnum.COMPLETE.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "索引构建完成。",
                Map.of("taskId", taskId, "chunkCount", chunkEntityList.size(), "parentCount", parentBlockEntityList.size()));
        }
        catch (Exception exception) {
            log.error("异步构建索引失败，documentId={}, taskId={}, planId={}", documentId, taskId, planId, exception);

            indexBuildProgressService.fail(documentId, taskId,
                "索引构建失败：" + (exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));

            document.setIndexStatus(DocumentIndexStatusEnum.BUILD_FAILED.getCode());
            documentMapper.updateById(document);

            chunkMapper.update(null, new LambdaUpdateWrapper<DochubDocumentChunk>()
                .eq(DochubDocumentChunk::getTaskId, taskId)
                .eq(DochubDocumentChunk::getStatus, BusinessStatus.YES.getCode())
                .set(DochubDocumentChunk::getVectorStatus, DocumentVectorStatusEnum.VECTOR_FAILED.getCode())
                .set(DochubDocumentChunk::getVectorStoreType, DocumentVectorStoreTypeEnum.QDRANT.getCode()));

            updateStepExecuteStatus(planId, DocumentStrategyExecuteStatusEnum.EXECUTE_FAILED.getCode());
            failTask(task, startTime, exception, task.getCurrentStage());
            taskLogService.saveLog(taskId, documentId,
                task.getCurrentStage(),
                DocumentTaskEventTypeEnum.FAILED.getCode(),
                DocumentLogLevelEnum.ERROR.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "索引构建失败。",
                detail("error", exception.getMessage()));
        }
    }

    private ParentChildEntityBundle buildParentChildEntities(Long documentId,
                                                             Long taskId,
                                                             Long planId,
                                                             List<ParentBlockCandidate> parentBlockCandidateList) {
        List<DochubDocumentParentBlock> parentBlockEntityList = new ArrayList<>();
        List<DochubDocumentChunk> chunkEntityList = new ArrayList<>();
        int globalChunkNo = 1;

        for (int parentIndex = 0; parentIndex < parentBlockCandidateList.size(); parentIndex++) {
            ParentBlockCandidate parentCandidate = parentBlockCandidateList.get(parentIndex);
            if (parentCandidate == null || StrUtil.isBlank(parentCandidate.getText())) {
                continue;
            }

            DochubDocumentParentBlock parentBlock = new DochubDocumentParentBlock();
            parentBlock.setId(uidGenerator.getUid());
            parentBlock.setDocumentId(documentId);
            parentBlock.setTaskId(taskId);
            parentBlock.setPlanId(planId);
            parentBlock.setParentNo(parentIndex + 1);
            parentBlock.setSourceType(parentCandidate.getSourceType() == null
                ? DocumentChunkSourceTypeEnum.ORIGINAL.getCode() : parentCandidate.getSourceType());
            parentBlock.setSectionPath(parentCandidate.getSectionPath());
            parentBlock.setStructureNodeId(parentCandidate.getStructureNodeId());
            parentBlock.setStructureNodeType(parentCandidate.getStructureNodeType());
            parentBlock.setCanonicalPath(parentCandidate.getCanonicalPath());
            parentBlock.setItemIndex(parentCandidate.getItemIndex());
            parentBlock.setParentText(parentCandidate.getText().trim());
            parentBlock.setCharCount(parentCandidate.getText().length());
            parentBlock.setTokenCount(estimateTokenCount(parentCandidate.getText()));
            parentBlock.setStatus(BusinessStatus.YES.getCode());

            int startChunkNo = globalChunkNo;
            int childCount = 0;
            for (ChunkCandidate childCandidate : parentCandidate.getChildChunks()) {
                if (childCandidate == null || StrUtil.isBlank(childCandidate.getText())) {
                    continue;
                }
                DochubDocumentChunk chunk = new DochubDocumentChunk();
                chunk.setId(uidGenerator.getUid());
                chunk.setDocumentId(documentId);
                chunk.setTaskId(taskId);
                chunk.setPlanId(planId);
                chunk.setParentBlockId(parentBlock.getId());
                chunk.setChunkNo(globalChunkNo++);
                chunk.setSourceType(childCandidate.getSourceType() == null
                    ? DocumentChunkSourceTypeEnum.ORIGINAL.getCode() : childCandidate.getSourceType());
                chunk.setSectionPath(StrUtil.blankToDefault(childCandidate.getSectionPath(), parentCandidate.getSectionPath()));
                chunk.setStructureNodeId(childCandidate.getStructureNodeId());
                chunk.setStructureNodeType(childCandidate.getStructureNodeType());
                chunk.setCanonicalPath(childCandidate.getCanonicalPath());
                chunk.setItemIndex(childCandidate.getItemIndex());
                chunk.setChunkText(childCandidate.getText().trim());
                chunk.setCharCount(childCandidate.getText().length());

                chunk.setTokenCount(estimateTokenCount(childCandidate.getText()));
                chunk.setVectorStatus(DocumentVectorStatusEnum.WAIT_VECTOR.getCode());
                chunk.setVectorStoreType(DocumentVectorStoreTypeEnum.QDRANT.getCode());
                chunk.setStatus(BusinessStatus.YES.getCode());
                chunkEntityList.add(chunk);
                childCount++;
            }

            parentBlock.setChildCount(childCount);
            parentBlock.setStartChunkNo(childCount == 0 ? null : startChunkNo);
            parentBlock.setEndChunkNo(childCount == 0 ? null : globalChunkNo - 1);
            parentBlockEntityList.add(parentBlock);
        }

        return new ParentChildEntityBundle(parentBlockEntityList, chunkEntityList);
    }

    private int countChildCandidates(List<ParentBlockCandidate> parentBlockCandidateList) {
        if (parentBlockCandidateList == null || parentBlockCandidateList.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ParentBlockCandidate candidate : parentBlockCandidateList) {
            if (candidate == null || candidate.getChildChunks() == null) {
                continue;
            }
            count += (int) candidate.getChildChunks().stream()
                .filter(child -> child != null && StrUtil.isNotBlank(child.getText()))
                .count();
        }
        return count;
    }

    private void updateStepExecuteStatus(Long planId, Integer executeStatus) {

        stepMapper.update(null, new LambdaUpdateWrapper<DochubDocumentStrategyStep>()
            .eq(DochubDocumentStrategyStep::getPlanId, planId)
            .eq(DochubDocumentStrategyStep::getStatus, BusinessStatus.YES.getCode())
            .set(DochubDocumentStrategyStep::getExecuteStatus, executeStatus));
    }

    private List<DochubDocumentStrategyStep> listSteps(Long planId) {
        List<DochubDocumentStrategyStep> stepList = stepMapper.selectList(new LambdaQueryWrapper<DochubDocumentStrategyStep>()
            .eq(DochubDocumentStrategyStep::getPlanId, planId)
            .eq(DochubDocumentStrategyStep::getStatus, BusinessStatus.YES.getCode()));
        return stepList.stream()
            .sorted(Comparator
                .comparingInt((DochubDocumentStrategyStep step) -> pipelineOrder(step.getPipelineType()))
                .thenComparing(DochubDocumentStrategyStep::getStepNo)
                .thenComparing(DochubDocumentStrategyStep::getId))
            .toList();
    }

    private int pipelineOrder(String pipelineType) {
        return DocumentStrategyPipelineTypeEnum.PARENT.getCode().equalsIgnoreCase(
            StrUtil.blankToDefault(pipelineType, "")
        ) ? 0 : 1;
    }

    private int getNextPlanVersion(Long documentId) {

        List<DochubDocumentStrategyPlan> planList = planMapper.selectList(new LambdaQueryWrapper<DochubDocumentStrategyPlan>()
            .eq(DochubDocumentStrategyPlan::getDocumentId, documentId)
            .eq(DochubDocumentStrategyPlan::getStatus, BusinessStatus.YES.getCode())
            .orderByDesc(DochubDocumentStrategyPlan::getPlanVersion)
            .last("limit 1"));
        return planList.isEmpty() ? 1 : planList.get(0).getPlanVersion() + 1;
    }

    private void finishTaskSuccess(DochubDocumentTask task, Integer stage, Date startTime) {

        Date finishTime = new Date();
        task.setTaskStatus(DocumentTaskStatusEnum.SUCCESS.getCode());
        task.setCurrentStage(stage);
        task.setFinishTime(finishTime);
        task.setCostMillis(finishTime.getTime() - startTime.getTime());
        task.setErrorCode(null);
        task.setErrorMsg(null);
        taskMapper.updateById(task);
    }

    private void syncNavigationArtifacts(Long documentId,
                                         Long parseTaskId,
                                         List<DochubDocumentStructureNode> structureNodes) {
        log.info("开始同步导航产物: documentId={}, parseTaskId={}, structureNodeCount={}",
            documentId,
            parseTaskId,
            structureNodes == null ? 0 : structureNodes.size());
        DocumentNavigationIndexService navigationIndexService = navigationIndexServiceProvider.getIfAvailable();
        if (navigationIndexService != null) {
            log.info("同步导航 ES 索引: documentId={}, parseTaskId={}", documentId, parseTaskId);
            navigationIndexService.reindexDocumentNodes(documentId, parseTaskId, structureNodes);
        }
        else {
            log.info("跳过导航 ES 索引同步，因为服务未启用: documentId={}, parseTaskId={}", documentId, parseTaskId);
        }
        DocumentStructureGraphProjectionService graphProjectionService = graphProjectionServiceProvider.getIfAvailable();
        if (graphProjectionService != null && graphProjectionService.enabled()) {
            log.info("同步结构图投影: documentId={}, parseTaskId={}", documentId, parseTaskId);
            graphProjectionService.projectToGraph(documentId, parseTaskId);
        }
        else {
            log.info("跳过结构图投影，因为图服务未启用: documentId={}, parseTaskId={}", documentId, parseTaskId);
        }
    }

    private void failTask(DochubDocumentTask task, Date startTime, Exception exception, Integer currentStage) {

        Date finishTime = new Date();
        task.setTaskStatus(DocumentTaskStatusEnum.FAILED.getCode());
        task.setCurrentStage(currentStage);
        task.setFinishTime(finishTime);
        task.setCostMillis(finishTime.getTime() - startTime.getTime());
        task.setErrorCode("TASK_FAILED");
        task.setErrorMsg(exception.getMessage());
        taskMapper.updateById(task);
    }

    private int estimateTokenCount(String text) {
        if (StrUtil.isBlank(text)) {
            return 0;
        }
        int chineseCount = 0;
        int englishCount = 0;

        for (char current : text.toCharArray()) {
            if (String.valueOf(current).matches("[\\u4e00-\\u9fa5]")) {
                chineseCount++;
            }
        }

        for (String word : text.split("\\s+")) {
            if (word.matches(".*[A-Za-z].*")) {
                englishCount++;
            }
        }

        return chineseCount + englishCount + Math.max(1, (text.length() - chineseCount) / 4);
    }

    private Map<String, Object> detail(Object... keyValues) {
        Map<String, Object> detailMap = new LinkedHashMap<>();

        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            detailMap.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return detailMap;
    }

    private record ParentChildEntityBundle(
        List<DochubDocumentParentBlock> parentBlocks,
        List<DochubDocumentChunk> childChunks
    ) {
    }
}
