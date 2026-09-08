package com.dochub.workbench.modelconfig.controller;

import com.dochub.workbench.auth.data.AdminUserEntity;
import com.dochub.workbench.auth.mapper.AdminUserMapper;
import com.dochub.workbench.auth.support.AdminRequestContext;
import com.dochub.workbench.modelconfig.service.ModelConfigService;
import com.dochub.workbench.modelconfig.service.EmbeddingModelChangeService;
import com.dochub.workbench.modelconfig.support.AdminGuard;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminModelConfigControllerTest {

    @Test
    void nonAdminCannotReadModelConfig() throws Exception {
        AdminUserMapper mapper = mock(AdminUserMapper.class);
        AdminUserEntity operator = new AdminUserEntity();
        operator.setIsAdmin(0);
        when(mapper.selectOne(any())).thenReturn(operator);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
            new AdminModelConfigController(mock(ModelConfigService.class), mock(EmbeddingModelChangeService.class),
                new AdminGuard(mapper))).build();

        mockMvc.perform(post("/admin/model-config/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .requestAttr(AdminRequestContext.ADMIN_USERNAME_ATTRIBUTE, "operator"))
            .andExpect(status().isForbidden());
    }
}
