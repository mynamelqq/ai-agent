package com.ai.aigent.controller;

import com.ai.aigent.app.CancelTokenRegistry;
import com.ai.aigent.app.SpringAiCode;
import com.ai.aigent.constant.Result;
import com.ai.aigent.entity.VO.ChatResult;
import com.ai.aigent.entity.domain.AnonymousUser;
import com.ai.aigent.entity.domain.Conversation;
import com.ai.aigent.entity.dto.*;
import com.ai.aigent.service.*;
import com.ai.aigent.service.impl.CachedService;
import com.ai.aigent.utils.CosyVoiceTtsService;
import com.ai.aigent.utils.JWTUtils;
import com.ai.aigent.utils.OcrService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/ai")
public class AiController {
    @Resource
    private SpringAiCode springAiCode;
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
    @Autowired
    private CachedService streamingCacheService;
    @PostMapping("/chat/cancel")
    public Result cancel(@RequestParam String chatId) {
        // 找到对应的 sink 或 subscription 取消掉
        cancelTokenRegistry.cancel(chatId);
        return Result.success();
    }
    @GetMapping("/snapshot")
    public ResponseEntity<StreamingSnapshotDTO> getSnapshot(//拿当前生成的快照
            @RequestParam String chatId,
            @RequestParam String messageId) {
        StreamingSnapshotDTO dto = streamingCacheService.getSnapshotDTO(chatId, messageId);
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dto);
    }
    @GetMapping(value = "/resubscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatResult>> resubscribe(
            @RequestParam String chatId,
            @RequestParam Long messageId,
            @RequestParam(required = false, defaultValue = "0") Integer lastSeq) {

        return Flux.defer(() -> {
            AtomicInteger cursor = new AtomicInteger(Math.max(0, lastSeq));
            return Flux.interval(Duration.ZERO, Duration.ofMillis(200))//
                    .concatMap(tick -> Mono.fromCallable(() -> streamingCacheService.getEventsAfter(
                                    chatId, messageId.toString(), cursor.get()))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMapMany(Flux::fromIterable))
                    .doOnNext(event -> cursor.set(event.getSeq()))
                    .map(event -> ServerSentEvent.<ChatResult>builder(event)
                            .id(String.valueOf(event.getSeq())).build())
                    .takeUntil(event -> {
                        String type = event.data().getEventType();//takeuntil连接最终结束到收到done以后
                        return "done".equals(type) || "error".equals(type) || "cancelled".equals(type);
                    });
        });
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
        return springAiCode.doChatStream(chatSendReq, uid, type);
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> doChatWithNothing(@RequestBody ChatSendReq chatSendReq)
    {
        return springAiCode.doChatStreamBlank(chatSendReq);
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
        return springAiCode.chatAiStreamRegenerate(regenDTO, uid,type);
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
        conversation.setUserId(userId);
        conversationService.save(conversation);
        String title= springAiCode.genTitle(message);
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
