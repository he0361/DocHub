package com.dochub.workbench.modelconfig.security;

import com.dochub.workbench.auth.service.AdminAuthService;
import com.dochub.workbench.modelconfig.support.SuperAdminGuard;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EmbeddingChangeConfirmationGuardTest {
    private final SuperAdminGuard superAdminGuard = mock(SuperAdminGuard.class);
    private final AdminAuthService authService = mock(AdminAuthService.class);
    private final EmbeddingChangeConfirmationGuard guard = new EmbeddingChangeConfirmationGuard(superAdminGuard, authService);

    @Test
    void rejectsWrongPassword() {
        when(authService.verifyCurrentPassword("admin", "wrong")).thenReturn(false);
        assertThatThrownBy(() -> guard.verify("admin", "wrong", EmbeddingChangeConfirmationGuard.EXACT_PHRASE))
            .hasMessageContaining("管理员密码错误");
    }

    @Test
    void rejectsPhraseThatIsNotExactBeforePasswordLookup() {
        assertThatThrownBy(() -> guard.verify("admin", "password", "我确认修改向量模型"))
            .hasMessageContaining(EmbeddingChangeConfirmationGuard.EXACT_PHRASE);
        verifyNoInteractions(authService);
    }
}
