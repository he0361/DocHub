package com.dochub.workbench.docgen.service;

import cn.hutool.core.util.StrUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dochub.workbench.chatagent.service.ObservedChatModelService;
import com.dochub.workbench.docgen.config.DocGenProperties;
import com.dochub.workbench.docgen.constant.DocGenCode;
import com.dochub.workbench.docgen.data.DocGenerationRecordEntity;
import com.dochub.workbench.docgen.dto.DocGenReferenceGenerateDto;
import com.dochub.workbench.docgen.mapper.DocGenerationRecordMapper;
import com.dochub.workbench.docgen.vo.DocGenGenerateVo;
import com.dochub.workbench.manage.data.DochubDocument;
import com.dochub.workbench.manage.mapper.DochubDocumentMapper;
import com.dochub.workbench.manage.service.DocumentParserService;
import com.dochub.workbench.manage.service.DocumentStorageService;
import com.dochub.workbench.manage.support.DocumentAnalysisResult;
import com.dochub.workbench.prompt.PromptTemplateNames;
import com.dochub.workbench.prompt.PromptTemplateService;
import com.dochub.workbench.skill.model.SkillMatchResult;
import com.dochub.workbench.skill.router.SkillSceneRouter;
import com.dochub.workbench.skill.support.SkillQuestionComposer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.javaup.enums.BusinessStatus;
import org.javaup.enums.DocumentFileTypeEnum;
import org.javaup.exception.DochubFrameException;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 文枢 DocHub 参考文档仿写服务。
 *
 * <p>用户提供一个参考文档（上传文件或知识库已有文档），给出需求，
 * 大模型照着参考文档的章节结构、写作风格与格式生成一份新文档。
 * 生成流程复用技能场景路由：需求命中技能时，把技能说明一并注入提示词。</p>
 */
@Slf4j
@Service
public class ReferenceDocumentGenerationService {

    public static final int STATUS_SUCCESS = 2;
    public static final int STATUS_FAILED = 3;

    private final DocGenProperties properties;
    private final ExecutorService executorService;
    private final ObservedChatModelService observedChatModelService;
    private final PromptTemplateService promptTemplateService;
    private final DocumentParserService documentParserService;
    private final DocumentStorageService storageService;
    private final DochubDocumentMapper documentMapper;
    private final DocGenerationRecordMapper recordMapper;
    private final SkillSceneRouter skillSceneRouter;
    private final SkillQuestionComposer skillQuestionComposer;
    private final UidGenerator uidGenerator;
    private final ObjectMapper objectMapper;

    public ReferenceDocumentGenerationService(DocGenProperties properties,
                                              @Qualifier("docGenExecutorService") ExecutorService executorService,
                                              ObservedChatModelService observedChatModelService,
                                              PromptTemplateService promptTemplateService,
                                              DocumentParserService documentParserService,
                                              DocumentStorageService storageService,
                                              DochubDocumentMapper documentMapper,
                                              DocGenerationRecordMapper recordMapper,
                                              SkillSceneRouter skillSceneRouter,
                                              SkillQuestionComposer skillQuestionComposer,
                                              UidGenerator uidGenerator,
                                              ObjectMapper objectMapper) {
        this.properties = properties;
        this.executorService = executorService;
        this.observedChatModelService = observedChatModelService;
        this.promptTemplateService = promptTemplateService;
        this.documentParserService = documentParserService;
        this.storageService = storageService;
        this.documentMapper = documentMapper;
        this.recordMapper = recordMapper;
        this.skillSceneRouter = skillSceneRouter;
        this.skillQuestionComposer = skillQuestionComposer;
        this.uidGenerator = uidGenerator;
        this.objectMapper = objectMapper;
    }

    /**
     * 参考文档仿写（阻塞版）：参考文档 + 需求 → 仿照格式与写法生成新文档。
     */
    public DocGenGenerateVo generateFromReference(MultipartFile file, DocGenReferenceGenerateDto dto) {
        ReferencePreparation prep = prepareReference(file, dto);
        long startMillis = System.currentTimeMillis();
        String body = generateBody(prep.systemPrompt(), prep.userPrompt());
        long costMillis = System.currentTimeMillis() - startMillis;
        return finalizeRecord(prep, body, costMillis);
    }

    /**
     * 参考文档仿写（SSE 流式版）：正文逐字流式输出，避免"卡在生成中"。
     * <p>事件类型：{@code status}（阶段进度）、{@code text}（正文片段）、{@code done}（最终元数据）、{@code error}（失败）。
     * 解析/准备阶段在后台线程执行，先立刻推送 {@code status} 事件给前端，避免看起来"卡死"。</p>
     */
    public Flux<String> generateFromReferenceStream(MultipartFile file, DocGenReferenceGenerateDto dto) {
        return Flux.concat(
            Flux.just(status("正在解析参考文档…")),
            Mono.fromCallable(() -> prepareReference(file, dto))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(this::generateBodyStream)
        ).onErrorResume(exception -> {
            log.warn("参考文档仿写流式失败: {}", exception.getMessage());
            return Flux.just(error("文档仿写失败：" + exception.getMessage()));
        });
    }

