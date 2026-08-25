package com.dochub.workbench.skill.constant;

/**
 * 文枢 DocHub 技能子系统错误码。
 */
public enum SkillCode {

    SKILL_NOT_FOUND(13001, "技能不存在"),

    SKILL_ALREADY_INSTALLED(13002, "技能已安装"),

    SKILL_INSTALL_FAILED(13003, "技能安装失败"),

    SKILL_UPLOAD_INVALID(13004, "技能包无效"),

    SKILL_DISABLED(13005, "技能未启用");

    private final Integer code;

    private final String msg;

    SkillCode(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public Integer getCode() {
        return this.code;
    }

    public String getMsg() {
        return this.msg == null ? "" : this.msg;
    }
}
