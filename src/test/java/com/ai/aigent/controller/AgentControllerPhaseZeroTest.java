package com.ai.aigent.controller;

import com.ai.aigent.agent.Manus;
import com.ai.aigent.agent.model.AgentSessionRegistry;
import com.ai.aigent.entity.domain.AgentSession;
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
        dailyService = mock(UserUsageDailyService.class);
        registry = mock(AgentSessionRegistry.class);
        provider = mock(ObjectProvider.class);
        request = mock(HttpServletRequest.class);
        controller = new AgentController(provider);
        ReflectionTestUtils.setField(controller, "usersService", usersService);
        ReflectionTestUtils.setField(controller, "agentSessionService", sessionService);
        ReflectionTestUtils.setField(controller, "agentMessageService", messageService);
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
        verifyNoInteractions(messageService, registry);
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

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.run(body, request));

        assertEquals(409, error.getCode());
        verifyNoInteractions(dailyService, messageService);
    }
}
