package com.ai.aigent.tools;

import lombok.AllArgsConstructor;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@AllArgsConstructor
@Configuration
public class ToolsRegistration {
    private WebSearchTool webSearchTool;
    private FileOperationTool fileOperationTool;
    @Bean
    public List<ToolCallback> ToolCallback() {//对话工具调用
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        DownloadResourceTool resourceDownloadTool = new DownloadResourceTool();
        TerminalOpTool terminalOperationTool = new TerminalOpTool();
//        SpecialFileGenerationTool specialFileGenerationTool = new SpecialFileGenerationTool();
        TimeTool timeTool=new TimeTool();
        TerminateTool terminateTool=new TerminateTool();
        return Arrays.stream(ToolCallbacks.from(timeTool,
                terminateTool,
            fileOperationTool,
            webSearchTool,
            webScrapingTool,
            resourceDownloadTool,
            terminalOperationTool
//                specialFileGenerationTool
        )).toList();
    }
    @Bean
    public ToolCallback[] toolCallback() {//智能体工具调用
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        DownloadResourceTool resourceDownloadTool = new DownloadResourceTool();
        TerminalOpTool terminalOperationTool = new TerminalOpTool();
//        SpecialFileGenerationTool specialFileGenerationTool = new SpecialFileGenerationTool();
        TimeTool timeTool=new TimeTool();
        TerminateTool terminateTool=new TerminateTool();
        return ToolCallbacks.from(timeTool,
                terminateTool,
            fileOperationTool,
            webSearchTool,
            webScrapingTool,
            resourceDownloadTool,
            terminalOperationTool
        );
    }
}