    /**
     * 正文流式生成：用非推理模型（默认 qwen-plus）让正文第一时间流式输出；流完落生成历史并回传元数据。
     * <p>仍叠加一个"心跳"兜底：若模型偶发停顿，静默期每 6 秒推送一次 {@code status} 事件，
     * 让前端进度条保持运动，避免看起来"卡死"。</p>
     */
    private Flux<String> generateBodyStream(ReferencePreparation prep) {
        long startMillis = System.currentTimeMillis();
        StringBuilder body = new StringBuilder();
        AtomicBoolean bodyDone = new AtomicBoolean(false);
        Flux<String> bodyEvents = observedChatModelService.streamText("doc-reference",
                prep.systemPrompt(), prep.userPrompt(), docgenOptions(), null)
            .doOnNext(body::append)
            .map(this::text)
            .doOnComplete(() -> bodyDone.set(true))
            .doOnError(error -> bodyDone.set(true));
        Flux<String> heartbeat = Flux.interval(Duration.ofSeconds(6))
            .takeWhile(tick -> !bodyDone.get())
            .map(tick -> status("正在生成正文…"));
        return Flux.concat(
            Flux.just(status("参考文档解析完成，开始生成正文…")),
            Flux.merge(bodyEvents, heartbeat),
            Flux.defer(() -> {
                String generated = body.toString();
                if (StrUtil.isBlank(generated)) {
                    return Flux.just(error("模型生成正文为空或超时"));
                }
                long costMillis = System.currentTimeMillis() - startMillis;
                DocGenerationRecordEntity record = buildRecord(prep.source(), prep.requirement(),
                    generated, costMillis, prep.skillMatch());
                recordMapper.insert(record);
                DocGenGenerateVo vo = new DocGenGenerateVo(record.getRecordCode(),
                    prep.source().name(), record.getFileName(), prep.outline(), generated, STATUS_SUCCESS);
                return Flux.just(done(vo));
            })
        );
    }

    private ReferencePreparation prepareReference(MultipartFile file, DocGenReferenceGenerateDto dto) {
        if (dto == null) {
            dto = new DocGenReferenceGenerateDto();
        }
        ReferenceSource source = resolveReference(file, dto.getReferenceDocumentId());
        if (source == null || StrUtil.isBlank(source.text())) {
            throw new DochubFrameException(DocGenCode.GENERATION_FAILED.getCode(), "请提供参考文档（上传文件或选择知识库文档）");
        }
        String requirement = StrUtil.blankToDefault(dto.getRequirement(), "");
        List<String> outline = extractHeadings(source.text());
        SkillMatchResult skillMatch = skillSceneRouter.route(requirement);
        String systemPrompt = promptTemplateService.render(PromptTemplateNames.DOC_REFERENCE_SYSTEM, Map.of());
        String userPrompt = promptTemplateService.render(PromptTemplateNames.DOC_REFERENCE_USER, Map.of(
            "referenceName", StrUtil.blankToDefault(source.name(), "参考文档"),
            "referenceOutline", outline.isEmpty() ? "（参考文档无明显章节结构）" : String.join("\n", outline),
            "referenceContent", truncate(source.text(), Math.max(1000, properties.getReferenceMaxChars())),
            "requirement", requirement));
        if (skillMatch != null) {
            userPrompt = skillQuestionComposer.compose(userPrompt, skillMatch);
        }
        return new ReferencePreparation(source, requirement, outline, skillMatch, systemPrompt, userPrompt);
    }

    private DocGenGenerateVo finalizeRecord(ReferencePreparation prep, String body, long costMillis) {
        DocGenerationRecordEntity record = buildRecord(prep.source(), prep.requirement(), body, costMillis, prep.skillMatch());
        recordMapper.insert(record);
        if (StrUtil.isBlank(body)) {
            record.setGenerationStatus(STATUS_FAILED);
            record.setErrorMsg("模型生成正文为空或超时");
            recordMapper.updateById(record);
            throw new DochubFrameException(DocGenCode.GENERATION_FAILED.getCode(), "文档仿写失败：模型返回正文为空或超时");
        }
        return new DocGenGenerateVo(record.getRecordCode(), prep.source().name(), record.getFileName(),
            prep.outline(), body, STATUS_SUCCESS);
    }

