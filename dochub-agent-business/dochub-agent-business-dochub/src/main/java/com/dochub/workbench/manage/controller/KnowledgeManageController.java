package com.dochub.workbench.manage.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import com.dochub.workbench.auth.support.AdminRequestContext;
import com.dochub.workbench.modelconfig.support.SuperAdminGuard;
import com.dochub.workbench.manage.dto.DocumentProfileBatchRegenerateDto;
import com.dochub.workbench.manage.dto.DocumentProfileDetailQueryDto;
import com.dochub.workbench.manage.dto.DocumentProfileRegenerateDto;
import com.dochub.workbench.manage.dto.KnowledgeRouteTraceQueryDto;
import com.dochub.workbench.manage.dto.KnowledgeClassificationReviewQueryDto;
import com.dochub.workbench.manage.dto.KnowledgeClassificationResolveDto;
import com.dochub.workbench.manage.dto.KnowledgeScopeDeleteDto;
import com.dochub.workbench.manage.dto.KnowledgeScopeSaveDto;
import com.dochub.workbench.manage.dto.KnowledgeTopicDeleteDto;
import com.dochub.workbench.manage.dto.KnowledgeTopicQueryDto;
import com.dochub.workbench.manage.dto.KnowledgeTopicSaveDto;
import com.dochub.workbench.manage.dto.TopicDocumentRelationListQueryDto;
import com.dochub.workbench.manage.dto.TopicDocumentRelationRemoveDto;
import com.dochub.workbench.manage.dto.TopicDocumentRelationSaveDto;
import com.dochub.workbench.manage.service.KnowledgeManageService;
import com.dochub.workbench.manage.service.KnowledgeClassificationReviewService;
import com.dochub.workbench.manage.vo.DocumentProfileVo;
import com.dochub.workbench.manage.vo.KnowledgeRouteTracePageVo;
import com.dochub.workbench.manage.vo.KnowledgeClassificationReviewVo;
import com.dochub.workbench.manage.vo.KnowledgeScopeItemVo;
import com.dochub.workbench.manage.vo.KnowledgeTopicItemVo;
import com.dochub.workbench.manage.vo.TopicDocumentRelationItemVo;
import org.javaup.common.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 控制层
 * @author: zhangjihe
 **/
@RestController
@RequestMapping("/manage/knowledge")
public class KnowledgeManageController {

    private final KnowledgeManageService knowledgeManageService;
    private final KnowledgeClassificationReviewService classificationReviewService;
    private final SuperAdminGuard superAdminGuard;

    public KnowledgeManageController(KnowledgeManageService knowledgeManageService,
                                     KnowledgeClassificationReviewService classificationReviewService,
                                     SuperAdminGuard superAdminGuard) {
        this.knowledgeManageService = knowledgeManageService;
        this.classificationReviewService = classificationReviewService;
        this.superAdminGuard = superAdminGuard;
    }

    @Operation(summary = "保存知识范围节点")
    @PostMapping("/scope/save")
    public ApiResponse<KnowledgeScopeItemVo> saveScope(@Valid @RequestBody KnowledgeScopeSaveDto dto) {
        return ApiResponse.ok(knowledgeManageService.saveScope(dto));
    }

    @Operation(summary = "删除知识范围节点")
    @PostMapping("/scope/delete")
    public ApiResponse<Boolean> deleteScope(@Valid @RequestBody KnowledgeScopeDeleteDto dto) {
        return ApiResponse.ok(knowledgeManageService.deleteScope(dto));
    }

    @Operation(summary = "查询知识范围列表")
    @PostMapping("/scope/list")
    public ApiResponse<List<KnowledgeScopeItemVo>> listScopes() {
        return ApiResponse.ok(knowledgeManageService.listScopes());
    }

    @Operation(summary = "保存知识主题节点")
    @PostMapping("/topic/save")
    public ApiResponse<KnowledgeTopicItemVo> saveTopic(@Valid @RequestBody KnowledgeTopicSaveDto dto) {
        return ApiResponse.ok(knowledgeManageService.saveTopic(dto));
    }

    @Operation(summary = "删除知识主题节点")
    @PostMapping("/topic/delete")
    public ApiResponse<Boolean> deleteTopic(@Valid @RequestBody KnowledgeTopicDeleteDto dto) {
        return ApiResponse.ok(knowledgeManageService.deleteTopic(dto));
    }

