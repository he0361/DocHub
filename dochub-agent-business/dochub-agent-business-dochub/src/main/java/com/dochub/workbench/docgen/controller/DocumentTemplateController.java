package com.dochub.workbench.docgen.controller;

import com.dochub.workbench.docgen.dto.DocTemplateIdDto;
import com.dochub.workbench.docgen.dto.DocTemplateQueryDto;
import com.dochub.workbench.docgen.dto.DocTemplateSaveDto;
import com.dochub.workbench.docgen.service.DocumentTemplateService;
import com.dochub.workbench.docgen.vo.DocTemplateDetailVo;
import com.dochub.workbench.docgen.vo.DocTemplatePageVo;
import io.swagger.v3.oas.annotations.Operation;
import org.javaup.common.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文枢 DocHub 文档模板中心接口。
 */
@RestController
@RequestMapping("/manage/workbench/template")
public class DocumentTemplateController {

    private final DocumentTemplateService templateService;

    public DocumentTemplateController(DocumentTemplateService templateService) {
        this.templateService = templateService;
    }

    @Operation(summary = "分页查询文档模板")
    @PostMapping("/page")
    public ApiResponse<DocTemplatePageVo> page(@RequestBody DocTemplateQueryDto dto) {
        return ApiResponse.ok(templateService.pageQuery(dto));
    }

    @Operation(summary = "查询模板详情")
    @PostMapping("/detail")
    public ApiResponse<DocTemplateDetailVo> detail(@RequestBody DocTemplateIdDto dto) {
        return ApiResponse.ok(templateService.detail(dto.getTemplateId()));
    }

    @Operation(summary = "保存/更新模板")
    @PostMapping("/save")
    public ApiResponse<Long> save(@RequestBody DocTemplateSaveDto dto) {
        return ApiResponse.ok(templateService.save(dto));
    }

    @Operation(summary = "删除模板")
    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody DocTemplateIdDto dto) {
        templateService.delete(dto.getTemplateId());
        return ApiResponse.ok(null);
    }
}
