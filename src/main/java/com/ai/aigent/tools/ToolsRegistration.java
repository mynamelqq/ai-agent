package com.ai.aigent.tools;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolsRegistration {
    @Bean
    public ToolCallback[] toolCallback() {
        FileOperationTool fileOperationTool = new FileOperationTool();
        WebSearchTool webSearchTool = new WebSearchTool();
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        DownloadResourceTool resourceDownloadTool = new DownloadResourceTool();
        TerminalOpTool terminalOperationTool = new TerminalOpTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        TerminateTool terminateTool=new TerminateTool();
        return ToolCallbacks.from(
                terminateTool,
            fileOperationTool,
            webSearchTool,
            webScrapingTool,
            resourceDownloadTool,
            terminalOperationTool,
            pdfGenerationTool
        );
    }
}
