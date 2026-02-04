package com.ai.aigent.tools;

import org.junit.jupiter.api.Test;

class WebSearchToolTest {

    @Test
    void searchWeb() {
        WebSearchTool webSearchTool = new WebSearchTool();
        String s = webSearchTool.searchWeb("程序员");
        System.out.println(s);
    }
}