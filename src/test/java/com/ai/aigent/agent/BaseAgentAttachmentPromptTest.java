package com.ai.aigent.agent;

import com.ai.aigent.agent.model.AgentAttachment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseAgentAttachmentPromptTest {
    private static class TestAgent extends BaseAgent {
        @Override
        public String step() {
            return "";
        }
    }

    @Test
    void includesRecentAttachmentsAndLimitsPromptTo1024Characters() {
        TestAgent agent = new TestAgent();
        agent.setSessionId("session-1");
        List<AgentAttachment> attachments = new ArrayList<>();
        for (long id = 1; id <= 30; id++) {
            AgentAttachment attachment = new AgentAttachment();
            attachment.setId(id);
            attachment.setFileName("file-" + id + ".txt");
            attachment.setFileType("text/plain");
            attachment.setFileSize(100L);
            attachments.add(attachment);
        }
        agent.setSessionAttachments(attachments);

        String prompt = agent.formatSessionAttachments();

        assertEquals(1024, prompt.length());
        assertTrue(prompt.contains("file-30.txt"));
        assertTrue(prompt.contains("text/plain"));
        assertTrue(prompt.contains("100 字节"));
        assertTrue(prompt.contains("/data/myagent/attachments/session-1/file-30.txt"));
    }

    @Test
    void noAttachmentsProducesNoExtraPrompt() {
        assertEquals("", new TestAgent().formatSessionAttachments());
    }
}
