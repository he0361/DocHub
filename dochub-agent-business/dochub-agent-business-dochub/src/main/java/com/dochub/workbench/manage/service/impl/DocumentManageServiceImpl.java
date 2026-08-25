package com.dochub.workbench.manage.service.impl;

import lombok.AllArgsConstructor;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import com.dochub.workbench.manage.data.DochubDocument;
import com.dochub.workbench.manage.data.DochubDocumentChunk;
import com.dochub.workbench.manage.data.DochubDocumentParentBlock;
import com.dochub.workbench.manage.data.DochubDocumentProfile;
import com.dochub.workbench.manage.data.DochubDocumentStrategyPlan;
import com.dochub.workbench.manage.data.DochubDocumentStrategyStep;
import com.dochub.workbench.manage.data.DochubDocumentTask;
import com.dochub.workbench.manage.data.DochubDocumentTaskLog;
import com.dochub.workbench.manage.data.DochubTopicDocumentRelation;
import com.dochub.workbench.manage.dto.DocumentChunkQueryDto;
import com.dochub.workbench.manage.dto.DocumentChunkDetailQueryDto;
import com.dochub.workbench.manage.dto.DocumentDeleteDto;
import com.dochub.workbench.manage.dto.DocumentDetailQueryDto;
import com.dochub.workbench.manage.dto.DocumentIndexBuildDto;
import com.dochub.workbench.manage.dto.DocumentPageQueryDto;
import com.dochub.workbench.manage.dto.DocumentStrategyConfirmDto;
import com.dochub.workbench.manage.dto.DocumentStrategyPlanQueryDto;
import com.dochub.workbench.manage.dto.DocumentStrategyStepItemDto;
import com.dochub.workbench.manage.dto.DocumentTaskLogQueryDto;
import com.dochub.workbench.manage.dto.DocumentUploadDto;
import com.dochub.workbench.manage.mapper.DochubDocumentMapper;
import com.dochub.workbench.manage.mapper.DochubDocumentChunkMapper;
import com.dochub.workbench.manage.mapper.DochubDocumentParentBlockMapper;
import com.dochub.workbench.manage.mapper.DochubDocumentProfileMapper;
import com.dochub.workbench.manage.mapper.DochubDocumentStrategyPlanMapper;
import com.dochub.workbench.manage.mapper.DochubDocumentStrategyStepMapper;
import com.dochub.workbench.manage.mapper.DochubDocumentTaskLogMapper;
import com.dochub.workbench.manage.mapper.DochubDocumentTaskMapper;
import com.dochub.workbench.manage.mapper.DochubTopicDocumentRelationMapper;
import com.dochub.workbench.manage.mq.DocumentKafkaProducer;
import com.dochub.workbench.manage.mq.message.DocumentIndexBuildMessage;
import com.dochub.workbench.manage.mq.message.DocumentParseRouteMessage;
import com.dochub.workbench.manage.service.DocumentManageService;
import com.dochub.workbench.manage.service.DocumentNavigationIndexService;
import com.dochub.workbench.manage.service.DocumentStorageService;
import com.dochub.workbench.manage.service.DocumentStructureGraphProjectionService;
import com.dochub.workbench.manage.service.DocumentStructureNodeService;
import com.dochub.workbench.manage.service.DocumentStrategyService;
import com.dochub.workbench.manage.service.DocumentTaskLogService;
import com.dochub.workbench.manage.service.DocumentVectorGateway;
import com.dochub.workbench.manage.service.KnowledgeRouteIndexService;
import com.dochub.workbench.manage.service.keyword.DocumentKeywordSearchGateway;
import com.dochub.workbench.manage.support.DocumentIndexBuildProgressService;
import com.dochub.workbench.manage.support.StoredObjectInfo;
import com.dochub.workbench.manage.vo.DocumentChunkItemVo;
import com.dochub.workbench.manage.vo.DocumentChunkQueryVo;
import com.dochub.workbench.manage.vo.DocumentChunkDetailVo;
import com.dochub.workbench.manage.vo.DocumentDeleteVo;
import com.dochub.workbench.manage.vo.DocumentIndexBuildProgressVo;
import com.dochub.workbench.manage.vo.DocumentIndexBuildVo;
import com.dochub.workbench.manage.vo.DocumentListItemVo;
import com.dochub.workbench.manage.vo.DocumentParentBlockItemVo;
import com.dochub.workbench.manage.vo.DocumentPageQueryVo;
import com.dochub.workbench.manage.vo.DocumentStrategyConfirmVo;
import com.dochub.workbench.manage.vo.DocumentStrategyPipelineVo;
import com.dochub.workbench.manage.vo.DocumentStrategyPlanQueryVo;
import com.dochub.workbench.manage.vo.DocumentStrategyPlanVo;
import com.dochub.workbench.manage.vo.DocumentStrategyStepVo;
import com.dochub.workbench.manage.vo.DocumentTaskLogQueryVo;
import com.dochub.workbench.manage.vo.DocumentTaskLogVo;
import com.dochub.workbench.manage.vo.DocumentUploadVo;
import org.javaup.enums.BaseCode;
import org.javaup.enums.BusinessStatus;
import org.javaup.enums.DocumentChunkSourceTypeEnum;
import org.javaup.enums.DocumentFileTypeEnum;
import org.javaup.enums.DocumentIndexStatusEnum;
import org.javaup.enums.DocumentLogLevelEnum;
import org.javaup.enums.DocumentManageCode;
import org.javaup.enums.DocumentOperatorTypeEnum;
import org.javaup.enums.DocumentParseStatusEnum;
import org.javaup.enums.DocumentPlanSourceEnum;
import org.javaup.enums.DocumentPlanStatusEnum;
import org.javaup.enums.DocumentStorageTypeEnum;
import org.javaup.enums.DocumentStrategyExecuteStatusEnum;
import org.javaup.enums.DocumentStrategyPipelineTypeEnum;
import org.javaup.enums.DocumentStrategyRoleEnum;
import org.javaup.enums.DocumentStrategySourceTypeEnum;
import org.javaup.enums.DocumentStrategyStatusEnum;
import org.javaup.enums.DocumentStrategyTypeEnum;
import org.javaup.enums.DocumentTaskEventTypeEnum;
import org.javaup.enums.DocumentTaskStageEnum;
import org.javaup.enums.DocumentTaskStatusEnum;
import org.javaup.enums.DocumentTaskTypeEnum;
import org.javaup.enums.DocumentTriggerSourceEnum;
import org.javaup.enums.DocumentVectorStatusEnum;
import org.javaup.exception.DochubFrameException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 服务实现层
 * @author: zhangjihe
 **/

@Slf4j
@AllArgsConstructor
@Service
public class DocumentManageServiceImpl implements DocumentManageService {

    private final DochubDocumentMapper documentMapper;

    private final DochubDocumentStrategyPlanMapper planMapper;

    private final DochubDocumentStrategyStepMapper stepMapper;

    private final DochubDocumentTaskMapper taskMapper;

    private final DochubDocumentTaskLogMapper taskLogMapper;

    private final DochubDocumentChunkMapper chunkMapper;

    private final DochubDocumentParentBlockMapper parentBlockMapper;

