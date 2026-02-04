package com.ai.aigent.tools;

import org.junit.jupiter.api.Test;

class TerminalOpToolTest {

    @Test
    void execTerminal() {
        TerminalOpTool tool = new TerminalOpTool();
        String commend="dir";
        String s = tool.execTerminal(commend);
        System.out.println(s);
    }
}