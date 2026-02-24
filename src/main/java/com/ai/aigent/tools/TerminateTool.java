package com.ai.aigent.tools;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;

import java.util.Collections;

public class TerminateTool {
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Tool(description = """
            Terminate the interaction when the request is met oR if the assistant cannot proceed further with the task.
            "When you have finished all the tasks,call this tool to end the work.
            """)
    public String doTerminateTool()
    {
        return JSONUtil.toJsonStr(Collections.singletonMap("success",true));
    }

}
