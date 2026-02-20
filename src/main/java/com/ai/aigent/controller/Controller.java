package com.ai.aigent.controller;

import cn.hutool.core.bean.BeanUtil;
import com.ai.aigent.constant.AiCientConstans;
import com.ai.aigent.constant.ExecErrorType;
import com.ai.aigent.constant.Result;
import com.ai.aigent.entity.VO.AttachmentVO;
import com.ai.aigent.entity.VO.ChatMessageVO;
import com.ai.aigent.entity.domin.AiAttachment;
import com.ai.aigent.entity.domin.ChatMessage;
import com.ai.aigent.entity.domin.ChatSummary;
import com.ai.aigent.entity.domin.Conversation;
import com.ai.aigent.exception.BusinessException;
import com.ai.aigent.service.AiAttachmentService;
import com.ai.aigent.service.ChatMessageService;
import com.ai.aigent.service.ChatSummaryService;
import com.ai.aigent.service.ConversationService;
import com.ai.aigent.utils.JWTUtils;
import com.ai.aigent.utils.KryoUtils;
import com.ai.aigent.utils.PdfExportUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


@Slf4j
@RequestMapping
@RestController
public class Controller {
    @Resource
    private ChatMessageService chatMessageService;
    @Resource
    private ConversationService conversationService;
    @Resource
    private ChatSummaryService chatSummaryService;
    @GetMapping("/list/chat")
    public Result<List<Conversation>> listChatConservations(HttpServletRequest request)
    {
         String authHeader = request.getHeader("Authorization");
         if(StringUtils.isBlank(authHeader) ||authHeader.isEmpty()||!JWTUtils.validateToken(authHeader))
         {
             return Result.error("请先登录");
         }
        String userId = JWTUtils.getUserId(authHeader);

        List<Conversation> list = conversationService.list(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getUser_id, userId));
        Collections.reverse(list);
        return Result.success(list);
    }
    @GetMapping("/pinned")
    public Result pinnedConservations(String conservationId)
    {
        Conversation conversation = conversationService.getById(conservationId);
        if(conversation==null)return Result.error("会话不存在");
        conversationService.lambdaUpdate().eq(Conversation::getId,conversation.getId())
                .set(Conversation::getPinned,true).update();
        return Result.success();
    }
    @GetMapping("/unpinned")
    public Result unpinnedConservations(String conservationId)
    {
        Conversation conversation = conversationService.getById(conservationId);
        if(conversation==null)return Result.error("会话不存在");
        conversationService.lambdaUpdate().eq(Conversation::getId,conversation.getId())
                .set(Conversation::getPinned,false).update();
        return Result.success();
    }
    @GetMapping("/list/message")
    public Result<List<ChatMessageVO>> listMessage(String ChatId)
    {
        List<ChatMessage> list = chatMessageService.list(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, ChatId));
        List<ChatMessageVO>listVO =
                list.stream().map(l->
                {ChatMessageVO chat=new ChatMessageVO(); BeanUtil.copyProperties(l,chat);
                    chat.setPayload(chatMessageService.deserialize(l).getText());
                    return chat;
                }).collect(Collectors.toList());
        if(listVO.size()>1)Collections.reverse(listVO);
        return Result.success(listVO);
    }
    @Autowired
    private AiAttachmentService  aiAttachmentService;
    @GetMapping("/list/message/{page}")
    public Result<Page<ChatMessageVO>> listMessage(String ChatId,@PathVariable int page)
    {

        Page<ChatMessage> pages = new Page<>(page, AiCientConstans.MESSAGE_PAGE_SIZE);
         Page<ChatMessage> page1= chatMessageService.page(pages,
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getConversationId, ChatId)
//                        .orderByDesc(ChatMessage::getCreateTime)
         );
         Page<ChatMessageVO>Page = new Page<>();
         Page.setCurrent(page1.getCurrent());
         Page.setPages(page1.getPages());
         Page.setTotal(page1.getTotal());
         List<ChatMessageVO>listVO = new ArrayList<>();
         for(ChatMessage chat:page1.getRecords())
         {
             ChatMessageVO chatVO=new ChatMessageVO();
             if(chat.getMessageType().equals(MessageType.USER.getValue()))//有附件
             {
                 List<AiAttachment> list = aiAttachmentService.list(new LambdaQueryWrapper<AiAttachment>()
                         .eq(AiAttachment::getChatId, chat.getId())
                 );
                 if(list!=null&&!list.isEmpty()){

                     chatVO.setAiAttachments(list.stream().map(t->
                     {
                         AttachmentVO attachmentVO=new AttachmentVO();
                         BeanUtils.copyProperties(t,attachmentVO);return attachmentVO;}).toList());
                 }
             }
             BeanUtil.copyProperties(chat,chatVO);
             chatVO.setPayload(chatMessageService.deserialize(chat).getText());
             listVO.add(chatVO);
         }
