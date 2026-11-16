package com.ai.aigent.controller;

import cn.hutool.json.JSONUtil;
import com.ai.aigent.agent.Manus;
import com.ai.aigent.agent.model.AgentMessagePayload;
import com.ai.aigent.agent.model.AgentSessionRegistry;
import com.ai.aigent.app.SpringAiCode;
import com.ai.aigent.constant.Result;
import com.ai.aigent.entity.VO.AgentMessageVO;
import com.ai.aigent.entity.VO.AgentSessionVO;
import com.ai.aigent.entity.domain.AgentMessage;
import com.ai.aigent.entity.domain.AgentSession;
import com.ai.aigent.entity.dto.AgentSendReq;
import com.ai.aigent.exception.BusinessException;
import com.ai.aigent.service.*;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/agent")
public class AgentController {
    @Resource
    private SpringAiCode springAiCode;

    @Autowired
    private UsersService usersService;
    @Autowired
    private AnonymousUserService anonymousUserService;
    @Autowired
    private UserUsageDailyService dailyService;
    private final ObjectProvider<Manus> manusProvider;
    @Autowired
    private AgentSessionService agentSessionService;
    @Autowired
    private AgentMessageService agentMessageService;
    @PostMapping("/session")
    public Result<AgentSessionVO> createSession(String message, HttpServletRequest request, String SessionId) {
        if (SessionId == null || SessionId.isBlank()) {
            throw BusinessException.badRequest("会话 ID 不能为空");
        }
        AgentSession session = new AgentSession();
        String userId = requireUserId(request);
        if (agentSessionService.getById(SessionId) != null) {
            throw BusinessException.conflict("会话已存在");
        }
        session.setUserId(userId);
        session.setTitle(springAiCode.genTitle(message));
        session.setCreateTime(new Date());
        session.setUpdateTime(new Date());
        session.setId(SessionId);
        if (!agentSessionService.save(session)) {
            throw BusinessException.internalError("创建会话失败");
        }
        AgentSessionVO vo = new AgentSessionVO();
        vo.setSessionId(session.getId());
        vo.setTitle(session.getTitle());
        vo.setCreateTime(session.getCreateTime());
        return Result.success(vo);
    }
    @GetMapping("/messages")
    public Result<List<AgentMessageVO>> listMessages(
            @RequestParam String sessionId,HttpServletRequest request) {
        requireOwnedSession(sessionId, request);
        List<AgentMessage> messages = agentMessageService.lambdaQuery()
                .eq(AgentMessage::getSessionId, sessionId)
                .orderByAsc(AgentMessage::getCreatedAt)
                .list();
        List<AgentMessageVO> vos = messages.stream().map(m -> {
            AgentMessageVO vo = new AgentMessageVO();
            vo.setId(m.getId().toString());
            vo.setType(m.getType());
            vo.setTokens(m.getTokens());
            vo.setCost(m.getCost());
            vo.setCachedTokens(m.getCachedTokens());
            vo.setCompletionTokens(m.getCompletionTokens());
            vo.setPromptTokens(m.getPromptTokens());
            vo.setThinkingTime(m.getThinkingTime());
            vo.setCreatedAt(m.getCreatedAt());
            vo.setPayload(m.getPayload());
            return vo;
        }).collect(Collectors.toList());
        return Result.success(vos);
    }
    private void saveMessage(String sessionId, String type, String content,
                             String reasoning, List<AssistantMessage.ToolCall> toolCalls, int tokens) {
        AgentMessagePayload payload = new AgentMessagePayload();
        payload.setContent(content);
        payload.setReasoning(reasoning);
        AgentMessage msg = new AgentMessage();
        msg.setSessionId(sessionId);
        msg.setType(type);
        msg.setTokens(tokens);
        msg.setCreatedAt(new Date());
        msg.setPayload(JSONUtil.toJsonStr(payload));
        agentMessageService.save(msg);
    }
    @Autowired
    private AgentSessionRegistry agentSessionRegistry;
    @PostMapping("/cancel")
    public Result cancel(@RequestParam String sessionId, HttpServletRequest request) {
        requireOwnedSession(sessionId, request);
        agentSessionRegistry.cancel(sessionId);
        return Result.success();
    }
    @GetMapping("/sessions")
    public Result<List<AgentSessionVO>> listSessions(HttpServletRequest request) {
        String userId = requireUserId(request);
        List<AgentSession> sessions = agentSessionService.lambdaQuery()
                .eq(AgentSession::getUserId, userId)
                .orderByDesc(AgentSession::getUpdateTime)
                .list();
        List<AgentSessionVO> vos = sessions.stream().map(s -> {
            AgentSessionVO vo = new AgentSessionVO();
            vo.setSessionId(s.getId());
            vo.setTitle(s.getTitle());
            vo.setLastMessage(s.getLastMessage());
            vo.setUpdateTime(s.getUpdateTime());
            return vo;
        }).collect(Collectors.toList());
        return Result.success(vos);
    }
    @PostMapping(value = "/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter run(@RequestBody AgentSendReq agentSendReq,HttpServletRequest req) {
         String sessionId = agentSendReq.getSessionId();
         String prompt = agentSendReq.getMessage();
         String userId = requireUserId(req);
         requireOwnedSession(sessionId, userId);
         if (prompt == null || prompt.isBlank()) {
             throw BusinessException.badRequest("消息不能为空");
         }
         Manus manus = manusProvider.getObject();
         if (!agentSessionRegistry.register(sessionId, manus)) {
             throw BusinessException.conflict("该会话已有正在执行的任务");
         }
         try {
             saveMessage(sessionId, "user", prompt, null, null, 0);
             agentSessionService.lambdaUpdate()
                .eq(AgentSession::getId, sessionId)
                .set(AgentSession::getLastMessage, prompt)
                .set(AgentSession::getUpdateTime, new Date())
                .update();

             String type = req.getHeader("Authorization") == null ? "anon" : "user";
             dailyService.checkAndConsume(userId, type);
             return manus.agentRun(prompt, sessionId, agentSendReq.getAttachments(), userId);
         } catch (RuntimeException | Error e) {
             agentSessionRegistry.remove(sessionId, manus);
             throw e;
         }
    }
    @Transactional
    @PostMapping("/delete")
    public Result delete(@RequestParam String sessionId, HttpServletRequest request) {
        requireOwnedSession(sessionId, request);
        if (agentSessionRegistry.isRunning(sessionId)) {
            throw BusinessException.conflict("任务执行中，请先取消");
        }
        // 删除会话
        agentSessionService.removeById(sessionId);
        // 删除消息
        agentMessageService.lambdaUpdate()
                .eq(AgentMessage::getSessionId, sessionId)
                .remove();
        return Result.success();
    }

    private String requireUserId(HttpServletRequest request) {
        String userId;
        try {
            userId = usersService.getLoginUser(request);
        } catch (IllegalArgumentException e) {
            throw BusinessException.unauthorized("请先登录");
        }
        if (userId == null || userId.isBlank()) {
            throw BusinessException.unauthorized("请先登录");
        }
        return userId;
    }

    private AgentSession requireOwnedSession(String sessionId, HttpServletRequest request) {
        return requireOwnedSession(sessionId, requireUserId(request));
    }

    private AgentSession requireOwnedSession(String sessionId, String userId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw BusinessException.badRequest("会话 ID 不能为空");
        }
        AgentSession session = agentSessionService.getById(sessionId);
        if (session == null) {
            throw BusinessException.notFound("会话不存在");
        }
        if (!Objects.equals(session.getUserId(), userId)) {
            throw BusinessException.forbidden("无权访问该会话");
        }
        return session;
    }
}
