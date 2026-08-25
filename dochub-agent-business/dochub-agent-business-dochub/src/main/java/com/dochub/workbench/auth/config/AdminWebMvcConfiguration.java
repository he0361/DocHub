package com.dochub.workbench.auth.config;

import com.dochub.workbench.auth.support.AdminAuthInterceptor;
import com.dochub.workbench.auth.support.PreviewModeInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 后台管理登录与预览模式的 MVC 配置。
 */
@Configuration
@EnableConfigurationProperties({AdminAuthProperties.class, PreviewModeProperties.class})
public class AdminWebMvcConfiguration implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;

    private final PreviewModeInterceptor previewModeInterceptor;

    public AdminWebMvcConfiguration(AdminAuthInterceptor adminAuthInterceptor,
                                    PreviewModeInterceptor previewModeInterceptor) {
        this.adminAuthInterceptor = adminAuthInterceptor;
        this.previewModeInterceptor = previewModeInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 只有"管理控制台"相关接口强制登录；聊天(/api/chat/**)、文档仿写/模板/技能(/manage/workbench/**)不拦截
        registry.addInterceptor(adminAuthInterceptor)
            .addPathPatterns(
                "/manage/document/**",       // 文档接入
                "/manage/knowledge/**",      // 知识路由 / 路由追踪
                "/admin/user/**",            // 账号管理
                "/admin/auth/me");           // 当前账号信息

        registry.addInterceptor(previewModeInterceptor)
            .addPathPatterns("/**");
    }
}
