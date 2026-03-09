package com.ai.aigent.controller;

import cn.hutool.core.bean.BeanUtil;
import com.ai.aigent.constant.AiCientConstans;
import com.ai.aigent.constant.ExecErrorType;
import com.ai.aigent.constant.Result;
import com.ai.aigent.entity.VO.AttachmentVO;
import com.ai.aigent.entity.VO.ChatMessageVO;
import com.ai.aigent.entity.domin.*;
import com.ai.aigent.exception.BusinessException;
import com.ai.aigent.service.*;
import com.ai.aigent.utils.JWTUtils;
import com.ai.aigent.utils.KryoUtils;
import com.ai.aigent.utils.PdfExportUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
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

import javax.security.auth.login.LoginException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
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
    @Resource
    private UsersService usersService;
    @Resource
    private UserUsageDailyService userUsageDailyService;
    @GetMapping("/list/chat")
    public Result<List<Conversation>> listChatConservations(HttpServletRequest request)
    {
        String userId = usersService.getLoginUser(request);
        if(userId.isBlank()){
            return Result.error("请先登录");
        }
        List<Conversation> list = conversationService.list(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getUser_id, userId));
        Collections.reverse(list);
        return Result.success(list);
    }
    @GetMapping("/pinned")
    public Result pinnedConservations(String conservationId)//置顶会话
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
    @GetMapping("/get/usage")
    public Result<Map<String, Object>> getUsage(HttpServletRequest request) {
        String type="anno";
        String authorization = request.getHeader("Authorization");
        if(authorization!=null&&!authorization.isEmpty()){
            type="user";
        }
        String userId = usersService.getLoginUser(request);
        Long total_token = userUsageDailyService.getUsage(request);

        Map<String,Object>map=new HashMap<>();
        map.put("total",userUsageDailyService.count(new LambdaQueryWrapper<UserUsageDaily>()
                .eq(UserUsageDaily::getUserId,userId)));//调用总数
        map.put("total_token",total_token);//总计消耗token

        QueryWrapper<UserUsageDaily> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
               .ge("time", LocalDate.now().atStartOfDay())
               .lt("time", LocalDate.now().plusDays(1).atStartOfDay())
               .select("SUM(token) as total");
        List<Map<String, Object>> maps = userUsageDailyService.getBaseMapper().selectMaps(wrapper);

        Long total = maps.isEmpty() ? 0 : ((Number) maps.get(0).get("total")).longValue();
        map.put("today_token_quota",userUsageDailyService.getDailyLimit(userId,type));
        map.put("today_token",total);
       LocalDate today = LocalDate.now();

        Long todayCount = userUsageDailyService.count(
            new LambdaQueryWrapper<UserUsageDaily>()
                .eq(UserUsageDaily::getUserId, userId)
                .ge(UserUsageDaily::getTime, today.atStartOfDay())          // >= 今天 00:00:00
                .lt(UserUsageDaily::getTime, today.plusDays(1).atStartOfDay()) // < 明天 00:00:00
        );


        map.put("today_calls", todayCount);
        return Result.success(map);
    }
    @GetMapping("/stat/hour")
    public Result<List<Map<String,Object>>> statByHour(
            @RequestParam String date,
            HttpServletRequest request){
        String userId = usersService.getLoginUser(request);
        LocalDate localDate = LocalDate.parse(date);
        List<Map<String,Object>> data =
                userUsageDailyService.statByHour(userId, localDate);
        return Result.success(data);
    }

    /**
     * 按月统计
     */
    @GetMapping("/stat/month")
    public Result<List<Map<String,Object>>> statByMonth(
            @RequestParam String start,
            @RequestParam String end,
            HttpServletRequest request){
        String userId = usersService.getLoginUser(request);
        LocalDate startDate = LocalDate.parse(start);
        LocalDate endDate = LocalDate.parse(end);
        List<Map<String,Object>> data =
                userUsageDailyService.statByMonth(userId,startDate,endDate);
        return Result.success(data);
    }
    /**
     * 按年统计
     */
    @GetMapping("/stat/year")
    public Result<List<Map<String,Object>>> statByYear(
            @RequestParam String start,
            @RequestParam String end,
            HttpServletRequest request){
        String userId = usersService.getLoginUser(request);
        LocalDate startDate = LocalDate.parse(start);
        LocalDate endDate = LocalDate.parse(end);
        List<Map<String,Object>> data =
                userUsageDailyService.statByYear(userId,startDate,endDate);
        return Result.success(data);
    }
    @GetMapping("/stat/model")
    public Result<List<Map<String,Object>>> statByModel(HttpServletRequest request){

        String userId = usersService.getLoginUser(request);

        List<Map<String,Object>> data =
                userUsageDailyService.statByModel(userId);

        return Result.success(data);
    }
    @GetMapping("/stat/promptCompletion")
    public Result<Map<String,Object>> statPromptCompletion(HttpServletRequest request){

        String userId = usersService.getLoginUser(request);
        Map<String,Object> data =
                userUsageDailyService.statPromptCompletion(userId);

        return Result.success(data);
    }
    @GetMapping("/stat")
    public Result<IPage<UserUsageDaily>>statModelRecords(HttpServletRequest request,Integer page)
    {
        String userId = usersService.getLoginUser(request);
         LambdaQueryWrapper<UserUsageDaily> queryWrapper = new LambdaQueryWrapper<UserUsageDaily>()
            .eq(UserUsageDaily::getUserId, userId)
            .orderByDesc(UserUsageDaily::getTime);  // 按时间倒序排列
        // 执行分页查询
        Page<UserUsageDaily>resultPage= userUsageDailyService.page(new Page<>(page, 15), queryWrapper);
        return Result.success(resultPage);
    }

}
