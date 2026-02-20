package com.ai.aigent.controller;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ai.aigent.agent.Manus;
import com.ai.aigent.agent.model.AgentMessagePayload;
import com.ai.aigent.app.CancelTokenRegistry;
import com.ai.aigent.app.CodeApp;
import com.ai.aigent.constant.Result;
import com.ai.aigent.entity.VO.AgentMessageVO;
import com.ai.aigent.entity.VO.AgentSessionVO;
import com.ai.aigent.entity.VO.ChatResult;
import com.ai.aigent.entity.domin.AgentMessage;
import com.ai.aigent.entity.domin.AgentSession;
import com.ai.aigent.entity.domin.AnonymousUser;
import com.ai.aigent.entity.domin.Conversation;
import com.ai.aigent.entity.dto.ChatSendReq;
import com.ai.aigent.entity.dto.ReadReq;
import com.ai.aigent.entity.dto.RegenDTO;
import com.ai.aigent.service.*;
import com.ai.aigent.utils.CosyVoiceTtsService;
import com.ai.aigent.utils.JWTUtils;
import com.ai.aigent.utils.OcrService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ai")
public class AiController {
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
    private final   ApplicationContext applicationContext;
    @Autowired
    private AgentSessionService agentSessionService;
    @Autowired
    private AgentMessageService agentMessageService;
    @PostMapping("/agent/session")
    public Result<AgentSessionVO> createSession(@RequestAttribute("userId") String userId,String message) {
        AgentSession session = new AgentSession();
        session.setUserId(Long.parseLong(userId));
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
      @GetMapping("/agent/messages")
    public Result<List<AgentMessageVO>> listMessages(
            @RequestParam Long sessionId,
            @RequestAttribute("userId") String userId) {
        // 校验 session 归属
        AgentSession session = agentSessionService.getById(sessionId);
        if (session == null || !session.getUserId().equals(Long.parseLong(userId))) {
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
            // payload 反序列化
            if (m.getPayload() != null) {
                JSONObject jsonObject = JSONUtil.parseObj(m.getPayload());
                AgentMessagePayload payload = jsonObject.toBean(AgentMessagePayload.class);
                vo.setContent(payload.getContent());
                vo.setReasoning(payload.getReasoning());
                vo.setToolCalls(payload.getToolCalls());
            }
            return vo;
        }).collect(Collectors.toList());
        return Result.success(vos);
    }
    private void saveMessage(Long sessionId, String type, String content,
                             String reasoning, List<AgentMessagePayload.ToolCallRecord> toolCalls, int tokens) {
        AgentMessagePayload payload = new AgentMessagePayload();
        payload.setRole(type);
        payload.setContent(content);
        payload.setReasoning(reasoning);
        payload.setToolCalls(toolCalls);
        AgentMessage msg = new AgentMessage();
        msg.setSessionId(sessionId);
        msg.setType(type);
        msg.setTokens(tokens);
        msg.setCreatedAt(new Date());
        msg.setPayload(JSONUtil.toJsonStr( payload));
        agentMessageService.save(msg);
    }
    @GetMapping("/agent/sessions")
    public Result<List<AgentSessionVO>> listSessions(@RequestAttribute("userId") String userId) {
        List<AgentSession> sessions = agentSessionService.lambdaQuery()
                .eq(AgentSession::getUserId, Long.parseLong(userId))
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
 @GetMapping(value = "/agent/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter run(
            @RequestParam Long sessionId,
            @RequestParam String prompt,
            @RequestAttribute("userId") String userId) {

        // 1. 校验 session
        AgentSession session = agentSessionService.getById(sessionId);
        if (session == null || !session.getUserId().equals(Long.parseLong(userId))) {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event().name("error").data("无权访问"));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        // 2. 保存用户消息
        saveMessage(sessionId, "user", prompt, null, null, 0);

        // 3. 更新 session lastMessage
        agentSessionService.lambdaUpdate()
                .eq(AgentSession::getId, sessionId)
                .set(AgentSession::getLastMessage, prompt)
                .set(AgentSession::getUpdateTime, new Date())
                .update();
        // 4. 每次取新的 prototype bean，避免状态污染
        Manus manus = applicationContext.getBean(Manus.class);
        // 6. 启动流
        return manus.runStream(prompt);
    }
    @Autowired
    private CancelTokenRegistry cancelTokenRegistry;
    @PostMapping("/chat/cancel")
    public Result cancel(@RequestParam String chatId) {
        // 找到对应的 sink 或 subscription 取消掉
        cancelTokenRegistry.cancel(chatId);
        return Result.success();
    }

    @PostMapping(value = "/chat/async/", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResult> doChatWithAppAsync(@RequestBody ChatSendReq chatSendReq,HttpServletRequest req)
    {
        String token = chatSendReq.getToken();
        conversationService.lambdaUpdate()//更新会话时间
        .setSql("updated_at = now()")
        .eq(Conversation::getId, chatSendReq.getChatId())
        .update();
        String uid;
        String type;
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
        return codeApp.doChatStream(chatSendReq, uid, type);
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> doChatWithNothing(@RequestBody ChatSendReq chatSendReq)
    {
        return codeApp.doChatStreamBlank(chatSendReq);
    }


    @PostMapping(value = "/chat/regenerate",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> regenerate(RegenDTO regenDTO, HttpServletRequest request)
    {
         Object token = request.getHeader("Authorization");
         if(token!=null)return codeApp.doChatStreamRegenerate(regenDTO,JWTUtils.getUserId(token.toString()));
        return codeApp.doChatStreamRegenerate(regenDTO,"");
    }

//        @GetMapping("/manus/chat")
//    public SseEmitter doChatWithManus(String message)
//    {
//        manus=new Manus(toolCallBacks,dashscopeChatModel);
//        return manus.runStream(message);
//    }
    @Autowired
    private AiAttachmentService attachmentService;
    @GetMapping("/chat/title")
    public Result<Map<String, String>> getChatItemTitle(String message, HttpServletRequest request, String ChatId)
    {
        String s = codeApp.genTitle(message);
        Object token = request.getHeader("Authorization");
        if(token!=null){
            String J = JWTUtils.getUserId(token.toString());
            if(!JWTUtils.validateToken(J)){
                Conversation conversation = new Conversation();
                conversation.setTitle(s);
                conversation.setCreatedAt(LocalDateTime.now());
                conversation.setUpdatedAt(LocalDateTime.now());
                conversation.setId(ChatId);
                conversation.setPinned(false);
                conversation.setUser_id(Long.parseLong(JWTUtils.getUserId(token.toString())));
                conversationService.save(conversation);
            }
        }
        return Result.success(Map.of("title",s));
    }
    @PostMapping("/ocr")
    public Result<String> getOcr(MultipartFile multipartFile)
    {
        try {
            String ocr = OcrService.ocr(multipartFile);
            return Result.success(ocr);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @Autowired
    private CosyVoiceTtsService ttsService;
    @PostMapping("/read")
    public void read(@RequestBody ReadReq req) {
        ttsService.readText(req.getText(), req.getSessionId());
    }
    @PostMapping("/stop")
    public void stop(@RequestBody ReadReq req) {
        ttsService.stop(req.getSessionId());
    }

}