    private final DochubDocumentProfileMapper documentProfileMapper;

    private final DochubTopicDocumentRelationMapper topicDocumentRelationMapper;

    private final DocumentStorageService storageService;

    private final DocumentStructureNodeService structureNodeService;

    private final DocumentStrategyService strategyService;

    private final DocumentTaskLogService taskLogService;

    private final DocumentVectorGateway vectorGateway;

    private final ObjectProvider<DocumentKeywordSearchGateway> keywordSearchGatewayProvider;

    private final ObjectProvider<DocumentNavigationIndexService> navigationIndexServiceProvider;

    private final ObjectProvider<DocumentStructureGraphProjectionService> graphProjectionServiceProvider;

    private final ObjectProvider<KnowledgeRouteIndexService> knowledgeRouteIndexServiceProvider;

    private final DocumentKafkaProducer kafkaProducer;

    private final TransactionTemplate transactionTemplate;

    private final DocumentIndexBuildProgressService indexBuildProgressService;
    
    private final UidGenerator uidGenerator;

    @Override
    public DocumentUploadVo upload(MultipartFile file, DocumentUploadDto dto) {
        //第一层防线：上传文件对象为空，或者mutipart 中虽然有字段但没有实际内容，都视为非法上传请求。
        if(file == null || file.isEmpty()){
            throw  new DochubFrameException(DocumentManageCode.EMPTY_FILE_CONTENT.getCode(),
                    DocumentManageCode.EMPTY_FILE_CONTENT.getMsg());
        }
        //原始文件不仅用于展示，更用于后续识别文件类型，因此缺失时不能继续往下走
        String originalFileName=file.getOriginalFilename();
        if(StrUtil.isBlank(originalFileName)){
            throw new DochubFrameException(DocumentManageCode.UNSUPPORTED_FILE_TYPE.getCode(),
                    "上传文件缺失原始文件名，无法识别文件类型");
        }
        //根据文件名后缀识别文档类型；如果无法识别，就不允许进入后续存储和解析流程
        DocumentFileTypeEnum fileType = DocumentFileTypeEnum.fromFileName(originalFileName);
        if(fileType==null){
            throw new DochubFrameException(DocumentManageCode.UNSUPPORTED_FILE_TYPE.getCode(),
                    DocumentManageCode.UNSUPPORTED_FILE_TYPE.getMsg());
        }
        //先把MutipartFile 一次性读取成字节数组，后续存储、文件大小计算都复用这份内存快照
        byte[] fileBytes=getFileBytes(file);
        //文件字节 + 元信息统一交给内部入库门面：upload 与 ingestGeneratedText 共用同一套链路。
        return ingestDocumentBytes(fileBytes, originalFileName, file.getContentType(), dto);
    }

    /**
     * 统一入库门面：把已就绪的文件字节写入 MinIO + 文档主表 + 任务 + Kafka 解析链路。
     * upload()（人工上传）与 ingestGeneratedText()（文档工作台生成后一键入库）都复用这里。
     */
    private DocumentUploadVo ingestDocumentBytes(byte[] fileBytes, String originalFileName, String contentType, DocumentUploadDto dto) {
        Long documentId=uidGenerator.getUid();
        //原始文件先上传到对象存储，拿到bucket/object/url等定位信息后，再写入文档主表。
        StoredObjectInfo storedObjectInfo=storageService.uploadOriginalFile(
                documentId,originalFileName,fileBytes,contentType
        );
        DochubDocument document = new DochubDocument();
        document.setId(documentId);
        document.setDocumentName(StrUtil.isNotBlank(dto.getDocumentName()) ? dto.getDocumentName() : originalFileName);
        document.setOriginalFileName(originalFileName);
        DocumentFileTypeEnum resolvedFileType = DocumentFileTypeEnum.fromFileName(originalFileName);
        document.setFileType(resolvedFileType == null ? null : resolvedFileType.getCode());
        document.setMimeType(contentType);
        document.setFileSize((long) fileBytes.length);
        document.setStorageType(DocumentStorageTypeEnum.MINIO.getCode());
        document.setBucketName(storedObjectInfo.getBucketName());
        document.setObjectName(storedObjectInfo.getObjectName());
        document.setObjectUrl(storedObjectInfo.getObjectUrl());
        document.setParseStatus(DocumentParseStatusEnum.PARSING.getCode());
        document.setStrategyStatus(DocumentStrategyStatusEnum.WAIT_RECOMMEND.getCode());
        document.setIndexStatus(DocumentIndexStatusEnum.WAIT_BUILD.getCode());
        document.setCharCount(0);
        document.setTokenCount(0);

        document.setKnowledgeScopeCode(StrUtil.trimToNull(dto.getKnowledgeScopeCode()));
        document.setKnowledgeScopeName(StrUtil.trimToNull(dto.getKnowledgeScopeName()));
        document.setBusinessCategory(StrUtil.trimToNull(dto.getBusinessCategory()));
        document.setDocumentTags(StrUtil.trimToNull(dto.getDocumentTags()));
        document.setStatus(BusinessStatus.YES.getCode());

        Long taskId = uidGenerator.getUid();
        DochubDocumentTask task = new DochubDocumentTask();
        task.setId(taskId);
        task.setDocumentId(documentId);
        task.setTaskType(DocumentTaskTypeEnum.PARSE_ROUTE.getCode());
        task.setTaskStatus(DocumentTaskStatusEnum.NEW.getCode());
        task.setCurrentStage(DocumentTaskStageEnum.FILE_UPLOAD.getCode());
        Long operatorId = parseOptionalLong(dto.getOperatorId());
        task.setTriggerSource(resolveTriggerSource(operatorId));
        task.setRetryCount(0);
        task.setStatus(BusinessStatus.YES.getCode());

        DocumentUploadVo uploadVo = transactionTemplate.execute(status -> {
            documentMapper.insert(document);
            taskMapper.insert(task);

            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.FILE_UPLOAD.getCode(),
                DocumentTaskEventTypeEnum.COMPLETE.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                resolveOperatorType(operatorId),
                operatorId,
                "文件上传完成，已进入解析与策略推荐队列。",
                Map.of("originalFileName", originalFileName, "fileSize", fileBytes.length));

            return new DocumentUploadVo(documentId, taskId, document.getDocumentName(),
                document.getParseStatus(), document.getStrategyStatus(), document.getIndexStatus());
        });

        kafkaProducer.sendParseRoute(new DocumentParseRouteMessage(documentId, taskId));

