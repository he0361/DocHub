package com.dochub.workbench.auth.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dochub.workbench.auth.data.AdminUserEntity;
import com.dochub.workbench.auth.dto.AdminLoginRequest;
import com.dochub.workbench.auth.mapper.AdminUserMapper;
import com.dochub.workbench.auth.service.AdminAuthService;
import com.dochub.workbench.auth.support.AdminJwtTokenService;
import com.dochub.workbench.auth.support.AdminRequestContext;
import com.dochub.workbench.auth.support.PasswordHasher;
import com.dochub.workbench.auth.vo.AdminLoginVo;
import com.dochub.workbench.auth.vo.AdminProfileVo;
import jakarta.servlet.http.HttpServletRequest;
import org.javaup.enums.BusinessStatus;
import org.javaup.exception.DochubFrameException;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 文枢 DocHub 控制台登录鉴权：账号从独立库 dochub_auth 查询，校验密码与启用状态。
 */
@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    private static final List<String> ADMIN_ALL_PERMISSIONS = List.of(
        "dashboard", "knowledge_route", "document_manage", "observability", "route_trace", "account_manage");

    private final AdminUserMapper adminUserMapper;
    private final AdminJwtTokenService adminJwtTokenService;
    private final PasswordHasher passwordHasher;

    public AdminAuthServiceImpl(AdminUserMapper adminUserMapper,
                                AdminJwtTokenService adminJwtTokenService,
                                PasswordHasher passwordHasher) {
        this.adminUserMapper = adminUserMapper;
        this.adminJwtTokenService = adminJwtTokenService;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public AdminLoginVo login(AdminLoginRequest request) {
        String username = StrUtil.trim(request == null ? null : request.getUsername());
        String password = StrUtil.trim(request == null ? null : request.getPassword());
        AdminUserEntity user = adminUserMapper.selectOne(new LambdaQueryWrapper<AdminUserEntity>()
            .eq(AdminUserEntity::getUsername, username)
            .last("LIMIT 1"));
        if (user == null || !passwordHasher.matches(password, user.getPasswordHash())) {
            throw new DochubFrameException(401, "账号或密码不正确");
        }
        if (user.getStatus() == null || user.getStatus() != BusinessStatus.YES.getCode()) {
            throw new DochubFrameException(403, "该账号已被停用，请联系管理员");
        }
        boolean isAdmin = user.getIsAdmin() != null && user.getIsAdmin() == 1;
        if (!isAdmin && StrUtil.isBlank(user.getPermissions())) {
            throw new DochubFrameException(403, "该账号暂无管理权限，请联系管理员授权");
        }
        String token = adminJwtTokenService.generateToken(username);
        return new AdminLoginVo(username, token, 720L);
    }

    @Override
    public AdminProfileVo currentProfile(HttpServletRequest request) {
        String username = AdminRequestContext.resolveUsername(request);
        AdminUserEntity user = adminUserMapper.selectOne(new LambdaQueryWrapper<AdminUserEntity>()
            .eq(AdminUserEntity::getUsername, username)
            .last("LIMIT 1"));
        if (user == null) {
            throw new DochubFrameException(401, "账号不存在");
        }
        boolean isAdmin = user.getIsAdmin() != null && user.getIsAdmin() == 1;
        List<String> permissions = isAdmin
            ? ADMIN_ALL_PERMISSIONS
            : splitPermissions(user.getPermissions());
        return new AdminProfileVo(user.getUsername(), user.getDisplayName(), isAdmin, permissions);
    }

    private List<String> splitPermissions(String permissions) {
        if (StrUtil.isBlank(permissions)) {
            return List.of();
        }
        return Arrays.stream(permissions.split(","))
            .map(String::trim)
            .filter(StrUtil::isNotBlank)
            .distinct()
            .toList();
    }
}
