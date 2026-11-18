package com.ai.aigent.controller;

import com.ai.aigent.agent.Manus;
import com.ai.aigent.agent.model.AgentSessionRegistry;
import com.ai.aigent.entity.domain.AgentSession;
import com.ai.aigent.entity.domain.AgentMessage;
import com.ai.aigent.entity.domain.AiAttachment;
import com.ai.aigent.entity.VO.AgentMessagesVO;
import com.ai.aigent.service.AiAttachmentService;
import org.mockito.ArgumentCaptor;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;
import com.ai.aigent.entity.dto.AgentSendReq;
import com.ai.aigent.exception.BusinessException;
import com.ai.aigent.service.AgentMessageService;
import com.ai.aigent.service.AgentSessionService;
import com.ai.aigent.service.UserUsageDailyService;
import com.ai.aigent.service.UsersService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AgentControllerPhaseZeroTest {
    private AgentController controller;
    private UsersService usersService;
    private AgentSessionService sessionService;
    private AgentMessageService messageService;
    private AiAttachmentService attachmentService;
    private UserUsageDailyService dailyService;
    private AgentSessionRegistry registry;
    private ObjectProvider<Manus> provider;
    private HttpServletRequest request;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        usersService = mock(UsersService.class);
        sessionService = mock(AgentSessionService.class);
        messageService = mock(AgentMessageService.class);
        attachmentService = mock(AiAttachmentService.class);
        dailyService = mock(UserUsageDailyService.class);
        registry = mock(AgentSessionRegistry.class);
        provider = mock(ObjectProvider.class);
        request = mock(HttpServletRequest.class);
        controller = new AgentController(provider);
        ReflectionTestUtils.setField(controller, "usersService", usersService);
        ReflectionTestUtils.setField(controller, "agentSessionService", sessionService);
        ReflectionTestUtils.setField(controller, "agentMessageService", messageService);
        ReflectionTestUtils.setField(controller, "aiAttachmentService", attachmentService);
        ReflectionTestUtils.setField(controller, "dailyService", dailyService);
        ReflectionTestUtils.setField(controller, "agentSessionRegistry", registry);
        when(usersService.getLoginUser(request)).thenReturn("owner-a");
    }

    @Test
    void otherUsersCannotReadOrCancelSession() {
        AgentSession session = new AgentSession();
        session.setUserId("owner-b");
        when(sessionService.getById("42")).thenReturn(session);

        BusinessException readError = assertThrows(BusinessException.class,
                () -> controller.listMessages("42", request));
        BusinessException cancelError = assertThrows(BusinessException.class,
                () -> controller.cancel("42", request));

        assertEquals(403, readError.getCode());
        assertEquals(403, cancelError.getCode());
        verifyNoInteractions(messageService, attachmentService, registry);
    }

    @Test
    void concurrentRunIsRejectedBeforeUsageOrMessageWrites() {
        AgentSession session = new AgentSession();
        session.setUserId("owner-a");
        when(sessionService.getById("42")).thenReturn(session);
        Manus manus = mock(Manus.class);
        when(provider.getObject()).thenReturn(manus);
        when(registry.register("42", manus)).thenReturn(false);
        AgentSendReq body = new AgentSendReq();
        body.setSessionId("42");
        body.setMessage("hi");
        body.setRunId(101L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.run(body, request));

        assertEquals(409, error.getCode());
        verifyNoInteractions(dailyService, messageService);
    }

    @Test
    void missingOrInvalidRunIdIsRejectedBeforeStartingAgent() {
        AgentSession session = new AgentSession();
        session.setUserId("owner-a");
        when(sessionService.getById("42")).thenReturn(session);
        AgentSendReq body = new AgentSendReq();
        body.setSessionId("42");
        body.setMessage("hi");
        for (Long runId : new Long[]{null, 0L, -1L}) {
            body.setRunId(runId);
            assertEquals(400, assertThrows(BusinessException.class,
                    () -> controller.run(body, request)).getCode());
        }
        verifyNoInteractions(provider, registry, messageService, dailyService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void frontendRunIdIsSavedAndPassedToAgent() {
        AgentSession session = new AgentSession();
        session.setUserId("owner-a");
        when(sessionService.getById("42")).thenReturn(session);
        Manus manus = mock(Manus.class);
        when(provider.getObject()).thenReturn(manus);
        when(registry.register("42", manus)).thenReturn(true);
        LambdaQueryChainWrapper<AgentMessage> query = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        when(query.eq(any(), any())).thenReturn(query);
        when(messageService.lambdaQuery()).thenReturn(query);
        LambdaUpdateChainWrapper<AgentSession> update = mock(LambdaUpdateChainWrapper.class, RETURNS_SELF);
        when(update.eq(any(), any())).thenReturn(update);
        when(update.set(any(), any())).thenReturn(update);
        when(sessionService.lambdaUpdate()).thenReturn(update);
        SseEmitter emitter = new SseEmitter();
        when(manus.agentRun("hi", "42", 9007199254740993L, List.of(7L), "owner-a"))
                .thenReturn(emitter);
        AgentSendReq body = new AgentSendReq();
        body.setSessionId("42");
        body.setMessage("hi");
        body.setRunId(9007199254740993L);
        body.setAttachments(List.of(7L));

        org.junit.jupiter.api.Assertions.assertSame(emitter, controller.run(body, request));

        ArgumentCaptor<AgentMessage> saved = ArgumentCaptor.forClass(AgentMessage.class);
        verify(messageService).save(saved.capture());
        assertEquals(body.getRunId(), saved.getValue().getRunId());
        assertEquals("42", saved.getValue().getSessionId());
        assertEquals("user", saved.getValue().getType());
        verify(manus).agentRun("hi", "42", body.getRunId(), List.of(7L), "owner-a");
    }

    @Test
    @SuppressWarnings("unchecked")
    void reusedRunIdIsRejectedAndRegistrationIsReleased() {
        AgentSession session = new AgentSession();
        session.setUserId("owner-a");
        when(sessionService.getById("42")).thenReturn(session);
        Manus manus = mock(Manus.class);
        when(provider.getObject()).thenReturn(manus);
        when(registry.register("42", manus)).thenReturn(true);
        LambdaQueryChainWrapper<AgentMessage> query = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        when(query.eq(any(), any())).thenReturn(query);
        when(messageService.lambdaQuery()).thenReturn(query);
        when(query.exists()).thenReturn(true);
        AgentSendReq body = new AgentSendReq();
        body.setSessionId("42");
        body.setMessage("hi");
        body.setRunId(101L);

        assertEquals(409, assertThrows(BusinessException.class,
                () -> controller.run(body, request)).getCode());

        verify(messageService, never()).save(any(AgentMessage.class));
        verifyNoInteractions(dailyService, manus);
        verify(registry).remove("42", manus);
    }


    @Test
    @SuppressWarnings("unchecked")
    void listMessagesReturnsAttachmentDetailsGroupedByRunId() {
        AgentSession session = new AgentSession();
        session.setUserId("owner-a");
        when(sessionService.getById("42")).thenReturn(session);
        AgentMessage first = new AgentMessage();
        first.setId(1L);
        first.setRunId(9007199254740993L);
        first.setType("user");
        AgentMessage second = new AgentMessage();
        second.setId(2L);
        second.setRunId(9007199254740994L);
        second.setType("user");
        LambdaQueryChainWrapper<AgentMessage> messageQuery = mock(LambdaQueryChainWrapper.class);
        when(messageService.lambdaQuery()).thenReturn(messageQuery);
        when(messageQuery.eq(any(), any())).thenReturn(messageQuery);
        when(messageQuery.orderByAsc(any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class))).thenReturn(messageQuery);
        when(messageQuery.list()).thenReturn(List.of(first, second));
        AiAttachment attachment = new AiAttachment();
        attachment.setId(9007199254740995L);
        attachment.setChatId("9007199254740993");
        attachment.setFileName("report.pdf");
        attachment.setFileUrl("https://example.com/report.pdf");
        attachment.setFileSize(1024L);
        attachment.setFileType("application/pdf");
        LambdaQueryChainWrapper<AiAttachment> attachmentQuery = mock(LambdaQueryChainWrapper.class);
        when(attachmentService.lambdaQuery()).thenReturn(attachmentQuery);
        when(attachmentQuery.eq(any(), any())).thenReturn(attachmentQuery);
        when(attachmentQuery.in(any(), any(java.util.Collection.class))).thenReturn(attachmentQuery);
        when(attachmentQuery.orderByAsc(any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class))).thenReturn(attachmentQuery);
        when(attachmentQuery.list()).thenReturn(List.of(attachment));

        AgentMessagesVO data = controller.listMessages("42", request).getData();

        assertEquals(2, data.getMessages().size());
        var files = data.getAttachmentsByRunId().get("9007199254740993");
        assertEquals(1, files.size());
        assertEquals("9007199254740995", files.getFirst().getId());
        assertEquals("report.pdf", files.getFirst().getFileName());
        assertEquals("https://example.com/report.pdf", files.getFirst().getFileUrl());
        assertEquals(1024L, files.getFirst().getFileSize());
        assertEquals("application/pdf", files.getFirst().getFileType());
        assertEquals(null, data.getAttachmentsByRunId().get("9007199254740994"));
    }

}
