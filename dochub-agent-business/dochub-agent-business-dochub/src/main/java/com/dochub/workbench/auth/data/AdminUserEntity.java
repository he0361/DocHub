package com.dochub.workbench.auth.data;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 文枢 DocHub 控制台账号（存于主库 dochub_business_chat，统一管理）。
 */
@Data
@TableName("dochub_admin_user")
public class AdminUserEntity {

    private Long id;

    private String username;

    /** salt$sha256(salt+password) */
    private String passwordHash;

    private String displayName;

    /** 1=管理员（拥有全部权限），0=普通账号 */
    private Integer isAdmin;

    /** 逗号分隔的权限码 */
    private String permissions;

    /** 1=启用，0=停用 */
    private Integer status;

    private Date createTime;

    private Date editTime;
}
