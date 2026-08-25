package com.dochub.workbench.auth.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dochub.workbench.auth.data.AdminUserEntity;
import com.dochub.workbench.auth.dto.AdminUserSaveDto;
import com.dochub.workbench.auth.dto.AdminUserStatusDto;
import com.dochub.workbench.auth.mapper.AdminUserMapper;
import com.dochub.workbench.auth.service.AdminUserService;
import com.dochub.workbench.auth.support.AdminRequestContext;
import com.dochub.workbench.auth.vo.AdminUserItemVo;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.javaup.common.ApiResponse;
import org.javaup.exception.DochubFrameException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 文枢 DocHub 控制台账号管理接口（仅管理员可访问，后端强制校验 isAdmin）。
 */
@RestController
@RequestMapping("/admin/user")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final AdminUserMapper adminUserMapper;

    public AdminUserController(AdminUserService adminUserService, AdminUserMapper adminUserMapper) {
        this.adminUserService = adminUserService;
        this.adminUserMapper = adminUserMapper;
    }

    @Operation(summary = "账号列表")
    @PostMapping("/list")
    public ApiResponse<List<AdminUserItemVo>> list(HttpServletRequest request) {
        requireAccountManage(request);
        return ApiResponse.ok(adminUserService.listUsers());
    }

    @Operation(summary = "新增/编辑账号")
    @PostMapping("/save")
    public ApiResponse<AdminUserItemVo> save(HttpServletRequest request, @RequestBody AdminUserSaveDto dto) {
        requireAccountManage(request);
        return ApiResponse.ok(adminUserService.saveUser(dto));
    }

    @Operation(summary = "停用/启用账号")
    @PostMapping("/status")
    public ApiResponse<Void> status(HttpServletRequest request, @RequestBody AdminUserStatusDto dto) {
        requireAccountManage(request);
        adminUserService.setStatus(dto);
        return ApiResponse.ok(null);
    }

    /** 账号管理：管理员或有 account_manage 权限的账号可操作。 */
    private void requireAccountManage(HttpServletRequest request) {
        String username = AdminRequestContext.resolveUsername(request);
        AdminUserEntity user = adminUserMapper.selectOne(new LambdaQueryWrapper<AdminUserEntity>()
            .eq(AdminUserEntity::getUsername, username)
            .last("LIMIT 1"));
        if (user == null) {
            throw new DochubFrameException(403, "账号不存在");
        }
        boolean isAdmin = user.getIsAdmin() != null && user.getIsAdmin() == 1;
        boolean hasPerm = StrUtil.isNotBlank(user.getPermissions())
            && Arrays.asList(user.getPermissions().split(",")).stream()
            .map(String::trim).anyMatch(p -> "account_manage".equals(p));
        if (!isAdmin && !hasPerm) {
            throw new DochubFrameException(403, "无账号管理权限，请联系管理员授权");
        }
    }
}
