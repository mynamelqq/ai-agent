package com.ai.aigent.tools;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class ToolsRegistration {
    @Value("${app.cos-enabled:true}")
    private boolean cosEnabled;

    private final WebSearchTool webSearchTool;
    private final ImageGenerateTool imageGenerateTool;
    private final FileOperationTool fileOperationTool;
    private final DownloadResourceTool downloadResourceTool;

    public ToolsRegistration(WebSearchTool webSearchTool, ImageGenerateTool imageGenerateTool,
                             FileOperationTool fileOperationTool, DownloadResourceTool downloadResourceTool) {
        this.webSearchTool = webSearchTool;
        this.imageGenerateTool = imageGenerateTool;
        this.fileOperationTool = fileOperationTool;
        this.downloadResourceTool = downloadResourceTool;
    }

    @Bean
    public List<ToolCallback> ToolCallback() {
        return Arrays.asList(callbacks());
    }

    @Bean
    public ToolCallback[] toolCallback() {
        return callbacks();
    }

    private ToolCallback[] callbacks() {
        TimeTool timeTool = new TimeTool();
        TerminalOpTool terminalOperationTool = new TerminalOpTool();
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        List<ToolCallback> callbacks = new ArrayList<>(Arrays.asList(
                ToolCallbacks.from(timeTool, terminalOperationTool, imageGenerateTool,
                        webSearchTool, webScrapingTool)));
        if (cosEnabled) {
            callbacks.addAll(Arrays.asList(ToolCallbacks.from(fileOperationTool, downloadResourceTool)));
        }
        return callbacks.toArray(ToolCallback[]::new);
    }
}
