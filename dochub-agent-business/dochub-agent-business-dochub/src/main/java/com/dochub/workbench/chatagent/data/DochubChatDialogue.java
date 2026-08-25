package com.dochub.workbench.chatagent.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.javaup.database.data.BaseTableData;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 数据实体
 * @author: zhangjihe
 **/

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("dochub_chat_dialogue")
@EqualsAndHashCode(callSuper = true)
public class DochubChatDialogue extends BaseTableData {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    @TableField("dialogue_code")
    private String conversationId;

    @TableField("dialogue_stage")
    private Integer sessionStatus;

    @TableField("chat_mode")
    private Integer chatMode;

    @TableField("selected_document_id")
    private Long selectedDocumentId;

    @TableField("selected_document_name")
    private String selectedDocumentName;
}
