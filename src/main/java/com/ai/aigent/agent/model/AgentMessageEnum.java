package com.ai.aigent.agent.model;

public enum AgentMessageEnum {
    TOOL("tool"),
    ASSISTANT("assistant"),
    TOOL_RESULT("tool_result"),
    USER("user"),
    ;
    private String value;
    AgentMessageEnum(String value) {
        this.value = value;
    }
    public String getValue() {
        return value;
    }
    public void setValue(String value) {
        this.value = value;
    }

}
