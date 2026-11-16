package com.ai.aigent.tools;

import com.ai.aigent.entity.VO.BochaWebSearchResponse;
import com.ai.aigent.utils.BochaWebSearchClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class WebSearchToolTest {
    @Autowired
    private WebSearchTool bochaWebSearchTool;
    @Autowired
    private BochaWebSearchClient bochaWebSearchClient;
    @Test
    void searchWeb() {

        BochaWebSearchResponse mockResp = new BochaWebSearchResponse();
        BochaWebSearchResponse.Datas data = new BochaWebSearchResponse.Datas();
        BochaWebSearchResponse.WebPages webPages = new BochaWebSearchResponse.WebPages();
        BochaWebSearchResponse.WebPageValue v = new BochaWebSearchResponse.WebPageValue();
        v.setName("阿里巴巴发布2024年ESG报告");
        v.setUrl("https://www.alibabagroup.com/xxx");
        v.setSnippet("阿里巴巴发布了2024年ESG报告，介绍了减碳与社会责任...");
        v.setSiteName("alibabagroup.com");
        webPages.setValue(List.of(v));
        data.setWebPages(webPages);
        mockResp.setData(data);
        String result = bochaWebSearchTool.webSearch(
                "阿里巴巴2024年的ESG报告",
                "noLimit",
                true,
                3
        );

        System.out.println(result);

        Assertions.assertTrue(result.contains("阿里巴巴发布2024年ESG报告"));
    }
}