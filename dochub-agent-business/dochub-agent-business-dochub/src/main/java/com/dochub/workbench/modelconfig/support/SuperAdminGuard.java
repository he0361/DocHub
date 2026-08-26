package com.dochub.workbench.modelconfig.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dochub.workbench.auth.data.AdminUserEntity;
import com.dochub.workbench.auth.mapper.AdminUserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Resolves the request subject again from the database and permits only an enabled super administrator. */
@Component
public class SuperAdminGuard {

    private final AdminUserMapper adminUserMapper;

    public SuperAdminGuard(AdminUserMapper adminUserMapper) {
        this.adminUserMapper = adminUserMapper;
    }

    public AdminUserEntity require(String username) {
        AdminUserEntity user = adminUserMapper.selectOne(new LambdaQueryWrapper<AdminUserEntity>()
            .eq(AdminUserEntity::getUsername, username)
            .last("LIMIT 1"));
        if (user == null || user.getStatus() == null || user.getStatus() != 1
            || user.getIsAdmin() == null || user.getIsAdmin() != 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅超级管理员可以管理模型配置");
        }
        return user;
    }
}
