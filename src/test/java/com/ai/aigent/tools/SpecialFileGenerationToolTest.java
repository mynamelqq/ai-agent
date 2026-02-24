package com.ai.aigent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class SpecialFileGenerationToolTest {

    @Test
    void generatePDF() {
        SpecialFileGenerationTool tool = new SpecialFileGenerationTool();
        String fileName = "编程导航原创项目.pdf";
        String content = "编程导航原创项目 https://www.codefather.cn";
        String result = tool.generatePDF(content, fileName);
        assertNotNull(result);
    }
}