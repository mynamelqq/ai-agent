package com.ai.aigent.controller;

import com.ai.aigent.app.CancelTokenRegistry;
import com.ai.aigent.app.CodeApp;
import com.ai.aigent.constant.Result;
import com.ai.aigent.entity.VO.ChatResult;
import com.ai.aigent.entity.domin.AnonymousUser;
import com.ai.aigent.entity.domin.Conversation;
import com.ai.aigent.entity.dto.ChatSendReq;
import com.ai.aigent.entity.dto.ReadReq;
import com.ai.aigent.entity.dto.RegenDTO;
import com.ai.aigent.entity.dto.VideoUnderstandReq;
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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ai")
public class AiController {
    @Resource
    private CodeApp codeApp;
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
    @Autowired
    private CancelTokenRegistry cancelTokenRegistry;
    @Autowired
    private VideoUnderstandingService videoUnderstandingService;
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
        if(token!=null&&!token.isEmpty()){
            token=token.substring("Bearer ".length());
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
    public Flux<ChatResult> regenerate(@RequestBody RegenDTO regenDTO, HttpServletRequest request)
    {
         String token = regenDTO.getToken();
        conversationService.lambdaUpdate()//更新会话时间
        .setSql("updated_at = now()")
        .eq(Conversation::getId, regenDTO.getChatId())
        .update();
        String uid;
        String type;
        if(token!=null&&!token.isEmpty()){
            token=JWTUtils.extractToken(token);
            uid = JWTUtils.getUserId(token);
            type = "user";
        }
        else {
            AnonymousUser user = anonymousUserService.getById(request);
            uid = user.getAnonId();
            type = "anon";
        }
        dailyService.checkAndConsume(uid,type);
        return codeApp.doChatStreamRegenerate(regenDTO, uid,type);
    }
    @Autowired
    private AiAttachmentService attachmentService;
    @GetMapping("/chat/title")
    public Result<Map<String, String>> getChatItemTitle(String message, HttpServletRequest request, String ChatId)
    {
        String userId = usersService.getLoginUser(request);
        if(userId.isBlank()){
            return Result.error("请先登录");
        }
        Conversation conversation = new Conversation();
        conversation.setTitle("新对话");
        conversation.setCreatedAt(LocalDateTime.now( ));
        conversation.setUpdatedAt(LocalDateTime.now());
        conversation.setId(ChatId);
        conversation.setPinned(false);
        conversation.setUser_id(userId);
        conversationService.save(conversation);
        String title= codeApp.genTitle(message);
        conversation.setTitle(title);
        conversationService.updateById(conversation);
        return Result.success(Map.of("title",title));
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
    @PostMapping("/video/understand")
    public Result<String> understandVideo(@RequestBody VideoUnderstandReq req) {
        return Result.success(videoUnderstandingService.understand(req));
    }

}
