package com.ai.aigent.agent;

import com.ai.aigent.agent.model.AgentEvent;
import com.ai.aigent.agent.model.AgentMessagePayload;
import com.ai.aigent.controller.UploadController;
import com.ai.aigent.entity.VO.AgentMessageVO;
import com.ai.aigent.entity.domain.AgentMessage;
import com.ai.aigent.entity.domain.AiAttachment;
import com.ai.aigent.entity.dto.AgentSendReq;
import com.ai.aigent.entity.dto.UploadFileDTO;
import com.ai.aigent.service.AiAttachmentService;
import com.ai.aigent.service.UsersService;
import com.ai.aigent.service.impl.AgentMessageServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentRunIdTest {
    private static final long RUN_ID = 9007199254740993L;

    private static class TestAgent extends BaseAgent {
        @Override
        public String step() {
            return "";
        }
    }

    @Test
    void largeFrontendIdRoundTripsWithoutPrecisionLoss() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AgentSendReq request = mapper.readValue("{\"runId\":\"" + RUN_ID + "\"}", AgentSendReq.class);
        assertEquals(RUN_ID, request.getRunId());
        AgentMessageVO message = new AgentMessageVO();
        message.setRunId(request.getRunId());
        assertEquals(Long.toString(RUN_ID), mapper.readTree(mapper.writeValueAsString(message))
                .get("runId").textValue());
    }

    @Test
    void allAgentMessageTypesPersistTheSuppliedRunId() {
        AgentMessageServiceImpl service = spy(new AgentMessageServiceImpl());
        doReturn(true).when(service).save(any(AgentMessage.class));
        for (String type : List.of("assistant", "tool", "tool_result")) {
            AgentMessage message = service.saveAgentMessage("session", RUN_ID, type,
                    new AgentMessagePayload(), 0, 0L, 0L, 0, 0, 0);
            assertEquals(RUN_ID, message.getRunId());
            assertEquals("session", message.getSessionId());
            assertEquals(type, message.getType());
        }
    }

    @Test
    void sseAddsRunIdWhilePreservingExistingMetadata() throws Exception {
        TestAgent agent = new TestAgent();
        SseEmitter emitter = mock(SseEmitter.class);
        agent.setEmitter(emitter);
        agent.setRunId(RUN_ID);
        AgentEvent event = AgentEvent.stepStart(1);

        agent.emit(event);

        assertEquals(Map.of("step", 1, "runId", Long.toString(RUN_ID)), event.getMeta());
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void attachmentFromAnotherRunIsRejectedBeforeReadingFile() {
        TestAgent agent = new TestAgent();
        agent.setSessionId("session");
        agent.setUserId("123");
        agent.setRunId(RUN_ID);
        AiAttachmentService attachments = mock(AiAttachmentService.class);
        agent.setAttachmentService(attachments);
        AiAttachment attachment = new AiAttachment();
        attachment.setId(7L);
        attachment.setUserId(123L);
        attachment.setConservationId("session");
        attachment.setChatId("101");
        when(attachments.listByIds(List.of(7L))).thenReturn(List.of(attachment));

        assertThrows(IllegalArgumentException.class,
                () -> ReflectionTestUtils.invokeMethod(agent, "loadAttachments", List.of(7L)));
    }

    @Test
    void uploadConfirmationStoresRunIdAndOwner() {
        UploadController controller = new UploadController();
        AiAttachmentService attachments = mock(AiAttachmentService.class);
        UsersService users = mock(UsersService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        ReflectionTestUtils.setField(controller, "attachmentService", attachments);
        ReflectionTestUtils.setField(controller, "usersService", users);
        when(users.getLoginUser(request)).thenReturn("123");
        UploadFileDTO body = new UploadFileDTO();
        body.setRunId(RUN_ID);
        body.setConservation_id("session");
        body.setChat_id("old-message-id");

        controller.confirmUpload(body, request);

        ArgumentCaptor<AiAttachment> saved = ArgumentCaptor.forClass(AiAttachment.class);
        verify(attachments).save(saved.capture());
        assertEquals(Long.toString(RUN_ID), saved.getValue().getChatId());
        assertEquals("session", saved.getValue().getConservationId());
        assertEquals(123L, saved.getValue().getUserId());
    }
}
