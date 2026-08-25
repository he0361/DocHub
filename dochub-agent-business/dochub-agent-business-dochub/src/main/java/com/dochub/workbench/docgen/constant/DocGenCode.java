package com.dochub.workbench.docgen.constant;

/**
 * 文枢 DocHub 文档生成模块错误码。
 */
public enum DocGenCode {

    TEMPLATE_NOT_FOUND(12001, "文档模板不存在"),

    RECORD_NOT_FOUND(12002, "生成记录不存在"),

    EXPORT_FORMAT_UNSUPPORTED(12003, "不支持的导出格式"),

    GENERATION_FAILED(12004, "文档生成失败"),

    RECORD_CONTENT_EMPTY(12005, "生成记录没有可导出的正文内容"),

    TEMPLATE_CODE_DUPLICATED(12006, "模板编码已存在");

    private final Integer code;

    private final String msg;

    DocGenCode(Integer code, String msg) {
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
