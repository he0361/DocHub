package com.dochub.workbench.modelconfig.security;

import com.dochub.workbench.auth.service.AdminAuthService;
import com.dochub.workbench.modelconfig.support.AdminGuard;
import org.javaup.exception.DochubFrameException;
import org.springframework.stereotype.Component;

/** Request-only second factor for every mutating embedding operation. */
@Component
public class EmbeddingChangeConfirmationGuard {
    public static final String EXACT_PHRASE = "我确认更改向量模型";

    private final AdminGuard adminGuard;
    private final AdminAuthService authService;

    public EmbeddingChangeConfirmationGuard(AdminGuard adminGuard, AdminAuthService authService) {
        this.adminGuard = adminGuard;
        this.authService = authService;
    }

    public void verify(String username, String currentPassword, String confirmationPhrase) {
        adminGuard.require(username);
        if (!EXACT_PHRASE.equals(confirmationPhrase)) {
            throw new DochubFrameException(400, "请输入精确确认短语：" + EXACT_PHRASE);
        }
        if (currentPassword == null || !authService.verifyCurrentPassword(username, currentPassword)) {
            throw new DochubFrameException(403, "管理员密码错误");
        }
    }
}
