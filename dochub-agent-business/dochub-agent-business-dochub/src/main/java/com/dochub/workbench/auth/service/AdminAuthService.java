package com.dochub.workbench.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import com.dochub.workbench.auth.dto.AdminLoginRequest;
import com.dochub.workbench.auth.vo.AdminLoginVo;
import com.dochub.workbench.auth.vo.AdminProfileVo;

/**
 * 后台登录认证服务。
 */
public interface AdminAuthService {

    AdminLoginVo login(AdminLoginRequest request);

    AdminProfileVo currentProfile(HttpServletRequest request);
}