    private String generateBody(String systemPrompt, String userPrompt) {
        try {
            return CompletableFuture.supplyAsync(
                    () -> observedChatModelService.callText("doc-reference", systemPrompt, userPrompt, docgenOptions(), null),
                    executorService)
                .orTimeout(Math.max(properties.getBodyTimeoutMs(), 1L), TimeUnit.MILLISECONDS)
                .exceptionally(exception -> {
                    log.warn("参考文档仿写生成失败: {}", exception.getMessage());
                    return null;
                })
                .join();
        }
        catch (Exception exception) {
            log.warn("参考文档仿写生成失败", exception);
            return null;
        }
    }

    private ReferenceSource resolveReference(MultipartFile file, Long referenceDocumentId) {
        if (file != null && !file.isEmpty()) {
            String originalFileName = StrUtil.blankToDefault(file.getOriginalFilename(), "参考文档");
            try {
                DocumentFileTypeEnum fileType = DocumentFileTypeEnum.fromFileName(originalFileName);
                // 仿写只需要纯文本，走轻量提取（跳过结构抽取/LLM 消歧），避免解析阶段长时间卡住。
                String text = documentParserService.parseTextOnly(
                    file.getBytes(), originalFileName, file.getContentType(), fileType);
                return new ReferenceSource(originalFileName, null, text);
            }
            catch (Exception exception) {
                log.error("参考文档解析失败: {}", originalFileName, exception);
                throw new DochubFrameException(DocGenCode.GENERATION_FAILED.getCode(),
                    "参考文档解析失败: " + exception.getMessage(), exception);
            }
        }
        if (referenceDocumentId != null) {
            DochubDocument document = documentMapper.selectById(referenceDocumentId);
            if (document == null || StrUtil.isBlank(document.getParseTextPath())) {
                throw new DochubFrameException(DocGenCode.GENERATION_FAILED.getCode(),
                    "参考文档不存在或尚未完成解析: " + referenceDocumentId);
            }
            String text = storageService.downloadText(document.getParseTextPath());
            return new ReferenceSource(document.getDocumentName(), document.getId(), text);
        }
        return null;
    }

    private DocGenerationRecordEntity buildRecord(ReferenceSource source, String requirement,
                                                  String body, long costMillis, SkillMatchResult skillMatch) {
        DocGenerationRecordEntity record = new DocGenerationRecordEntity();
        record.setId(uidGenerator.getUid());
        record.setRecordCode("DG" + uidGenerator.getUid());
        record.setTemplateName(source.name());
        record.setGenerationMode("REFERENCE_GUIDED");
        record.setUserRequirement(StrUtil.isBlank(requirement) ? null : requirement);
        record.setReferenceDocumentId(source.documentId());
        record.setGeneratedMarkdown(body);
        record.setOutputFormat("md");
        record.setFileName("仿写-" + stripExtension(source.name()) + ".md");
        record.setCostMillis(costMillis);
        record.setGenerationStatus(STATUS_SUCCESS);
        record.setStatus(BusinessStatus.YES.getCode());
        if (skillMatch != null && skillMatch.getSkill() != null) {
            record.setVariablesJson("{\"matchedSkill\":\"" + skillMatch.getSkill().getName() + "\"}");
        }
        return record;
    }

    private String truncate(String text, int maxChars) {
        if (StrUtil.isBlank(text) || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "\n……（参考文档过长，已截断）";
    }

    private String stripExtension(String fileName) {
        if (StrUtil.isBlank(fileName)) {
            return "文档";
        }
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private List<String> extractHeadings(String markdown) {
        List<String> headings = new ArrayList<>();
        if (StrUtil.isBlank(markdown)) {
            return headings;
        }
        for (String line : markdown.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.matches("^#{1,6}\\s+.+")) {
                headings.add(trimmed.replaceFirst("^#{1,6}\\s+", ""));
            }
        }
        return headings;
    }

    private record ReferenceSource(String name, Long documentId, String text) {
    }

    private record ReferencePreparation(ReferenceSource source, String requirement, List<String> outline,
                                        SkillMatchResult skillMatch, String systemPrompt, String userPrompt) {
    }

    /** 文档生成使用非推理模型（默认 qwen-plus），保证内容能立即流式输出。 */
    private ChatOptions docgenOptions() {
        // 未单独配置 docgen 模型时返回 null，使用全局聊天模型（跟随 yaml，方便切换本地模型）
        if (StrUtil.isBlank(properties.getModel())) {
            return null;
        }
        return OpenAiChatOptions.builder()
            .model(properties.getModel())
            .build();
    }

    // ===== SSE 事件构造 =====

    private String status(String content) {
        return jsonEvent("status", content);
    }

    private String text(String content) {
        return jsonEvent("text", content);
    }

    private String error(String content) {
        return jsonEvent("error", content);
    }

    private String done(Object content) {
        return jsonEvent("done", content);
    }

    private String jsonEvent(String type, Object content) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", type);
            payload.put("content", content);
            return objectMapper.writeValueAsString(payload);
        }
        catch (Exception exception) {
            return "{\"type\":\"error\",\"content\":\"流式事件序列化失败\"}";
        }
    }
}
