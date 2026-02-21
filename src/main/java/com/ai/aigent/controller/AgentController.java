package com.ai.aigent.controller;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ai.aigent.agent.Manus;
import com.ai.aigent.agent.model.AgentMessagePayload;
import com.ai.aigent.agent.model.AgentSessionRegistry;
import com.ai.aigent.app.CodeApp;
import com.ai.aigent.constant.Result;
import com.ai.aigent.entity.VO.AgentMessageVO;
import com.ai.aigent.entity.VO.AgentSessionVO;
import com.ai.aigent.entity.domin.AgentMessage;
import com.ai.aigent.entity.domin.AgentSession;
import com.ai.aigent.entity.domin.AnonymousUser;
import com.ai.aigent.entity.dto.AgentSendReq;
import com.ai.aigent.service.*;
import com.ai.aigent.utils.JWTUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/agent")
public class AgentController {
    @Resource
    private CodeApp codeApp;

//    private Manus manus;
    @Resource
    private ToolCallback[]toolCallBacks;
    @Resource
    @Qualifier("dashScopeChatModel")
    private ChatModel dashscopeChatModel;
    @Resource
    private ConversationService conversationService;
    @Autowired
    private UserUsageDailyService userUsageDailyService;
    @Autowired
    private UsersService usersService;
    @Autowired
    private AnonymousUserService anonymousUserService;
    @Autowired
    private UserUsageDailyService dailyService;
    private final ApplicationContext applicationContext;
    @Autowired
    private AgentSessionService agentSessionService;
    @Autowired
    private AgentMessageService agentMessageService;
    @PostMapping("/session")
    public Result<AgentSessionVO> createSession(String message, HttpServletRequest request, String SessionId) {
        AgentSession session = new AgentSession();
        String userId =request.getHeader("Authorization");
        if (userId == null) {
            throw new IllegalArgumentException("用户未登录");
        }
        session.setUserId(Long.parseLong(JWTUtils.getUserId(userId)));
        session.setTitle(codeApp.genTitle(message));
        session.setCreateTime(new Date());
        session.setUpdateTime(new Date());
        agentSessionService.save(session);
        AgentSessionVO vo = new AgentSessionVO();
        vo.setSessionId(session.getId());
        vo.setTitle(session.getTitle());
        vo.setCreateTime(session.getCreateTime());
        return Result.success(vo);
    }
      @GetMapping("/messages")
    public Result<List<AgentMessageVO>> listMessages(
            @RequestParam Long sessionId,HttpServletRequest request) {
        String userId =request.getHeader("Authorization");
        if (userId == null) {
            throw new IllegalArgumentException("用户未登录");
        }
        AgentSession session = agentSessionService.getById(sessionId);
        if (session == null || !session.getUserId().equals(Long.parseLong(JWTUtils.getUserId(userId)))) {
            return Result.error("无权访问");
        }
        List<AgentMessage> messages = agentMessageService.lambdaQuery()
                .eq(AgentMessage::getSessionId, sessionId)
                .orderByAsc(AgentMessage::getCreatedAt)
                .list();
        List<AgentMessageVO> vos = messages.stream().map(m -> {
            AgentMessageVO vo = new AgentMessageVO();
            vo.setId(m.getId());
            vo.setType(m.getType());
            vo.setCreatedAt(m.getCreatedAt());
            vo.setPayload(m.getPayload());
            return vo;
        }).collect(Collectors.toList());
        return Result.success(vos);
    }
    private void saveMessage(Long sessionId, String type, String content,
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
    public Result cancel(@RequestParam Long sessionId) {
        agentSessionRegistry.cancel(sessionId);
        return Result.success();
    }
    @GetMapping("/sessions")
    public Result<List<AgentSessionVO>> listSessions(HttpServletRequest request) {
         String authHeader = request.getHeader("Authorization");
         if(StringUtils.isBlank(authHeader) ||authHeader.isEmpty()||!JWTUtils.validateToken(authHeader))
         {
             return Result.error("请先登录");
         }
        List<AgentSession> sessions = agentSessionService.lambdaQuery()
                .eq(AgentSession::getUserId, Long.parseLong(JWTUtils.getUserId(authHeader)))
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
         String token = agentSendReq.getToken();
         Long sessionId = agentSendReq.getSessionId();
         String prompt = agentSendReq.getMessage();
         String uid;
         String type;
         saveMessage(sessionId, "user", prompt, null, null, 0);
         agentSessionService.lambdaUpdate()
                .eq(AgentSession::getId, sessionId)
                .set(AgentSession::getLastMessage, prompt)
                .set(AgentSession::getUpdateTime, new Date())
                .update();
         if(token!=null && JWTUtils.validateToken(token)){
            uid = JWTUtils.getUserId(token);
            type = "user";
         }
        else {
            AnonymousUser user = anonymousUserService.getById(req);
            uid = user.getAnonId();
            type = "anon";
        }
        dailyService.checkAndConsume(uid,type);
        // 4. 每次取新的 prototype bean，避免状态污染
        Manus manus = applicationContext.getBean(Manus.class);
        // 6. 启动流
        return manus.runStream(prompt,sessionId,agentSendReq.getAttachments());
    }
    @Transactional
    @PostMapping("/delete")
    public Result delete(@RequestParam Long sessionId) {
        AgentSession session = agentSessionService.getById(sessionId);
        if (session == null) {
            return Result.error("会话不存在");
        }
        // 删除会话
        agentSessionService.removeById(sessionId);
        // 删除消息
        agentMessageService.lambdaUpdate()
                .eq(AgentMessage::getSessionId, sessionId)
                .remove();
        return Result.success();
    }
}