//         List<ChatMessageVO>listVO =page1.getRecords().stream().map(l->
//                {ChatMessageVO chat=new ChatMessageVO(); BeanUtil.copyProperties(l,chat);
//                    chat.setPayload(chatMessageService.deserialize(l).getText());
//                    return chat;
//                }).collect(Collectors.toList());

        if(listVO.size()>1)Collections.reverse(listVO);
        Page.setRecords(listVO);
        return Result.success(Page);
    }
    @Transactional
    @PostMapping("/chat/delete")
    public Result<?> delete(String ChatId)
    {
        chatMessageService.remove(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, ChatId));

        conversationService.remove(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getId, ChatId));
        chatSummaryService.remove(new LambdaQueryWrapper<ChatSummary>().eq(ChatSummary::getConversationId, ChatId));
        return Result.success("删除成功");
    }
  @GetMapping("/export/{messageId}")
    public ResponseEntity<byte[]> export(
            @PathVariable Long messageId,
            @RequestParam(defaultValue = "txt") String format) {

        ChatMessage chatMessage = chatMessageService.getById(messageId);
        if(chatMessage==null){
            throw new RuntimeException("会话不存在");
        }
        byte[] payload = chatMessage.getPayload();
        AssistantMessage assistantMessage = KryoUtils.deserialize(payload);
        String text = assistantMessage.getText();
        // 2️⃣ 根据格式生成文件
        byte[] fileBytes;
        String fileName;
        String contentType;
        if(text.isEmpty()){
            throw new BusinessException(ExecErrorType.NONE.ordinal(),"会话内容为空");
        }
        switch (format.toLowerCase()) {
            case "pdf":
                fileBytes = PdfExportUtil.toPdf(text);
                fileName = "answer.pdf";
                contentType = "application/pdf";
                break;

            case "md":
                fileBytes = text.getBytes(StandardCharsets.UTF_8);
                fileName = "answer.md";
                contentType = "text/markdown";
                break;

            default:
                fileBytes = text.getBytes(StandardCharsets.UTF_8);
                fileName = "answer.txt";
                contentType = "text/plain";
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=" +messageId +"-"+ fileName)
                .contentType(MediaType.parseMediaType(contentType))
                .body(fileBytes);
    }
    @Transactional
    @PostMapping("/chat/rename")
    public Result<String> renameChatItem(String ChatId, String title)
    {
        Conversation conversation = new Conversation();
        conversation.setId(ChatId);
        conversation.setTitle(title);
        conversationService.updateById(conversation);
        return Result.success("修改成功");
    }
    @GetMapping("/search/message")
    public Result<List<Conversation>> searchMessage(String ChatId, String keyword)
    {
        List<Conversation> list = conversationService.list(new LambdaQueryWrapper<Conversation>()
//                .eq(Conversation::, ChatId)
                .like(Conversation::getTitle, keyword));
        return Result.success(list);
    }
}
