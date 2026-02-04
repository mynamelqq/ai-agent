package com.ai.aigent.controller;

import cn.hutool.core.util.IdUtil;
import com.ai.aigent.app.CodeApp;
import com.ai.aigent.constant.Result;
import com.ai.aigent.entity.Conversation;
import com.ai.aigent.service.ConversationService;
import com.ai.aigent.utils.JWTUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;

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
    @GetMapping(value = "/chat/async/",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithAppAsync(String ChatId, String message,@RequestParam(required = false) String token)
    {

        if(token!=null&&JWTUtils.validateToken(token.toString())){
            String J = JWTUtils.getUserId(token.toString());
            return codeApp.doChatStream(message,ChatId,J);
        }
        return codeApp.doChatStream(message,ChatId,"");

    }
//    @GetMapping("/manus/chat")
//    public SseEmitter doChatWithManus(String message)
//    {
//        manus=new Manus(toolCallBacks,dashscopeChatModel);
//        return manus.runStream(message);
//    }
    @GetMapping("/chat/title")
    public Result<Map<String, String>> getChatItemTitle(String message, HttpServletRequest request, @RequestParam(required = false) String ChatId)
    {
        String s = codeApp.genTitle(message);
        Object token = request.getHeader("Authorization");

        if(token!=null){
            String J = JWTUtils.getUserId(token.toString());
            if(!JWTUtils.validateToken(J)){
                Conversation conversation = new Conversation();
                conversation.setTitle(s);
                UUID uuid = UUID.randomUUID();
                ChatId = IdUtil.nanoId();  // 默认21位，可自定义
                conversation.setId(ChatId);
                conversation.setUser_id(Long.parseLong(JWTUtils.getUserId(token.toString())));
                conversationService.save(conversation);
            }
        }
        return Result.success(Map.of("title",s,"chatId",ChatId));
    }



//    @GetMapping(value = "/love/chat/async/",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public Flux<ServerSentEvent<String>> doChatWithAppAsyncServer(String ChatId, String message)
//    {
//        return codeApp.doChatStream(message,ChatId)
//                .map(chunk-> ServerSentEvent.<String>builder().data(chunk).build());
//
//    }
}
