package com.dochub.workbench.modelconfig.controller;

import com.dochub.workbench.auth.support.AdminRequestContext;
import com.dochub.workbench.modelconfig.dto.ModelConfigSaveDto;
import com.dochub.workbench.modelconfig.dto.ModelConfigTestDto;
import com.dochub.workbench.modelconfig.service.ModelConfigService;
import com.dochub.workbench.modelconfig.support.SuperAdminGuard;
import com.dochub.workbench.modelconfig.vo.ModelConfigVo;
import com.dochub.workbench.modelconfig.vo.ModelConnectionTestVo;
import jakarta.servlet.http.HttpServletRequest;
import org.javaup.common.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Super-administrator-only model configuration routes. */
@RestController
@RequestMapping("/admin/model-config")
public class AdminModelConfigController {

    private final ModelConfigService modelConfigService;
    private final SuperAdminGuard superAdminGuard;

    public AdminModelConfigController(ModelConfigService modelConfigService, SuperAdminGuard superAdminGuard) {
        this.modelConfigService = modelConfigService;
        this.superAdminGuard = superAdminGuard;
    }

    @PostMapping("/query")
    public ApiResponse<ModelConfigVo> query(HttpServletRequest request) {
        String username = requireSuperAdmin(request);
        return ApiResponse.ok(modelConfigService.queryChat(username));
    }

    @PostMapping("/chat/test")
    public ApiResponse<ModelConnectionTestVo> test(HttpServletRequest request, @RequestBody ModelConfigTestDto dto) {
        String username = requireSuperAdmin(request);
        return ApiResponse.ok(modelConfigService.testChat(username, dto));
    }

    @PostMapping("/chat/save")
    public ApiResponse<ModelConfigVo> save(HttpServletRequest request, @RequestBody ModelConfigSaveDto dto) {
        String username = requireSuperAdmin(request);
        return ApiResponse.ok(modelConfigService.saveChat(username, dto));
    }

    private String requireSuperAdmin(HttpServletRequest request) {
        String username = AdminRequestContext.resolveUsername(request);
        superAdminGuard.require(username);
        return username;
    }
}
