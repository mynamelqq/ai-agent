package com.ai.aigent.tools;

import com.ai.aigent.tools.sandbox.TerminalOpTool;
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

    private final com.ai.aigent.tools.sandbox.ArtifactUploadTool artifact;
    private final TerminalOpTool terminal;
    private final com.ai.aigent.tools.sandbox.PythonExecutorTool python;
    private final WebSearchTool webSearchTool;
    private final WebScrapingTool webScrapingTool;
    private final ImageGenerateTool imageGenerateTool;
    private final FileOperationTool fileOperationTool;
    private final DownloadResourceTool downloadResourceTool;
    public ToolsRegistration(WebSearchTool webSearchTool, ImageGenerateTool imageGenerateTool,
                             FileOperationTool fileOperationTool, DownloadResourceTool downloadResourceTool,
                             WebScrapingTool webScrapingTool, com.ai.aigent.tools.sandbox.ArtifactUploadTool artifact, TerminalOpTool terminal, com.ai.aigent.tools.sandbox.PythonExecutorTool python) {
        this.artifact = artifact;
        this.terminal = terminal; this.python = python;
        this.webSearchTool = webSearchTool;
        this.imageGenerateTool = imageGenerateTool;
        this.fileOperationTool = fileOperationTool;
        this.webScrapingTool=webScrapingTool;
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


        List<ToolCallback> callbacks = new ArrayList<>(Arrays.asList(
                ToolCallbacks.from(timeTool, terminal, python, imageGenerateTool,
                        webSearchTool, webScrapingTool)));
        if (cosEnabled) {
            callbacks.addAll(Arrays.asList(ToolCallbacks.from(fileOperationTool, downloadResourceTool, artifact)));
        }
        return callbacks.toArray(ToolCallback[]::new);
    }
}
