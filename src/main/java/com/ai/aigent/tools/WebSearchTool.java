package com.ai.aigent.tools;


import com.ai.aigent.entity.VO.BochaWebSearchResponse;
import com.ai.aigent.entity.dto.BochaWebSearchRequest;
import com.ai.aigent.utils.BochaWebSearchClient;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WebSearchTool {
    @Autowired
    private BochaWebSearchClient client;
    @Tool(
        name = "bocha_web_search",
        description = "从博查AI搜索网页信息，返回网页标题、摘要和链接"
        ,returnDirect = false
    )
    public String webSearch(
            @ToolParam(description = "搜索关键词，例如：阿里巴巴2024年ESG报告")
            String query,
            @ToolParam(description = "时间范围，如 noLimit, oneDay, oneWeek, oneMonth, oneYear")
            String freshness,
            @ToolParam(description = "是否返回摘要 true/false")
            Boolean summary,
            @ToolParam(description = "返回条数，1-20")
            Integer count
    ) {
        try {
             BochaWebSearchRequest req = new BochaWebSearchRequest();
            req.setQuery(query);
            if (freshness != null) req.setFreshness(freshness);
            if (summary != null) req.setSummary(summary);
            if (count != null) req.setCount(count);
            BochaWebSearchResponse resp = client.search(req);
            if (resp == null || resp.getData() == null
                    || resp.getData().getWebPages() == null
                    || CollectionUtils.isEmpty(resp.getData().getWebPages().getValue())) {
                return "未搜索到相关结果。";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("搜索结果：\n\n");
            int i = 1;
            for (BochaWebSearchResponse.WebPageValue v : resp.getData().getWebPages().getValue()) {
                sb.append(i++).append(". ").append(v.getName()).append("\n")
                  .append("   来源：").append(v.getSiteName()).append("\n")
                  .append("   链接：").append(v.getUrl()).append("\n")
                  .append("   摘要：")
                  .append(v.getSummary() != null ? v.getSummary() : v.getSnippet())
                  .append("\n\n");
            }
            return sb.toString();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return "搜索工具调用失败";
        }



    }
}