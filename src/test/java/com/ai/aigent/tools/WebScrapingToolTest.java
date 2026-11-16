package com.ai.aigent.tools;

import org.junit.jupiter.api.Test;


class WebScrapingToolTest {

    @Test
    void fetchWebPage() {
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        String s = webScrapingTool.fetchWebPage("https://www.codefather.cn");
        System.out.println(s);
    }

    @Test
    void testFetchWebPage() {

    }
}