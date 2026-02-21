package com.ai.aigent.tools;

import lombok.AllArgsConstructor;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@AllArgsConstructor
@Configuration
public class ToolsRegistration {
    private WebSearchTool webSearchTool;
    @Bean
    public ToolCallback[] toolCallback() {
        FileOperationTool fileOperationTool = new FileOperationTool();
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        DownloadResourceTool resourceDownloadTool = new DownloadResourceTool();
        TerminalOpTool terminalOperationTool = new TerminalOpTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        TimeTool timeTool=new TimeTool();
        TerminateTool terminateTool=new TerminateTool();
        return ToolCallbacks.from(timeTool,
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
