package com.ai.aigent.utils;


import com.ai.aigent.entity.ToolContext;

public class ToolContextHolder {
    private static final ThreadLocal<ToolContext> CTX = new ThreadLocal<>();

    public static void set(ToolContext context) {
        CTX.set(context);
    }

    public static ToolContext get() {
        return CTX.get();
    }

    public static void clear() {
        CTX.remove();
    }

}
