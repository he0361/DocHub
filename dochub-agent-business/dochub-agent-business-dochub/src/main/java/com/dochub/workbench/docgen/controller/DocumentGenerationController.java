package com.dochub.workbench.docgen.controller;

import com.dochub.workbench.docgen.dto.DocGenExportDto;
import com.dochub.workbench.docgen.dto.DocGenGenerateDto;
import com.dochub.workbench.docgen.dto.DocGenIngestDto;
import com.dochub.workbench.docgen.dto.DocGenRecordDeleteDto;
import com.dochub.workbench.docgen.dto.DocGenRecordQueryDto;
import com.dochub.workbench.docgen.dto.DocGenReferenceGenerateDto;
import com.dochub.workbench.docgen.service.DocExportResult;
import com.dochub.workbench.docgen.service.DocumentGenerationService;
import com.dochub.workbench.docgen.service.ReferenceDocumentGenerationService;
import com.dochub.workbench.docgen.vo.DocGenGenerateVo;
import com.dochub.workbench.docgen.vo.DocGenIngestVo;
import com.dochub.workbench.docgen.vo.DocGenRecordPageVo;
import io.swagger.v3.oas.annotations.Operation;
import org.javaup.common.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 文枢 DocHub 文档生成接口：生成 / 导出 / 历史 / 一键入库。
 */
@RestController
@RequestMapping("/manage/workbench/docgen")
public class DocumentGenerationController {

    private final DocumentGenerationService generationService;
    private final ReferenceDocumentGenerationService referenceGenerationService;

    public DocumentGenerationController(DocumentGenerationService generationService,
                                        ReferenceDocumentGenerationService referenceGenerationService) {
        this.generationService = generationService;
        this.referenceGenerationService = referenceGenerationService;
    }

    @Operation(summary = "基于模板生成文档")
    @PostMapping("/generate")
    public ApiResponse<DocGenGenerateVo> generate(@RequestBody DocGenGenerateDto dto) {
        return ApiResponse.ok(generationService.generate(dto));
    }

    @Operation(summary = "参考文档仿写：上传参考文档 + 需求，仿照格式生成")
    @PostMapping(value = "/generate-reference", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocGenGenerateVo> generateReference(
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart(value = "meta", required = false) DocGenReferenceGenerateDto dto) {
        return ApiResponse.ok(referenceGenerationService.generateFromReference(file,
            dto == null ? new DocGenReferenceGenerateDto() : dto));
    }

    @Operation(summary = "参考文档仿写（SSE 流式）：正文逐字输出，避免生成过程卡住")
    @PostMapping(value = "/generate-reference/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = "text/event-stream;charset=UTF-8")
    public Flux<String> generateReferenceStream(
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart(value = "meta", required = false) DocGenReferenceGenerateDto dto) {
        return referenceGenerationService.generateFromReferenceStream(file,
            dto == null ? new DocGenReferenceGenerateDto() : dto);
    }

    @Operation(summary = "下载生成的文档（md/docx）")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam("recordCode") String recordCode,
                                         @RequestParam(value = "format", required = false) String format) {
        DocExportResult result = generationService.export(recordCode, format);
        String encodedFileName = URLEncoder.encode(result.getFileName(), StandardCharsets.UTF_8)
            .replace("+", "%20");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
            .contentType(MediaType.parseMediaType(result.getMediaType()))
            .body(result.getContent());
    }

    @Operation(summary = "分页查询生成历史")
    @PostMapping("/records")
    public ApiResponse<DocGenRecordPageVo> records(@RequestBody DocGenRecordQueryDto dto) {
        return ApiResponse.ok(generationService.pageRecords(dto));
    }

    @Operation(summary = "删除生成历史记录")
    @PostMapping("/record/delete")
    public ApiResponse<Void> deleteRecord(@RequestBody DocGenRecordDeleteDto dto) {
        generationService.deleteRecord(dto == null ? null : dto.getRecordCode());
        return ApiResponse.ok(null);
    }

    @Operation(summary = "生成文档一键入库")
    @PostMapping("/ingest")
    public ApiResponse<DocGenIngestVo> ingest(@RequestBody DocGenIngestDto dto) {
        return ApiResponse.ok(generationService.ingest(dto));
    }
}
