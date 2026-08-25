package com.dochub.workbench.auth.service;

import cn.hutool.core.util.StrUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dochub.workbench.auth.data.AdminUserEntity;
import com.dochub.workbench.auth.dto.AdminUserSaveDto;
import com.dochub.workbench.auth.dto.AdminUserStatusDto;
import com.dochub.workbench.auth.mapper.AdminUserMapper;
import com.dochub.workbench.auth.support.PasswordHasher;
import com.dochub.workbench.auth.vo.AdminUserItemVo;
import lombok.extern.slf4j.Slf4j;
import org.javaup.exception.DochubFrameException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 文枢 DocHub 控制台账号管理。
 */
@Slf4j
@Service
public class AdminUserService {

    private final AdminUserMapper adminUserMapper;
    private final PasswordHasher passwordHasher;
    private final UidGenerator uidGenerator;

    public AdminUserService(AdminUserMapper adminUserMapper,
                            PasswordHasher passwordHasher,
                            UidGenerator uidGenerator) {
        this.adminUserMapper = adminUserMapper;
        this.passwordHasher = passwordHasher;
        this.uidGenerator = uidGenerator;
    }

    public List<AdminUserItemVo> listUsers() {
        List<AdminUserEntity> users = adminUserMapper.selectList(new LambdaQueryWrapper<AdminUserEntity>()
            .orderByAsc(AdminUserEntity::getId));
        return users.stream().map(this::toVo).toList();
    }

    public AdminUserItemVo saveUser(AdminUserSaveDto dto) {
        if (dto == null) {
            throw new DochubFrameException(400, "请求不能为空");
        }
        if (dto.getId() == null) {
            return createUser(dto);
        }
        return updateUser(dto);
    }

    public void setStatus(AdminUserStatusDto dto) {
        if (dto == null || dto.getId() == null) {
            throw new DochubFrameException(400, "账号 id 不能为空");
        }
        AdminUserEntity entity = adminUserMapper.selectById(dto.getId());
        if (entity == null) {
            throw new DochubFrameException(400, "账号不存在");
        }
        entity.setStatus(dto.getStatus() != null && dto.getStatus() == 1 ? 1 : 0);
        entity.setEditTime(new Date());
        adminUserMapper.updateById(entity);
        log.info("控制台账号状态变更: username={}, status={}", entity.getUsername(), entity.getStatus());
    }

    private AdminUserItemVo createUser(AdminUserSaveDto dto) {
        if (StrUtil.isBlank(dto.getUsername()) || StrUtil.isBlank(dto.getPassword())) {
            throw new DochubFrameException(400, "用户名和密码不能为空");
        }
        String username = dto.getUsername().trim();
        AdminUserEntity existing = adminUserMapper.selectOne(new LambdaQueryWrapper<AdminUserEntity>()
            .eq(AdminUserEntity::getUsername, username)
            .last("LIMIT 1"));
        if (existing != null) {
            throw new DochubFrameException(400, "用户名已存在: " + username);
        }
        AdminUserEntity entity = new AdminUserEntity();
        entity.setId(uidGenerator.getUid());
        entity.setUsername(username);
        entity.setPasswordHash(passwordHasher.hash(dto.getPassword()));
        entity.setDisplayName(StrUtil.blankToDefault(dto.getDisplayName(), username));
        entity.setIsAdmin(Boolean.TRUE.equals(dto.getIsAdmin()) ? 1 : 0);
        entity.setPermissions(StrUtil.nullToEmpty(dto.getPermissions()));
        entity.setStatus(1);
        entity.setCreateTime(new Date());
        entity.setEditTime(new Date());
        adminUserMapper.insert(entity);
        log.info("新增控制台账号: {}", username);
        return toVo(entity);
    }

    private AdminUserItemVo updateUser(AdminUserSaveDto dto) {
        AdminUserEntity entity = adminUserMapper.selectById(dto.getId());
        if (entity == null) {
            throw new DochubFrameException(400, "账号不存在");
        }
        if (StrUtil.isNotBlank(dto.getPassword())) {
            entity.setPasswordHash(passwordHasher.hash(dto.getPassword()));
        }
        if (StrUtil.isNotBlank(dto.getDisplayName())) {
            entity.setDisplayName(dto.getDisplayName().trim());
        }
        if (dto.getIsAdmin() != null) {
            entity.setIsAdmin(dto.getIsAdmin() ? 1 : 0);
        }
        if (dto.getPermissions() != null) {
            entity.setPermissions(dto.getPermissions().trim());
        }
        entity.setEditTime(new Date());
        adminUserMapper.updateById(entity);
        log.info("编辑控制台账号: {}", entity.getUsername());
        return toVo(entity);
    }

    private AdminUserItemVo toVo(AdminUserEntity entity) {
        return new AdminUserItemVo(
            entity.getId(),
            entity.getUsername(),
            entity.getDisplayName(),
            entity.getIsAdmin() != null && entity.getIsAdmin() == 1,
            splitPermissions(entity.getPermissions()),
            entity.getStatus(),
            entity.getCreateTime());
    }

    private List<String> splitPermissions(String permissions) {
        if (StrUtil.isBlank(permissions)) {
            return new ArrayList<>();
        }
        return Arrays.stream(permissions.split(","))
            .map(String::trim)
            .filter(StrUtil::isNotBlank)
            .distinct()
            .toList();
    }
}
