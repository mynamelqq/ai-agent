package com.ai.aigent.controller;

import com.ai.aigent.app.CodeApp;
import com.ai.aigent.constant.Result;
import com.ai.aigent.entity.Conversation;
import com.ai.aigent.entity.dto.ChatSendReq;
import com.ai.aigent.service.AiAttachmentService;
import com.ai.aigent.service.ConversationService;
import com.ai.aigent.utils.JWTUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
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

    @PostMapping(value = "/chat/async/", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> doChatWithAppAsync(@RequestBody ChatSendReq chatSendReq)
    {
        String token = chatSendReq.getToken();
        conversationService.lambdaUpdate()
        .setSql("updated_at = now()")
        .eq(Conversation::getId, chatSendReq.getChatId())
        .update();
        if(token!=null&&JWTUtils.validateToken(token.toString())){
            String J = JWTUtils.getUserId(token.toString());
            return codeApp.doChatStream(chatSendReq,J);
        }
        return codeApp.doChatStream(chatSendReq,"");
    }
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> doChatWithNothing(@RequestBody ChatSendReq chatSendReq)
    {
        return codeApp.doChatStreamBlank(chatSendReq);
    }
//    @PostMapping(value = "/chat/regenerate",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public Flux<ChatResponse> regenerate(ChatSendReq chatSendReq,HttpServletRequest request)
//    {
//         Object token = request.getHeader("Authorization");
////         if(token!=null)return codeApp.doChatStreamRegenerate(chatSendReq,JWTUtils.getUserId(token.toString()));
//        return codeApp.doChatStreamRegenerate(chatSendReq,"");
//    }

    //    @GetMapping("/manus/chat")
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



//    @GetMapping(value = "/love/chat/async/",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public Flux<ServerSentEvent<String>> doChatWithAppAsyncServer(String ChatId, String message)
//    {
//        return codeApp.doChatStream(message,ChatId)
//                .map(chunk-> ServerSentEvent.<String>builder().data(chunk).build());
//
//    }
}