        return uploadVo;
    }

    @Override
    public DocumentUploadVo ingestGeneratedText(String documentName, String markdownContent, DocumentUploadDto dto) {
        //把工作台生成的 Markdown 当作文本型文档（.md）走标准入库链路，形成"生成 → 入库 → 可检索"闭环。
        String safeName = StrUtil.isNotBlank(documentName) ? documentName.trim() : "文枢生成文档";
        if (!StrUtil.endWithIgnoreCase(safeName, ".md")) {
            safeName = safeName + ".md";
        }
        DocumentUploadDto effectiveDto = dto == null ? new DocumentUploadDto() : dto;
        if (StrUtil.isBlank(effectiveDto.getDocumentName())) {
            effectiveDto.setDocumentName(safeName);
        }
        byte[] fileBytes = markdownContent == null ? new byte[0] : markdownContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ingestDocumentBytes(fileBytes, safeName, "text/markdown", effectiveDto);
    }

    @Override
    public DocumentPageQueryVo queryDocumentPage(DocumentPageQueryDto dto) {

        int pageNo = dto.getPageNo() == null || dto.getPageNo() <= 0 ? 1 : dto.getPageNo();
        int pageSize = dto.getPageSize() == null || dto.getPageSize() <= 0 ? 10 : dto.getPageSize();
        String keyword = StrUtil.isNotBlank(dto.getKeyword()) ? dto.getKeyword().trim() : null;

        Page<DochubDocument> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<DochubDocument> wrapper = new LambdaQueryWrapper<DochubDocument>()
            .eq(DochubDocument::getStatus, BusinessStatus.YES.getCode())
            .orderByDesc(DochubDocument::getEditTime, DochubDocument::getId);

        if (keyword != null) {
            wrapper.and(query -> query.like(DochubDocument::getDocumentName, keyword)
                .or()
                .like(DochubDocument::getOriginalFileName, keyword));
        }

        IPage<DochubDocument> resultPage = documentMapper.selectPage(page, wrapper);
        List<DochubDocument> documentList = resultPage.getRecords();
        Map<Long, DochubDocumentTask> latestTaskMap = getLatestTaskMap(documentList);

        List<DocumentListItemVo> records = documentList.stream()
            .map(document -> toDocumentListItemVo(document, latestTaskMap.get(document.getId())))
            .toList();

        return new DocumentPageQueryVo(pageNo, pageSize, resultPage.getTotal(), records);
    }

    @Override
    public DocumentListItemVo queryDocumentDetail(DocumentDetailQueryDto dto) {
        DochubDocument document = getDocumentOrThrow(dto.getDocumentId());
        DochubDocumentTask latestTask = getLatestTask(document.getId());
        return toDocumentListItemVo(document, latestTask);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentDeleteVo deleteDocument(DocumentDeleteDto dto) {
        Long documentId = parseRequiredLong(dto.getDocumentId(), "文档id");
        DochubDocument document = getDocumentOrThrow(documentId);

        // 只阻止"正在执行"（RUNNING）的任务删除；NEW 状态（排队/卡住）允许删除，
        // 便于清理异常遗留的"待解析"文档。删除后若有 Kafka 消息在途，消费者会安全兜底。
        long runningTaskCount = taskMapper.selectCount(new LambdaQueryWrapper<DochubDocumentTask>()
            .eq(DochubDocumentTask::getDocumentId, documentId)
            .eq(DochubDocumentTask::getStatus, BusinessStatus.YES.getCode())
            .eq(DochubDocumentTask::getTaskStatus, DocumentTaskStatusEnum.RUNNING.getCode()));
        if (runningTaskCount > 0) {
            throw new DochubFrameException(DocumentManageCode.DOCUMENT_STATUS_INVALID.getCode(),
                "当前文档存在正在执行的任务，请等待任务结束后再删除。");
        }

        storageService.deleteObjects(List.of(document.getObjectName(), document.getParseTextPath()));
        vectorGateway.deleteByDocumentId(documentId);

        DocumentKeywordSearchGateway keywordSearchGateway = keywordSearchGatewayProvider.getIfAvailable();
        if (keywordSearchGateway != null) {
            log.info("删除文档关键词索引: documentId={}", documentId);
            keywordSearchGateway.deleteByDocumentId(documentId);
        }
        DocumentNavigationIndexService navigationIndexService = navigationIndexServiceProvider.getIfAvailable();
        if (navigationIndexService != null) {
            log.info("删除文档导航索引: documentId={}", documentId);
            navigationIndexService.deleteByDocumentId(documentId);
        }
        KnowledgeRouteIndexService knowledgeRouteIndexService = knowledgeRouteIndexServiceProvider.getIfAvailable();
        if (knowledgeRouteIndexService != null) {
            log.info("删除知识路由索引中的文档快照: documentId={}", documentId);
            knowledgeRouteIndexService.deleteDocumentRoute(documentId);
        }
        DocumentStructureGraphProjectionService graphProjectionService = graphProjectionServiceProvider.getIfAvailable();
        if (graphProjectionService != null && graphProjectionService.enabled()) {
            log.info("删除文档结构图投影: documentId={}", documentId);
            graphProjectionService.deleteByDocumentId(documentId);
        }

        documentProfileMapper.delete(new LambdaQueryWrapper<DochubDocumentProfile>()
            .eq(DochubDocumentProfile::getDocumentId, documentId));
        topicDocumentRelationMapper.delete(new LambdaQueryWrapper<DochubTopicDocumentRelation>()
            .eq(DochubTopicDocumentRelation::getDocumentId, documentId));
        parentBlockMapper.delete(new LambdaQueryWrapper<DochubDocumentParentBlock>()
            .eq(DochubDocumentParentBlock::getDocumentId, documentId));
        chunkMapper.delete(new LambdaQueryWrapper<DochubDocumentChunk>()
            .eq(DochubDocumentChunk::getDocumentId, documentId));
        structureNodeService.deleteByDocumentId(documentId);
        taskLogMapper.delete(new LambdaQueryWrapper<DochubDocumentTaskLog>()
            .eq(DochubDocumentTaskLog::getDocumentId, documentId));
        stepMapper.delete(new LambdaQueryWrapper<DochubDocumentStrategyStep>()
            .eq(DochubDocumentStrategyStep::getDocumentId, documentId));
        taskMapper.delete(new LambdaQueryWrapper<DochubDocumentTask>()
            .eq(DochubDocumentTask::getDocumentId, documentId));
        planMapper.delete(new LambdaQueryWrapper<DochubDocumentStrategyPlan>()
            .eq(DochubDocumentStrategyPlan::getDocumentId, documentId));
        documentMapper.deleteById(documentId);

        return new DocumentDeleteVo(documentId, document.getDocumentName());
    }

    @Override
    public DocumentStrategyPlanQueryVo queryStrategyPlan(DocumentStrategyPlanQueryDto dto) {

        DochubDocument document = getDocumentOrThrow(dto.getDocumentId());
        DocumentStrategyPlanVo planVo = null;
        boolean planReady = false;

        if (document.getCurrentPlanId() != null) {
            DochubDocumentStrategyPlan plan = planMapper.selectById(document.getCurrentPlanId());
            if (plan != null && Objects.equals(plan.getStatus(), BusinessStatus.YES.getCode())) {
                List<DochubDocumentStrategyStep> stepList = listStepByPlanId(plan.getId());
                planVo = toPlanVo(plan, stepList);
                planReady = true;
            }
        }

        return new DocumentStrategyPlanQueryVo(
            document.getId(),
            document.getDocumentName(),
            document.getParseStatus(),
            enumMsg(DocumentParseStatusEnum.getRc(document.getParseStatus())),
            document.getStrategyStatus(),
            enumMsg(DocumentStrategyStatusEnum.getRc(document.getStrategyStatus())),
            document.getIndexStatus(),
            enumMsg(DocumentIndexStatusEnum.getRc(document.getIndexStatus())),
            document.getParseErrorMsg(),
            planReady,
            planVo
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentStrategyConfirmVo confirmStrategy(DocumentStrategyConfirmDto dto) {

        DochubDocument document = getDocumentOrThrow(dto.getDocumentId());
        if (!Objects.equals(document.getParseStatus(), DocumentParseStatusEnum.PARSE_SUCCESS.getCode())) {
            throw new DochubFrameException(DocumentManageCode.DOCUMENT_STATUS_INVALID.getCode(), "当前文档还未完成解析，不能确认策略。");
        }

        if (!Objects.equals(document.getCurrentPlanId(), dto.getBasePlanId())) {
            throw new DochubFrameException(DocumentManageCode.STRATEGY_PLAN_NOT_FOUND.getCode(), "当前文档的基础方案不存在或已切换。");
        }

        DochubDocumentStrategyPlan basePlan = planMapper.selectById(dto.getBasePlanId());
        if (basePlan == null || !Objects.equals(basePlan.getStatus(), BusinessStatus.YES.getCode())) {
            throw new DochubFrameException(DocumentManageCode.STRATEGY_PLAN_NOT_FOUND.getCode(),
                DocumentManageCode.STRATEGY_PLAN_NOT_FOUND.getMsg());
        }

        List<DochubDocumentStrategyStep> baseStepList = listStepByPlanId(basePlan.getId());
        List<Integer> requestParentTypeList = dto.getParentSteps().stream()
            .sorted(Comparator.comparing(item -> item.getStepNo() == null ? Integer.MAX_VALUE : item.getStepNo()))
            .map(DocumentStrategyStepItemDto::getStrategyType)
            .filter(Objects::nonNull)
            .toList();
        List<Integer> requestChildTypeList = dto.getChildSteps().stream()
            .sorted(Comparator.comparing(item -> item.getStepNo() == null ? Integer.MAX_VALUE : item.getStepNo()))
            .map(DocumentStrategyStepItemDto::getStrategyType)
            .filter(Objects::nonNull)
            .toList();

        List<DochubDocumentStrategyStep> normalizedStepList = strategyService.normalizeSteps(
            basePlan, baseStepList, requestParentTypeList, requestChildTypeList, dto.getDocumentId());

        List<Integer> normalizedParentTypeList = extractPipelineTypes(normalizedStepList, DocumentStrategyPipelineTypeEnum.PARENT);
        List<Integer> normalizedChildTypeList = extractPipelineTypes(normalizedStepList, DocumentStrategyPipelineTypeEnum.CHILD);

        if (normalizedParentTypeList.isEmpty()) {
            throw new DochubFrameException(DocumentManageCode.STRATEGY_STEP_EMPTY.getCode(), "父块流水线不能为空。");
        }
        if (normalizedChildTypeList.isEmpty()) {
            throw new DochubFrameException(DocumentManageCode.STRATEGY_STEP_EMPTY.getCode(), "子块流水线不能为空。");
        }

        if (normalizedStepList.isEmpty()) {
            throw new DochubFrameException(DocumentManageCode.STRATEGY_STEP_EMPTY.getCode(),
                DocumentManageCode.STRATEGY_STEP_EMPTY.getMsg());
        }

        List<Integer> baseParentTypeList = extractPipelineTypes(baseStepList, DocumentStrategyPipelineTypeEnum.PARENT);
        List<Integer> baseChildTypeList = extractPipelineTypes(baseStepList, DocumentStrategyPipelineTypeEnum.CHILD);
        List<Integer> requestDistinctParentTypeList = new LinkedHashSet<>(requestParentTypeList).stream().toList();
        List<Integer> requestDistinctChildTypeList = new LinkedHashSet<>(requestChildTypeList).stream().toList();

        boolean normalized = !requestDistinctParentTypeList.equals(normalizedParentTypeList)
            || !requestDistinctChildTypeList.equals(normalizedChildTypeList);

        boolean changed = !baseParentTypeList.equals(normalizedParentTypeList)
            || !baseChildTypeList.equals(normalizedChildTypeList);

        Long targetPlanId;
        Integer targetPlanVersion;
        List<DochubDocumentStrategyStep> targetStepList;

        if (!changed) {

            basePlan.setPlanStatus(DocumentPlanStatusEnum.CONFIRMED.getCode());
            basePlan.setPlanSource(basePlan.getPlanSource() == null ? DocumentPlanSourceEnum.SYSTEM_RECOMMEND.getCode() : basePlan.getPlanSource());
            basePlan.setAdjustNote(dto.getAdjustNote());
            basePlan.setConfirmUserId(dto.getOperatorId());
            basePlan.setConfirmTime(new Date());
            planMapper.updateById(basePlan);
            targetPlanId = basePlan.getId();
            targetPlanVersion = basePlan.getPlanVersion();
            targetStepList = baseStepList;
        } else {

            basePlan.setPlanStatus(DocumentPlanStatusEnum.DISCARDED.getCode());
            planMapper.updateById(basePlan);

            Long newPlanId = uidGenerator.getUid();
            Integer newPlanVersion = getNextPlanVersion(document.getId());
            DochubDocumentStrategyPlan newPlan = new DochubDocumentStrategyPlan();
            newPlan.setId(newPlanId);
            newPlan.setDocumentId(document.getId());
            newPlan.setPlanVersion(newPlanVersion);

            newPlan.setPlanSource(DocumentPlanSourceEnum.USER_ADJUST.getCode());
            newPlan.setPlanStatus(DocumentPlanStatusEnum.CONFIRMED.getCode());
            newPlan.setStrategyCount(normalizedStepList.size());
            newPlan.setStrategySnapshot(buildStrategySnapshot(normalizedStepList));
            newPlan.setRecommendReason(basePlan.getRecommendReason());
            newPlan.setAdjustNote(dto.getAdjustNote());
            newPlan.setConfirmUserId(dto.getOperatorId());
            newPlan.setConfirmTime(new Date());
            newPlan.setStatus(BusinessStatus.YES.getCode());
            planMapper.insert(newPlan);

            for (DochubDocumentStrategyStep step : normalizedStepList) {
                step.setId(uidGenerator.getUid());
                step.setPlanId(newPlanId);
                step.setStatus(BusinessStatus.YES.getCode());
                stepMapper.insert(step);
            }

            targetPlanId = newPlanId;
            targetPlanVersion = newPlanVersion;
            targetStepList = normalizedStepList;
        }

        document.setCurrentPlanId(targetPlanId);
        document.setStrategyStatus(DocumentStrategyStatusEnum.CONFIRMED.getCode());
        documentMapper.updateById(document);

        DochubDocumentTask latestParseTask = getLatestTask(document.getId(), DocumentTaskTypeEnum.PARSE_ROUTE.getCode());
        if (latestParseTask != null) {

            latestParseTask.setCurrentStage(DocumentTaskStageEnum.STRATEGY_CONFIRM.getCode());
            taskMapper.updateById(latestParseTask);

            if (changed) {

                taskLogService.saveLog(latestParseTask.getId(), document.getId(),
                    DocumentTaskStageEnum.STRATEGY_CONFIRM.getCode(),
                    DocumentTaskEventTypeEnum.USER_ADJUST.getCode(),
                    DocumentLogLevelEnum.INFO.getCode(),
                    resolveOperatorType(parseOptionalLong(dto.getOperatorId())),
                    parseOptionalLong(dto.getOperatorId()),
                    "用户调整了系统推荐策略。",
                    detail("parentStrategyTypes", normalizedParentTypeList,
                        "childStrategyTypes", normalizedChildTypeList,
                        "adjustNote", dto.getAdjustNote()));
            }

            taskLogService.saveLog(latestParseTask.getId(), document.getId(),
                DocumentTaskStageEnum.STRATEGY_CONFIRM.getCode(),
                DocumentTaskEventTypeEnum.USER_CONFIRM.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                    resolveOperatorType(parseOptionalLong(dto.getOperatorId())),
                    parseOptionalLong(dto.getOperatorId()),
                    "用户已确认最终策略方案。",
                Map.of("planId", targetPlanId,
                    "parentStrategyTypes", normalizedParentTypeList,
                    "childStrategyTypes", normalizedChildTypeList));
        }

        return new DocumentStrategyConfirmVo(
            document.getId(),
            targetPlanId,
            targetPlanVersion,
            document.getStrategyStatus(),
            enumMsg(DocumentStrategyStatusEnum.getRc(document.getStrategyStatus())),
            normalized,
            toPipelineVo(DocumentStrategyPipelineTypeEnum.PARENT, targetStepList),
            toPipelineVo(DocumentStrategyPipelineTypeEnum.CHILD, targetStepList)
        );
    }

    @Override
    public DocumentIndexBuildVo buildIndex(DocumentIndexBuildDto dto) {

        DochubDocument document = getDocumentOrThrow(dto.getDocumentId());
        if (!Objects.equals(document.getParseStatus(), DocumentParseStatusEnum.PARSE_SUCCESS.getCode())
            || !Objects.equals(document.getStrategyStatus(), DocumentStrategyStatusEnum.CONFIRMED.getCode())) {
            throw new DochubFrameException(DocumentManageCode.DOCUMENT_STATUS_INVALID.getCode(), "当前文档尚未完成“解析成功 + 策略确认”，不能构建索引。");
        }

        if (!Objects.equals(document.getCurrentPlanId(), dto.getPlanId())) {
            throw new DochubFrameException(DocumentManageCode.STRATEGY_PLAN_NOT_FOUND.getCode(), "当前文档的生效方案与请求方案不一致。");
        }

        long runningTaskCount = taskMapper.selectCount(new LambdaQueryWrapper<DochubDocumentTask>()
            .eq(DochubDocumentTask::getDocumentId, dto.getDocumentId())
            .eq(DochubDocumentTask::getTaskType, DocumentTaskTypeEnum.BUILD_INDEX.getCode())
            .in(DochubDocumentTask::getTaskStatus, DocumentTaskStatusEnum.NEW.getCode(), DocumentTaskStatusEnum.RUNNING.getCode())
            .eq(DochubDocumentTask::getStatus, BusinessStatus.YES.getCode()));
        if (runningTaskCount > 0) {
            throw new DochubFrameException(DocumentManageCode.INDEX_TASK_RUNNING.getCode(),
                DocumentManageCode.INDEX_TASK_RUNNING.getMsg());
        }

        DochubDocumentStrategyPlan plan = planMapper.selectById(dto.getPlanId());
        if (plan == null || !Objects.equals(plan.getStatus(), BusinessStatus.YES.getCode())) {
            throw new DochubFrameException(DocumentManageCode.STRATEGY_PLAN_NOT_FOUND.getCode(),
                DocumentManageCode.STRATEGY_PLAN_NOT_FOUND.getMsg());
        }

        Long taskId = uidGenerator.getUid();
        DochubDocumentTask task = new DochubDocumentTask();
        task.setId(taskId);
        task.setDocumentId(document.getId());
        task.setPlanId(dto.getPlanId());
        task.setTaskType(DocumentTaskTypeEnum.BUILD_INDEX.getCode());
        task.setTaskStatus(DocumentTaskStatusEnum.NEW.getCode());
        task.setCurrentStage(DocumentTaskStageEnum.CHUNK_EXECUTE.getCode());
        Long operatorId = parseOptionalLong(dto.getOperatorId());
        task.setTriggerSource(resolveTriggerSource(operatorId));
        task.setStrategySnapshot(plan.getStrategySnapshot());
        task.setRetryCount(0);
        task.setStatus(BusinessStatus.YES.getCode());

        // 事务内写库：任务 + 文档状态 + 日志
        transactionTemplate.execute(status -> {
            taskMapper.insert(task);
            document.setIndexStatus(DocumentIndexStatusEnum.BUILDING.getCode());
            documentMapper.updateById(document);
            taskLogService.saveLog(taskId, document.getId(),
                DocumentTaskStageEnum.CHUNK_EXECUTE.getCode(),
                DocumentTaskEventTypeEnum.START.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                resolveOperatorType(operatorId),
                operatorId,
                "索引构建任务已创建，等待异步执行。",
                Map.of("planId", dto.getPlanId(), "strategySnapshot", plan.getStrategySnapshot()));
            return null;
        });

        // 事务提交后再投递消息，避免消费者在提交前查不到刚创建的任务
        kafkaProducer.sendIndexBuild(new DocumentIndexBuildMessage(document.getId(), taskId, dto.getPlanId()));

        return new DocumentIndexBuildVo(
            document.getId(),
            taskId,
            task.getTaskType(),
            enumMsg(DocumentTaskTypeEnum.getRc(task.getTaskType())),
            task.getTaskStatus(),
            enumMsg(DocumentTaskStatusEnum.getRc(task.getTaskStatus())),
            document.getIndexStatus(),
            enumMsg(DocumentIndexStatusEnum.getRc(document.getIndexStatus()))
        );
    }

    @Override
    public DocumentTaskLogQueryVo queryTaskLogs(DocumentTaskLogQueryDto dto) {

        DochubDocumentTask task = taskMapper.selectById(dto.getTaskId());
        if (task == null || !Objects.equals(task.getStatus(), BusinessStatus.YES.getCode())) {
            throw new DochubFrameException(DocumentManageCode.DOCUMENT_NOT_FOUND.getCode(), "任务不存在。");
        }

        int pageNo = dto.getPageNo() == null || dto.getPageNo() <= 0 ? 1 : dto.getPageNo();
        int pageSize = dto.getPageSize() == null || dto.getPageSize() <= 0 ? 20 : dto.getPageSize();
        Page<DochubDocumentTaskLog> page = new Page<>(pageNo, pageSize);

        IPage<DochubDocumentTaskLog> resultPage = taskLogMapper.selectPage(page,
            new LambdaQueryWrapper<DochubDocumentTaskLog>()
                .eq(DochubDocumentTaskLog::getTaskId, dto.getTaskId())
                .eq(DochubDocumentTaskLog::getStatus, BusinessStatus.YES.getCode())
                .orderByAsc(DochubDocumentTaskLog::getCreateTime, DochubDocumentTaskLog::getId));

        List<DocumentTaskLogVo> logVoList = resultPage.getRecords().stream()
            .map(this::toTaskLogVo)
            .toList();

        return new DocumentTaskLogQueryVo(
            task.getId(),
            task.getDocumentId(),
            task.getTaskType(),
            enumMsg(DocumentTaskTypeEnum.getRc(task.getTaskType())),
            task.getTaskStatus(),
            enumMsg(DocumentTaskStatusEnum.getRc(task.getTaskStatus())),
            task.getCurrentStage(),
            enumMsg(DocumentTaskStageEnum.getRc(task.getCurrentStage())),
            task.getStartTime(),
            task.getFinishTime(),
            task.getCostMillis(),
            task.getErrorCode(),
            task.getErrorMsg(),
            resultPage.getTotal(),
            logVoList
        );
    }

    @Override
    public DocumentIndexBuildProgressVo queryIndexBuildProgress(Long documentId) {
        if (documentId == null) {
            return null;
        }
        return indexBuildProgressService.get(documentId);
    }

    @Override
    public DocumentChunkQueryVo queryDocumentChunks(DocumentChunkQueryDto dto) {
        DochubDocument document = getDocumentOrThrow(dto.getDocumentId());
        int pageNo = dto.getPageNo() == null || dto.getPageNo() <= 0 ? 1 : dto.getPageNo();
        int pageSize = dto.getPageSize() == null || dto.getPageSize() <= 0 ? 20 : dto.getPageSize();

        Long effectiveTaskId = resolveChunkTaskId(document, dto.getTaskId());
        if (effectiveTaskId == null) {
            return new DocumentChunkQueryVo(document.getId(), null, document.getCurrentPlanId(), pageNo, pageSize, 0L, List.of());
        }

        DochubDocumentTask task = taskMapper.selectById(effectiveTaskId);
        if (task == null
            || !Objects.equals(task.getStatus(), BusinessStatus.YES.getCode())
            || !Objects.equals(task.getDocumentId(), document.getId())) {
            throw new DochubFrameException(DocumentManageCode.DOCUMENT_NOT_FOUND.getCode(), "切块任务不存在。");
        }

        Page<DochubDocumentChunk> page = new Page<>(pageNo, pageSize);
        IPage<DochubDocumentChunk> resultPage = chunkMapper.selectPage(page,
            new LambdaQueryWrapper<DochubDocumentChunk>()
                .eq(DochubDocumentChunk::getDocumentId, document.getId())
                .eq(DochubDocumentChunk::getTaskId, effectiveTaskId)
                .eq(DochubDocumentChunk::getStatus, BusinessStatus.YES.getCode())
                .orderByAsc(DochubDocumentChunk::getChunkNo, DochubDocumentChunk::getId));

        Map<Long, DochubDocumentParentBlock> parentBlockMap = listParentBlockMap(
            resultPage.getRecords().stream()
                .map(DochubDocumentChunk::getParentBlockId)
                .filter(Objects::nonNull)
                .toList()
        );

        List<DocumentChunkItemVo> records = resultPage.getRecords().stream()
            .map(chunk -> toDocumentChunkItemVo(chunk, parentBlockMap.get(chunk.getParentBlockId())))
            .toList();

        return new DocumentChunkQueryVo(
            document.getId(),
            effectiveTaskId,
            task.getPlanId(),
            pageNo,
            pageSize,
            resultPage.getTotal(),
            records
        );
    }

    @Override
    public DocumentChunkDetailVo queryDocumentChunkDetail(DocumentChunkDetailQueryDto dto) {
        DochubDocument document = getDocumentOrThrow(dto.getDocumentId());
        Long effectiveTaskId = resolveChunkTaskId(document, dto.getTaskId());
        if (effectiveTaskId == null) {
            throw new DochubFrameException(DocumentManageCode.DOCUMENT_NOT_FOUND.getCode(), "当前文档还没有可查看的 chunk 详情。");
        }

        DochubDocumentTask task = taskMapper.selectById(effectiveTaskId);
        if (task == null
            || !Objects.equals(task.getStatus(), BusinessStatus.YES.getCode())
            || !Objects.equals(task.getDocumentId(), document.getId())) {
            throw new DochubFrameException(DocumentManageCode.DOCUMENT_NOT_FOUND.getCode(), "切块任务不存在。");
        }

        DochubDocumentChunk chunk = chunkMapper.selectOne(new LambdaQueryWrapper<DochubDocumentChunk>()
            .eq(DochubDocumentChunk::getId, dto.getChunkId())
            .eq(DochubDocumentChunk::getDocumentId, document.getId())
            .eq(DochubDocumentChunk::getTaskId, effectiveTaskId)
            .eq(DochubDocumentChunk::getStatus, BusinessStatus.YES.getCode())
            .last("limit 1"));
        if (chunk == null) {
            throw new DochubFrameException(DocumentManageCode.DOCUMENT_NOT_FOUND.getCode(), "chunk 详情不存在。");
        }

        DochubDocumentParentBlock parentBlock = chunk.getParentBlockId() == null
            ? null
            : parentBlockMapper.selectOne(new LambdaQueryWrapper<DochubDocumentParentBlock>()
                .eq(DochubDocumentParentBlock::getId, chunk.getParentBlockId())
                .eq(DochubDocumentParentBlock::getDocumentId, document.getId())
                .eq(DochubDocumentParentBlock::getTaskId, effectiveTaskId)
                .eq(DochubDocumentParentBlock::getStatus, BusinessStatus.YES.getCode())
                .last("limit 1"));

        List<DochubDocumentChunk> siblingChunkList = chunk.getParentBlockId() == null
            ? List.of(chunk)
            : chunkMapper.selectList(new LambdaQueryWrapper<DochubDocumentChunk>()
                .eq(DochubDocumentChunk::getDocumentId, document.getId())
                .eq(DochubDocumentChunk::getTaskId, effectiveTaskId)
                .eq(DochubDocumentChunk::getParentBlockId, chunk.getParentBlockId())
                .eq(DochubDocumentChunk::getStatus, BusinessStatus.YES.getCode())
                .orderByAsc(DochubDocumentChunk::getChunkNo, DochubDocumentChunk::getId));

        return new DocumentChunkDetailVo(
            document.getId(),
            effectiveTaskId,
            task.getPlanId(),
            toDocumentChunkItemVo(chunk, parentBlock),
            toDocumentParentBlockItemVo(parentBlock),
            siblingChunkList.stream()
                .map(item -> toDocumentChunkItemVo(item, parentBlock))
                .toList()
        );
    }

    private DochubDocument getDocumentOrThrow(Long documentId) {

        DochubDocument document = documentMapper.selectById(documentId);
        if (document == null || !Objects.equals(document.getStatus(), BusinessStatus.YES.getCode())) {
            throw new DochubFrameException(DocumentManageCode.DOCUMENT_NOT_FOUND.getCode(),
                DocumentManageCode.DOCUMENT_NOT_FOUND.getMsg());
        }
        return document;
    }

    private List<DochubDocumentStrategyStep> listStepByPlanId(Long planId) {
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

    private Integer getNextPlanVersion(Long documentId) {

        DochubDocumentStrategyPlan latestPlan = planMapper.selectOne(new LambdaQueryWrapper<DochubDocumentStrategyPlan>()
            .eq(DochubDocumentStrategyPlan::getDocumentId, documentId)
            .eq(DochubDocumentStrategyPlan::getStatus, BusinessStatus.YES.getCode())
            .orderByDesc(DochubDocumentStrategyPlan::getPlanVersion)
            .last("limit 1"));
        return latestPlan == null ? 1 : latestPlan.getPlanVersion() + 1;
    }

    private DochubDocumentTask getLatestTask(Long documentId, Integer taskType) {

        return taskMapper.selectOne(new LambdaQueryWrapper<DochubDocumentTask>()
            .eq(DochubDocumentTask::getDocumentId, documentId)
            .eq(DochubDocumentTask::getTaskType, taskType)
            .eq(DochubDocumentTask::getStatus, BusinessStatus.YES.getCode())
            .orderByDesc(DochubDocumentTask::getId)
            .last("limit 1"));
    }

    private DochubDocumentTask getLatestTask(Long documentId) {
        return taskMapper.selectOne(new LambdaQueryWrapper<DochubDocumentTask>()
            .eq(DochubDocumentTask::getDocumentId, documentId)
            .eq(DochubDocumentTask::getStatus, BusinessStatus.YES.getCode())
            .orderByDesc(DochubDocumentTask::getId)
            .last("limit 1"));
    }

    private Map<Long, DochubDocumentTask> getLatestTaskMap(List<DochubDocument> documentList) {
        if (documentList == null || documentList.isEmpty()) {
            return Map.of();
        }

        Set<Long> documentIdSet = documentList.stream()
            .map(DochubDocument::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (documentIdSet.isEmpty()) {
            return Map.of();
        }

        List<DochubDocumentTask> taskList = taskMapper.selectList(new LambdaQueryWrapper<DochubDocumentTask>()
            .in(DochubDocumentTask::getDocumentId, documentIdSet)
            .eq(DochubDocumentTask::getStatus, BusinessStatus.YES.getCode())
            .orderByDesc(DochubDocumentTask::getId));

        Map<Long, DochubDocumentTask> latestTaskMap = new LinkedHashMap<>();
        for (DochubDocumentTask task : taskList) {
            latestTaskMap.putIfAbsent(task.getDocumentId(), task);
        }
        return latestTaskMap;
    }

    private Long resolveChunkTaskId(DochubDocument document, Long requestedTaskId) {
        if (requestedTaskId != null) {
            return requestedTaskId;
        }
        if (document.getLastIndexTaskId() != null) {
            return document.getLastIndexTaskId();
        }
        DochubDocumentTask latestBuildTask = getLatestTask(document.getId(), DocumentTaskTypeEnum.BUILD_INDEX.getCode());
        return latestBuildTask == null ? null : latestBuildTask.getId();
    }

    private DocumentListItemVo toDocumentListItemVo(DochubDocument document, DochubDocumentTask latestTask) {
        return new DocumentListItemVo(
            document.getId(),
            document.getDocumentName(),
            document.getOriginalFileName(),
            document.getFileType(),
            enumMsg(DocumentFileTypeEnum.getRc(document.getFileType())),
            document.getFileSize(),
            document.getCharCount(),
            document.getTokenCount(),
            document.getParseStatus(),
            enumMsg(DocumentParseStatusEnum.getRc(document.getParseStatus())),
            document.getStrategyStatus(),
            enumMsg(DocumentStrategyStatusEnum.getRc(document.getStrategyStatus())),
            document.getIndexStatus(),
            enumMsg(DocumentIndexStatusEnum.getRc(document.getIndexStatus())),
            document.getParseErrorMsg(),
            document.getKnowledgeScopeCode(),
            document.getKnowledgeScopeName(),
            document.getBusinessCategory(),
            document.getDocumentTags(),
            document.getCurrentPlanId(),
            document.getLastIndexTaskId(),
            latestTask == null ? null : latestTask.getId(),
            latestTask == null ? null : latestTask.getTaskType(),
            latestTask == null ? "" : enumMsg(DocumentTaskTypeEnum.getRc(latestTask.getTaskType())),
            latestTask == null ? null : latestTask.getTaskStatus(),
            latestTask == null ? "" : enumMsg(DocumentTaskStatusEnum.getRc(latestTask.getTaskStatus())),
            latestTask == null ? null : latestTask.getCurrentStage(),
            latestTask == null ? "" : enumMsg(DocumentTaskStageEnum.getRc(latestTask.getCurrentStage())),
            document.getCreateTime(),
            document.getEditTime()
        );
    }

    private DocumentChunkItemVo toDocumentChunkItemVo(DochubDocumentChunk chunk,
                                                     DochubDocumentParentBlock parentBlock) {
        return new DocumentChunkItemVo(
            chunk.getId(),
            chunk.getParentBlockId(),
            parentBlock == null ? null : parentBlock.getParentNo(),
            parentBlock == null ? null : parentBlock.getChildCount(),
            parentBlock == null ? null : parentBlock.getStartChunkNo(),
            parentBlock == null ? null : parentBlock.getEndChunkNo(),
            chunk.getChunkNo(),
            chunk.getSectionPath(),
            chunk.getSourceType(),
            enumMsg(DocumentChunkSourceTypeEnum.getRc(chunk.getSourceType())),
            chunk.getCharCount(),
            chunk.getTokenCount(),
            chunk.getVectorStatus(),
            enumMsg(DocumentVectorStatusEnum.getRc(chunk.getVectorStatus())),
            chunk.getChunkText()
        );
    }

    private DocumentParentBlockItemVo toDocumentParentBlockItemVo(DochubDocumentParentBlock parentBlock) {
        if (parentBlock == null) {
            return null;
        }
        return new DocumentParentBlockItemVo(
            parentBlock.getId(),
            parentBlock.getParentNo(),
            parentBlock.getSectionPath(),
            parentBlock.getSourceType(),
            enumMsg(DocumentChunkSourceTypeEnum.getRc(parentBlock.getSourceType())),
            parentBlock.getCharCount(),
            parentBlock.getTokenCount(),
            parentBlock.getChildCount(),
            parentBlock.getStartChunkNo(),
            parentBlock.getEndChunkNo(),
            parentBlock.getParentText()
        );
    }

    private Map<Long, DochubDocumentParentBlock> listParentBlockMap(List<Long> parentBlockIds) {
        if (parentBlockIds == null || parentBlockIds.isEmpty()) {
            return Map.of();
        }
        return parentBlockMapper.selectList(new LambdaQueryWrapper<DochubDocumentParentBlock>()
                .in(DochubDocumentParentBlock::getId, parentBlockIds)
                .eq(DochubDocumentParentBlock::getStatus, BusinessStatus.YES.getCode()))
            .stream()
            .collect(Collectors.toMap(
                DochubDocumentParentBlock::getId,
                item -> item,
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    private DocumentStrategyPlanVo toPlanVo(DochubDocumentStrategyPlan plan, List<DochubDocumentStrategyStep> stepList) {
        return new DocumentStrategyPlanVo(
            plan.getId(),
            plan.getPlanVersion(),
            plan.getPlanSource(),
            enumMsg(DocumentPlanSourceEnum.getRc(plan.getPlanSource())),
            plan.getPlanStatus(),
            enumMsg(DocumentPlanStatusEnum.getRc(plan.getPlanStatus())),
            plan.getStrategySnapshot(),
            plan.getRecommendReason(),
            toPipelineVo(DocumentStrategyPipelineTypeEnum.PARENT, stepList),
            toPipelineVo(DocumentStrategyPipelineTypeEnum.CHILD, stepList)
        );
    }

    private List<DocumentStrategyStepVo> toStepVoList(List<DochubDocumentStrategyStep> stepList) {

        return stepList.stream()
            .sorted(Comparator
                .comparingInt((DochubDocumentStrategyStep step) -> pipelineOrder(step.getPipelineType()))
                .thenComparing(DochubDocumentStrategyStep::getStepNo)
                .thenComparing(DochubDocumentStrategyStep::getId))
            .map(step -> new DocumentStrategyStepVo(
                step.getStepNo(),
                step.getPipelineType(),
                enumMsg(DocumentStrategyPipelineTypeEnum.getRc(step.getPipelineType())),
                step.getStrategyType(),
                enumMsg(DocumentStrategyTypeEnum.getRc(step.getStrategyType())),
                step.getStrategyRole(),
                enumMsg(DocumentStrategyRoleEnum.getRc(step.getStrategyRole())),
                step.getSourceType(),
                enumMsg(DocumentStrategySourceTypeEnum.getRc(step.getSourceType())),
                step.getExecuteStatus(),
                enumMsg(DocumentStrategyExecuteStatusEnum.getRc(step.getExecuteStatus())),
                step.getRecommendReason()
            ))
            .toList();
    }

    private DocumentStrategyPipelineVo toPipelineVo(DocumentStrategyPipelineTypeEnum pipelineType,
                                                    List<DochubDocumentStrategyStep> stepList) {
        List<DochubDocumentStrategyStep> pipelineSteps = stepList.stream()
            .filter(step -> pipelineType.getCode().equalsIgnoreCase(
                StrUtil.blankToDefault(step.getPipelineType(), DocumentStrategyPipelineTypeEnum.CHILD.getCode())
            ))
            .sorted(Comparator.comparingInt(DochubDocumentStrategyStep::getStepNo))
            .toList();
        return new DocumentStrategyPipelineVo(
            pipelineType.getCode(),
            pipelineType.getMsg(),
            pipelineSteps.stream().map(step -> String.valueOf(step.getStrategyType())).collect(Collectors.joining(",")),
            toStepVoList(pipelineSteps)
        );
    }

    private List<Integer> extractPipelineTypes(List<DochubDocumentStrategyStep> stepList,
                                               DocumentStrategyPipelineTypeEnum pipelineType) {
        return stepList.stream()
            .filter(step -> pipelineType.getCode().equalsIgnoreCase(
                StrUtil.blankToDefault(step.getPipelineType(), DocumentStrategyPipelineTypeEnum.CHILD.getCode())
            ))
            .sorted(Comparator.comparingInt(DochubDocumentStrategyStep::getStepNo))
            .map(DochubDocumentStrategyStep::getStrategyType)
            .toList();
    }

    private String buildStrategySnapshot(List<DochubDocumentStrategyStep> stepList) {
        return "PARENT:" + toPipelineVo(DocumentStrategyPipelineTypeEnum.PARENT, stepList).getStrategySnapshot()
            + ";CHILD:" + toPipelineVo(DocumentStrategyPipelineTypeEnum.CHILD, stepList).getStrategySnapshot();
    }

    private int pipelineOrder(String pipelineType) {
        return DocumentStrategyPipelineTypeEnum.PARENT.getCode().equalsIgnoreCase(
            StrUtil.blankToDefault(pipelineType, "")
        ) ? 0 : 1;
    }

    private DocumentTaskLogVo toTaskLogVo(DochubDocumentTaskLog logRecord) {
        return new DocumentTaskLogVo(
            logRecord.getId(),
            logRecord.getStageType(),
            enumMsg(DocumentTaskStageEnum.getRc(logRecord.getStageType())),
            logRecord.getEventType(),
            enumMsg(DocumentTaskEventTypeEnum.getRc(logRecord.getEventType())),
            logRecord.getLogLevel(),
            enumMsg(DocumentLogLevelEnum.getRc(logRecord.getLogLevel())),
            logRecord.getContent(),
            logRecord.getDetailJson(),
            logRecord.getCreateTime()
        );
    }

    private Integer resolveOperatorType(Long operatorId) {

        return operatorId == null ? DocumentOperatorTypeEnum.SYSTEM.getCode() : DocumentOperatorTypeEnum.USER.getCode();
    }

    private Integer resolveTriggerSource(Long operatorId) {

        return operatorId == null ? DocumentTriggerSourceEnum.SYSTEM.getCode() : DocumentTriggerSourceEnum.USER.getCode();
    }

    private Long parseOptionalLong(String rawValue) {
        if (StrUtil.isBlank(rawValue)) {
            return null;
        }
        try {
            Long value = Long.valueOf(rawValue.trim());
            return value > 0 ? value : null;
        }
        catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long parseOptionalLong(Long rawValue) {
        return rawValue == null || rawValue <= 0 ? null : rawValue;
    }

    private Long parseRequiredLong(String rawValue, String fieldName) {
        if (StrUtil.isBlank(rawValue)) {
            throw new DochubFrameException(BaseCode.PARAMETER_ERROR.getCode(), fieldName + "不能为空。");
        }

        try {

            Long value = Long.valueOf(rawValue.trim());
            if (value <= 0) {
                throw new NumberFormatException("id must be positive");
            }
            return value;
        }
        catch (NumberFormatException exception) {
            throw new DochubFrameException(BaseCode.PARAMETER_ERROR.getCode(), fieldName + "格式不正确。");
        }
    }

    private String enumMsg(Object enumObject) {
        if (enumObject == null) {
            return "";
        }
        if (enumObject instanceof DocumentParseStatusEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentFileTypeEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentStrategyStatusEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentIndexStatusEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentPlanSourceEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentPlanStatusEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentStrategyTypeEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentStrategyRoleEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentStrategySourceTypeEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentStrategyExecuteStatusEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentTaskTypeEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentTaskStatusEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentTaskStageEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentTaskEventTypeEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentLogLevelEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentChunkSourceTypeEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentVectorStatusEnum value) {
            return value.getMsg();
        }
        return "";
    }

    private byte[] getFileBytes(MultipartFile file) {
        try {
            return file.getBytes();
        }catch (IOException exception) {
            throw new DochubFrameException(DocumentManageCode.DOCUMENT_STORAGE_FAILED.getCode(),
                    "读取上传文件内容失败："+exception.getMessage(), exception);
        }
    }


    private Map<String, Object> detail(Object... keyValues) {
        Map<String, Object> detailMap = new LinkedHashMap<>();

        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            detailMap.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return detailMap;
    }
}
