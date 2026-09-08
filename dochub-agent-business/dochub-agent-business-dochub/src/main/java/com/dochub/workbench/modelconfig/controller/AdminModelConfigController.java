package com.dochub.workbench.modelconfig.controller;

import com.dochub.workbench.auth.support.AdminRequestContext;
import com.dochub.workbench.modelconfig.dto.ModelConfigSaveDto;
import com.dochub.workbench.modelconfig.dto.ModelConfigTestDto;
import com.dochub.workbench.modelconfig.dto.EmbeddingMigrationRetryDto;
import com.dochub.workbench.modelconfig.dto.EmbeddingMigrationStatusDto;
import com.dochub.workbench.modelconfig.dto.EmbeddingModelChangeDto;
import com.dochub.workbench.modelconfig.dto.EmbeddingRollbackDto;
import com.dochub.workbench.modelconfig.service.EmbeddingModelChangeService;
import com.dochub.workbench.modelconfig.service.ModelConfigService;
import com.dochub.workbench.modelconfig.support.AdminGuard;
import com.dochub.workbench.modelconfig.vo.ModelConfigVo;
import com.dochub.workbench.modelconfig.vo.ModelConnectionTestVo;
import com.dochub.workbench.modelconfig.vo.EmbeddingConfigVo;
import com.dochub.workbench.modelconfig.vo.EmbeddingMigrationVo;
import com.dochub.workbench.modelconfig.vo.EmbeddingModelChangeVo;
import com.dochub.workbench.modelconfig.vo.EmbeddingModelTestVo;
import jakarta.servlet.http.HttpServletRequest;
import org.javaup.common.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administrator-only model configuration routes. */
@RestController
@RequestMapping("/admin/model-config")
public class AdminModelConfigController {

    private final ModelConfigService modelConfigService;
    private final EmbeddingModelChangeService embeddingModelChangeService;
    private final AdminGuard adminGuard;

    public AdminModelConfigController(ModelConfigService modelConfigService,
                                      EmbeddingModelChangeService embeddingModelChangeService,
                                      AdminGuard adminGuard) {
        this.modelConfigService = modelConfigService;
        this.embeddingModelChangeService = embeddingModelChangeService;
        this.adminGuard = adminGuard;
    }

    @PostMapping("/query")
    public ApiResponse<ModelConfigVo> query(HttpServletRequest request) {
        String username = requireAdmin(request);
        return ApiResponse.ok(modelConfigService.queryChat(username));
    }

    @PostMapping("/chat/test")
    public ApiResponse<ModelConnectionTestVo> test(HttpServletRequest request, @RequestBody ModelConfigTestDto dto) {
        String username = requireAdmin(request);
        return ApiResponse.ok(modelConfigService.testChat(username, dto));
    }

    @PostMapping("/chat/save")
    public ApiResponse<ModelConfigVo> save(HttpServletRequest request, @RequestBody ModelConfigSaveDto dto) {
        String username = requireAdmin(request);
        return ApiResponse.ok(modelConfigService.saveChat(username, dto));
    }

    @PostMapping("/embedding/query")
    public ApiResponse<EmbeddingConfigVo> queryEmbedding(HttpServletRequest request) {
        return ApiResponse.ok(embeddingModelChangeService.query(requireAdmin(request)));
    }

    @PostMapping("/embedding/test")
    public ApiResponse<EmbeddingModelTestVo> testEmbedding(HttpServletRequest request,
                                                            @RequestBody EmbeddingModelChangeDto dto) {
        return ApiResponse.ok(embeddingModelChangeService.test(requireAdmin(request), dto));
    }

    @PostMapping("/embedding/change")
    public ApiResponse<EmbeddingModelChangeVo> changeEmbedding(HttpServletRequest request,
                                                                @RequestBody EmbeddingModelChangeDto dto) {
        return ApiResponse.ok(embeddingModelChangeService.change(requireAdmin(request), dto));
    }

    @PostMapping("/embedding/migration/status")
    public ApiResponse<EmbeddingMigrationVo> embeddingMigrationStatus(HttpServletRequest request,
                                                                       @RequestBody(required = false) EmbeddingMigrationStatusDto dto) {
        return ApiResponse.ok(embeddingModelChangeService.migrationStatus(requireAdmin(request), dto == null ? null : dto.getMigrationId()));
    }

    @PostMapping("/embedding/migration/retry")
    public ApiResponse<EmbeddingMigrationVo> retryEmbeddingMigration(HttpServletRequest request,
                                                                      @RequestBody EmbeddingMigrationRetryDto dto) {
        return ApiResponse.ok(embeddingModelChangeService.retry(requireAdmin(request), dto));
    }

    @PostMapping("/embedding/rollback")
    public ApiResponse<EmbeddingModelChangeVo> rollbackEmbedding(HttpServletRequest request,
                                                                  @RequestBody EmbeddingRollbackDto dto) {
        return ApiResponse.ok(embeddingModelChangeService.rollback(requireAdmin(request), dto));
    }

    private String requireAdmin(HttpServletRequest request) {
        String username = AdminRequestContext.resolveUsername(request);
        adminGuard.require(username);
        return username;
    }
}
