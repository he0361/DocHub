package com.dochub.workbench.chatagent.tool;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.dochub.workbench.chatagent.model.SearchReference;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 工具类
 * @author: zhangjihe
 **/

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TavilySearchToolResult {

    private String query;
    private String answer;
    private List<SearchReference> results;
}