    @Operation(summary = "查询知识主题列表")
    @PostMapping("/topic/list")
    public ApiResponse<List<KnowledgeTopicItemVo>> listTopics(@RequestBody(required = false) KnowledgeTopicQueryDto dto) {
        return ApiResponse.ok(knowledgeManageService.listTopics(dto == null ? new KnowledgeTopicQueryDto() : dto));
    }

    @Operation(summary = "查询文档画像详情")
    @PostMapping("/document/profile/detail")
    public ApiResponse<DocumentProfileVo> queryProfile(@Valid @RequestBody DocumentProfileDetailQueryDto dto) {
        return ApiResponse.ok(knowledgeManageService.queryProfile(dto));
    }

    @Operation(summary = "重新生成文档画像")
    @PostMapping("/document/profile/regenerate")
    public ApiResponse<DocumentProfileVo> regenerateProfile(@Valid @RequestBody DocumentProfileRegenerateDto dto) {
        return ApiResponse.ok(knowledgeManageService.regenerateProfile(dto));
    }

    @Operation(summary = "批量重新生成文档画像")
    @PostMapping("/document/profile/batch/regenerate")
    public ApiResponse<List<DocumentProfileVo>> batchRegenerateProfiles(@Valid @RequestBody DocumentProfileBatchRegenerateDto dto) {
        return ApiResponse.ok(knowledgeManageService.batchRegenerateProfiles(dto));
    }

    @Operation(summary = "查询主题文档关联")
    @PostMapping("/topic/document/list")
    public ApiResponse<List<TopicDocumentRelationItemVo>> listTopicDocuments(@RequestBody(required = false) TopicDocumentRelationListQueryDto dto) {
        return ApiResponse.ok(knowledgeManageService.listTopicDocuments(dto == null ? new TopicDocumentRelationListQueryDto() : dto));
    }

    @Operation(summary = "保存主题文档关联")
    @PostMapping("/topic/document/save")
    public ApiResponse<TopicDocumentRelationItemVo> saveTopicDocumentRelation(@Valid @RequestBody TopicDocumentRelationSaveDto dto) {
        return ApiResponse.ok(knowledgeManageService.saveTopicDocumentRelation(dto));
    }

    @Operation(summary = "移除主题文档关联")
    @PostMapping("/topic/document/remove")
    public ApiResponse<Boolean> removeTopicDocumentRelation(@Valid @RequestBody TopicDocumentRelationRemoveDto dto) {
        return ApiResponse.ok(knowledgeManageService.removeTopicDocumentRelation(dto));
    }

    @Operation(summary = "分页查询知识路由追踪")
    @PostMapping("/route/trace/page/query")
    public ApiResponse<KnowledgeRouteTracePageVo> queryRouteTracePage(@RequestBody(required = false) KnowledgeRouteTraceQueryDto dto) {
        return ApiResponse.ok(knowledgeManageService.queryRouteTracePage(dto == null ? new KnowledgeRouteTraceQueryDto() : dto));
    }

    @Operation(summary = "查询知识分类待审核列表")
    @PostMapping("/classification/review/list")
    public ApiResponse<List<KnowledgeClassificationReviewVo>> listClassificationReviews(HttpServletRequest request,
                                                                                         @RequestBody(required = false) KnowledgeClassificationReviewQueryDto dto) {
        requireSuperAdmin(request);
        return ApiResponse.ok(classificationReviewService.list(dto));
    }

    @Operation(summary = "查询知识分类审核详情")
    @PostMapping("/classification/review/detail")
    public ApiResponse<KnowledgeClassificationReviewVo> classificationReviewDetail(HttpServletRequest request,
                                                                                     @RequestBody KnowledgeClassificationReviewQueryDto dto) {
        requireSuperAdmin(request);
        return ApiResponse.ok(classificationReviewService.detail(dto));
    }

    @Operation(summary = "确认知识分类")
    @PostMapping("/classification/review/resolve")
    public ApiResponse<KnowledgeClassificationReviewVo> resolveClassificationReview(HttpServletRequest request,
                                                                                     @Valid @RequestBody KnowledgeClassificationResolveDto dto) {
        String operator = requireSuperAdmin(request);
        return ApiResponse.ok(classificationReviewService.resolve(operator, dto));
    }

    private String requireSuperAdmin(HttpServletRequest request) {
        String username = AdminRequestContext.resolveUsername(request);
        superAdminGuard.require(username);
        return username;
    }
}
