package com.ai.aigent.app;

import cn.hutool.core.io.resource.InputStreamResource;
import com.ai.aigent.AiCientConstans;
import com.ai.aigent.advisor.JdbcChatMemoryAdvisor;
import com.ai.aigent.advisor.MySimpleLoggerAdvisor;
import com.ai.aigent.advisor.SummaryMemoryAdvisor;
import com.ai.aigent.advisor.VectorStoreMemoryAdvisor;
import com.ai.aigent.entity.AiAttachment;
import com.ai.aigent.entity.ChatMessage;
import com.ai.aigent.entity.VO.ChatResult;
import com.ai.aigent.entity.dto.ChatEmbeddingDTO;
import com.ai.aigent.entity.dto.ChatSendReq;
import com.ai.aigent.entity.dto.RegenDTO;
import com.ai.aigent.service.AiAttachmentService;
import com.ai.aigent.service.ChatMessageService;
import com.ai.aigent.tools.FileOperationTool;
import com.ai.aigent.tools.ImageGenerateTool;
import com.ai.aigent.tools.WebSearchTool;
import com.ai.aigent.utils.AttachmentReaderUtil;
import com.ai.aigent.utils.KryoUtils;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qcloud.cos.model.COSObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StreamUtils;
import prompt.ChatPrompt;
import prompt.SystemPrompt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CodeApp {
    private final ChatClient.Builder chatClientBuilder;
    private final JdbcChatMemoryAdvisor chatMemory;
    //    @Autowired
//    @Resource
//    private OllamaChatModel ollamaChatModel;
    @Resource
    @Qualifier("openAiChatModel")
    private OpenAiChatModel openAiChatModel;


//    @Resource
//    @Qualifier("zhiPuAiChatModel")
//    private ZhiPuAiChatModel zhiPuAiChatModel;
    @Resource
    private VectorStore codeAppVectorStore;
    private final SummaryMemoryAdvisor summaryMemoryAdvisor;
    @Autowired
    private DashScopeChatModel dashScopeChatModel;

    public CodeApp(@Qualifier("dashScopeChatModel") ChatModel dashscpoeChatModel, JdbcChatMemoryAdvisor chatMemory, SummaryMemoryAdvisor summaryMemoryAdvisor,VectorStoreMemoryAdvisor vectorStoreMemoryAdvisor) {
        this.chatMemory=chatMemory;
        this.summaryMemoryAdvisor = summaryMemoryAdvisor;
        InputStream resourceAsStream = this.getClass().getResourceAsStream("/forbiddenWords.txt");
        List<String> forbiddenWords=new ArrayList<String>();
        try {
            BufferedReader br=new BufferedReader(new InputStreamReader(resourceAsStream));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim().toLowerCase();
                if (!line.isEmpty() && !line.startsWith("#")) { // 可跳过空行和注释
                    forbiddenWords.add(line);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        chatClientBuilder = ChatClient.builder(dashscpoeChatModel)
//                .defaultSystem(SystemPrompt.GENERAL_SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                    vectorStoreMemoryAdvisor,
//////                        new SafeGuardAdvisor(forbiddenWords,"内容涉及敏感信息，请更换表述",-1),
//                        summaryMemoryAdvisor,
                        new MySimpleLoggerAdvisor()
                );
    }

    public String genTitle(String args)  {
        return  dashScopeChatModel
                .stream(new Prompt(ChatPrompt.titleGeneratePrompt+args))
                .collectList().block().stream().map(response -> response.getResult().getOutput().getText())
            .collect(Collectors.joining());
    }
    @Autowired
    private WebSearchTool webSearchTool;
    @Resource
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private ImageGenerateTool imageGenerateTool;
    @Resource
    private AiAttachmentService attachmentService;
    @Autowired
    private FileOperationTool fileOperationTool;

//    public Flux<ChatResponse> doChatStream(ChatSendReq chatSendReq, String userId) {
//        String chatId = chatSendReq.getChatId();
//        Long messageId = Long.parseLong(chatSendReq.getMessageId());
//
//        // 附件内容拼接
//        StringBuilder attachmentContent = new StringBuilder();
//        Flux<ChatResponse> prefixFlux = Flux.empty();
//        if (!chatSendReq.getAttachments().isEmpty()) {
//            prefixFlux = Flux.just(
//                    ChatResponse.builder()
//                            .generations(List.of(
//                                    new Generation(new AssistantMessage("<<<ATTACHMENT_LOADING>>>"))
//                            ))
//                            .build()
//            );
//        }
//        if (!chatSendReq.getAttachments().isEmpty()) {
//            List<AiAttachment> aiAttachments = attachmentService.listByIds(chatSendReq.getAttachments());
//            for (AiAttachment aiAttachment : aiAttachments) {
//                attachmentContent.append(
//                        attachmentReaderUtil.readContent(aiAttachment, messageId.toString(), chatId, chatSendReq.getMessage())
//                ).append("\n");
//            }
//        }
//        // Thinking 模式配置
//        if (chatSendReq.getIsThinking() != null && chatSendReq.getIsThinking()) {
//            DashScopeChatOptions dashScopeChatOptions=DashScopeChatOptions.builder().
//                    model("qwq-32b").enableThinking(true).temperature(0.7).build();

////            chatClientBuilder.defaultOptions(zhiPuAiChatOptions);
//            chatClientBuilder.defaultOptions(dashScopeChatOptions);
//        }
//        ChatClient chatClient = chatClientBuilder.build();
//
//        // 动态工具挂载（图片生成）
//        ToolCallback[] localToolCallbacks = null;
//
//
//        String message = chatSendReq.getMessage();
//        StringBuilder full = new StringBuilder();
//        StringBuilder reasoningContent = new StringBuilder();
//        chatMessageService.saveMessage(chatId, new UserMessage(message), messageId.toString());
//        // 构建 AI 流
//        if(chatSendReq.getIsImageGen() != null && chatSendReq.getIsImageGen()){
//            localToolCallbacks=ToolCallbacks.from(imageGenerateTool);
//        } else if (chatSendReq.getIsSearch()) {
//            localToolCallbacks=ToolCallbacks.from(webSearchTool);//联网搜索工具
//        }
//        else if(chatSendReq.getIsGenerateFile()){
//            localToolCallbacks=ToolCallbacks.from(fileOperationTool);
//        }
//        var promptBuilder = chatClient.prompt()
//                .user(message)
//                .system(SystemPrompt.GENERAL_SYSTEM_PROMPT + (attachmentContent.isEmpty() ? "" : attachmentContent.toString()))
//                .advisors(advisor -> advisor
//                        .param(ChatMemory.CONVERSATION_ID, chatId)
//                        .param("messageId", messageId)
//                );
//
//        if (localToolCallbacks != null) {
//            promptBuilder.toolCallbacks(localToolCallbacks);
//        }
//        Map<String,Object>toolContext=Collections.singletonMap("chatId",chatId);
//        Flux<ChatResponse> aiFlux = promptBuilder.toolContext(toolContext)
//                .stream()
//                .chatResponse()
//                .doOnNext(chatResponse -> {
//                    var msg = chatResponse.getResult().getOutput();
//                    Object reasoning = msg.getMetadata().get("reasoningContent");
//                    if (reasoning!=null) reasoningContent.append(reasoning);
//                    if (!msg.getText().isEmpty()) full.append(msg.getText());
////                    if (msg instanceof ZhiPuAiAssistantMessage zhipuMsg) {
////                        String content = zhipuMsg.getText() != null ? zhipuMsg.getText() : "";
////                        String reasoning = zhipuMsg.getReasoningContent() != null ? zhipuMsg.getReasoningContent() : "";
////                        if (!reasoning.isEmpty()) reasoningContent.append(reasoning);
////                        if (!content.isEmpty()) full.append(content);
////                    }
////                    if(!msg.getText().isEmpty())full.append(msg.getText());
//                })
//                .doFinally(signal -> {
//                    // 保存聊天记录
//                    if(chatSendReq.getIsImageGen())return;
//                    String finalAnswer = "";
//                    if (!reasoningContent.isEmpty()) {
//                        reasoningContent.append("</Think>");
//                        finalAnswer = "<Think>" + reasoningContent;
//                    }
//                    finalAnswer += full;
//                    ChatEmbeddingDTO dto = new ChatEmbeddingDTO();
//                    dto.setUserId(userId);
//                    dto.setChatId(chatId);
//                    dto.setUserMessage(message);
//                    dto.setMemoryType("chat");
//                    dto.setAssistantMessage(finalAnswer);
//                    chatMessageService.saveAssistantMessage(chatId, finalAnswer);
//                        // 可选：向向量库发送
//    //                    rabbitTemplate.convertAndSend(
//    //                            "ChatVectorStoreExchange",
//    //                            "ChatVectorStore",
//    //                            JSONUtil.toJsonStr(dto)
//    //                    );
//                })
//                .cache(); // 热流
//
//            return Flux.concat(prefixFlux, aiFlux)
//            .onErrorResume(e -> Flux.empty());
//        }
    public ChatOptions thinking()
    {
        return OpenAiChatOptions.builder()
                .model("openai/gpt-oss-120b")
                .temperature(0.7)
                .build();
//        return  DashScopeChatOptions.builder()
//                    .model("qwen/qwen3-235b-a22b-fp8")
//                    .enableThinking(true)
//                    .temperature(0.7)
//                    .build();
////            ZhiPuAiChatOptions zhiPuAiChatOptions = ZhiPuAiChatOptions.builder()
////                    .model("glm-4.7-FlashX")
////                    .temperature(0.6)
////                    .thinking(ZhiPuAiApi.ChatCompletionRequest.Thinking.enabled())
////                    .build();
    }

    @Transactional
    public Flux<ChatResult> doChatStream(ChatSendReq chatSendReq, String userId) {
        String chatId = chatSendReq.getChatId();
        Long messageId = Long.parseLong(chatSendReq.getMessageId());
        String message = chatSendReq.getMessage();
        // 1️⃣ 前缀 SSE：附件加载骨架
        Flux<ChatResult> prefixFlux = Flux.empty();

        if (!chatSendReq.getAttachments().isEmpty()) {
             ChatResult attachmentChat=new ChatResult();attachmentChat.setText("<<<ATTACHMENT_LOADING>>>");
            prefixFlux = Flux.just(
                    attachmentChat
            );
        }

        // 2️⃣ Thinking 模式配置
        if (Boolean.TRUE.equals(chatSendReq.getIsThinking())) {
            chatClientBuilder.defaultOptions(thinking());
        }
        ChatClient chatClient = chatClientBuilder.build();
        // 3️⃣ 动态工具挂载
        ToolCallback[] localToolCallbacks;
        if (Boolean.TRUE.equals(chatSendReq.getIsImageGen())) {
            localToolCallbacks = ToolCallbacks.from(imageGenerateTool);
        } else if (Boolean.TRUE.equals(chatSendReq.getIsSearch())) {
            localToolCallbacks = ToolCallbacks.from(webSearchTool);
        } else if (Boolean.TRUE.equals(chatSendReq.getIsGenerateFile())) {
            localToolCallbacks = ToolCallbacks.from(fileOperationTool);
        } else {
            localToolCallbacks = null;
        }
        // 4️⃣ 先存用户消息
        chatMessageService.saveMessage(chatSendReq);
        StringBuilder full = new StringBuilder();
        StringBuilder reasoningContent = new StringBuilder();
        List<Media>mediaList=new ArrayList<>();
        // 5️⃣ 响应式读取附件（不会阻塞 Netty）
        Mono<String> attachmentMono = Mono.fromCallable(() -> {
            if (chatSendReq.getAttachments().isEmpty()) return "";
            StringBuilder attachmentContent = new StringBuilder();
            List<AiAttachment> aiAttachments = attachmentService.listByIds(chatSendReq.getAttachments());
            for (AiAttachment aiAttachment : aiAttachments) {
                if(aiAttachment.getFileType().startsWith("image/")){//多模态模型上传
                    COSObject object = attachmentReaderUtil.getObject(AiCientConstans.bulk, aiAttachment.getFileUrl());
                    cn.hutool.core.io.resource.Resource resource = new InputStreamResource(object.getObjectContent());
                    byte[] bytes = StreamUtils.copyToByteArray(resource.getStream());
                    mediaList.add(Media.builder().mimeType(MimeTypeUtils.parseMimeType(aiAttachment.getFileType())
                            ).id(aiAttachment.getId().toString()).name(aiAttachment.getFileName()).data(bytes).build());
                    continue;
                }
                attachmentContent.append(
                    attachmentReaderUtil.readContent(aiAttachment, messageId.toString(), chatId, message)
                ).append("\n");
            }
            return attachmentContent.toString();
        }).subscribeOn(Schedulers.boundedElastic());
        // 6️⃣ 附件读取完成后，再启动 AI SSE 流
    String system;
    if(chatSendReq.getIsEcharts()){
        system=SystemPrompt.ECHARTS_SYSTEM_PROMPT;
    } else {
        system = SystemPrompt.GENERAL_SYSTEM_PROMPT;
    }
    Flux<ChatResult> aiFlux = attachmentMono.flatMapMany(attachmentContent -> {
    var promptBuilder = chatClient.prompt()
            .user(promptUserSpec -> {promptUserSpec.text(message)
                    .media(mediaList.toArray(new Media[0]));
            })
            .system(system+attachmentContent)
            .advisors(advisor -> advisor
                    .param(ChatMemory.CONVERSATION_ID, chatId)
                    .param("messageId", messageId)
            );
    if (localToolCallbacks != null) {
        promptBuilder.toolCallbacks(localToolCallbacks);
    }
    AtomicReference<Integer> token= new AtomicReference<>(0);
    AtomicReference<Long>startTime = new AtomicReference<>(System.currentTimeMillis());
    Map<String, Object> toolContext = Collections.singletonMap("chatId", chatId);
        return promptBuilder
                .toolContext(toolContext)
                .stream()
                .chatResponse()
                .doOnError(throwable -> {
                    log.error(throwable.getMessage());
                })
                // 异常不中断流
                .onErrorContinue((throwable, o) -> {
                    log.error(throwable.getMessage());
                })
                .switchIfEmpty(Flux.error(new RuntimeException("AI 响应失败")))
                .doOnNext(chatResponse -> {
                     try {
                        if (chatResponse == null) return;
                        Usage usage = chatResponse.getMetadata().getUsage();
                        token.set(Integer.parseInt(usage != null ? usage.getTotalTokens().toString() : "0"));
                        var result = chatResponse.getResult();
                        if (result == null) return;
                        var msg = result.getOutput();
                        if (msg == null) return;
                        Optional.ofNullable(msg.getMetadata())
                                        .map(meta -> meta.get("reasoningContent"))
                                        .map(Object::toString)
                                        .ifPresent(reasoningContent::append);
                        String text = msg.getText();
                        if (text != null && !text.isEmpty()) {
                            full.append(text);
                        }
                    } catch (Throwable e) {
                         log.error("解析 chatResponse 时发生异常", e);
                    }
                })
                .map(chatResponse -> {
                    ChatResult chatResult=new ChatResult();
                   if (chatResponse == null) return chatResult;
                    String s = Optional.of(chatResponse.getResult().getOutput().getMetadata())
                            .map(meta -> meta.get("reasoningContent"))
                            .map(Object::toString).get();
                    chatResult.setReasoning(s);
                    String text = chatResponse.getResult().getOutput().getText();
                    if (text != null && !text.isEmpty()) {
                        chatResult.setText(text);
                    }
                     return chatResult;
                })
                .doFinally(signal -> {
                    if (Boolean.TRUE.equals(chatSendReq.getIsImageGen())) return;
                    if (signal != SignalType.ON_COMPLETE) {
                        log.warn("AI 流未正常完成，signal={}, 不保存消息", signal);
                        return;
                    }
                    String finalAnswer = buildFinalAnswer(reasoningContent, full);
                    if (!finalAnswer.isEmpty()) {
                        chatMessageService.saveAssistantMessage(chatId, finalAnswer, token.get(),
                                (int) (System.currentTimeMillis()-startTime.get()));
                    }
                });
    });
    return Flux.concat(prefixFlux, aiFlux)
            .onErrorResume(e -> {
                System.out.println("=== 上游异常被 onErrorResume 捕获 ===");
                return Flux.empty();
            });
    }
    private String buildFinalAnswer(StringBuilder reasoningContent, StringBuilder full) {
        String finalAnswer = "";
        if (!reasoningContent.isEmpty()) {
            finalAnswer = "<Think>" + reasoningContent + "</Think>";
        }
        return finalAnswer + full;
    }
     @Transactional
    public Flux<ChatResponse> doChatStreamRegenerate(RegenDTO regenDTO, String userId) {
         String userMessageId = regenDTO.getUserMessageId();
         ChatMessage chatMessage = chatMessageService.getById(userMessageId);//获取用户消息
         UserMessage userMessage = KryoUtils.deserialize(chatMessage.getPayload());
         String chatId = regenDTO.getChatId();
        String message =userMessage.getText();
        StringBuilder full = new StringBuilder();
        StringBuilder attachmentContent = new StringBuilder();
        StringBuilder reasoningContent=new StringBuilder();
         List<AiAttachment> list = attachmentService.list(new LambdaQueryWrapper<AiAttachment>().eq(AiAttachment::getChatId, userMessageId));
         if(list!=null&&!list.isEmpty())//获取附件
        {
            for (AiAttachment aiAttachment : list) {
                attachmentContent.append(attachmentReaderUtil.readContent(aiAttachment, regenDTO.getUserMessageId(), chatId, message));
                attachmentContent.append("\n");
            }
        }
        if(chatMessage.getIsThinking()){
              chatClientBuilder.defaultOptions(thinking());
        }
        ChatClient chatClient = chatClientBuilder.build();
        Flux<ChatResponse> aiFlux = chatClient.prompt().user(message)
                .system(SystemPrompt.GENERAL_SYSTEM_PROMPT+(list==null||list.isEmpty()?"":attachmentContent.toString()))
                .advisors(advisor -> advisor
                        .param(ChatMemory.CONVERSATION_ID, chatId)
                        .param("messageId", userMessageId)
                )
                .stream().chatResponse()
                .doOnNext(chatResponse -> {
                    var msg = chatResponse.getResult().getOutput();
                     Object reasoning = msg.getMetadata().get("reasoningContent");
                    if (reasoning != null) reasoningContent.append(reasoning);
                    if (msg.getText() != null && !msg.getText().isEmpty()) {
                        full.append(msg.getText());
                    }
//                   if (msg instanceof ZhiPuAiAssistantMessage zhipuMsg) {
//                       if (zhipuMsg.getText()!=null) {
//                            content = zhipuMsg.getText();
//                        }
//                        String reasoning = "";
//                        if (zhipuMsg.getReasoningContent()!=null) {
//                            reasoning = zhipuMsg.getReasoningContent();
//                        }
//                        if (!reasoning.isEmpty()) reasoningContent.append(reasoning);
//                        if (!content.isEmpty()) full.append(content);
//                   }
                }
                )
                .doFinally(signal -> {
                    String finalAnswer="";
                    // 无论前端取消 / 断连 / 正常完成，这里都会执行
                    if (!reasoningContent.isEmpty()) {
                        reasoningContent.append("</Think>");
                        finalAnswer="<Think>"+reasoningContent;
                    }
                    finalAnswer+=full;
                    if (!finalAnswer.isEmpty()) {
                        ChatEmbeddingDTO dto = new ChatEmbeddingDTO();
                        dto.setUserId(userId);
                        dto.setChatId(chatId);
                        dto.setUserMessage(message);
                        dto.setMemoryType("chat");
                        dto.setAssistantMessage(finalAnswer);
                        chatMessageService.updateMessage(chatId,finalAnswer,regenDTO.getAssistantMessageId(),"assistant");
                    }
                })
                .cache(); // 关键：让 Flux 变成“可复用的热流”
        // 返回给前端的流（前端取消不会影响 aiFlux）
        return aiFlux
                .onErrorResume(e -> {
                    // 前端断连时忽略 IOException
                    return Flux.empty();
                });
    }
    public Flux<ChatResponse> doChatStreamBlank(ChatSendReq chatSendReq) {
        String message = chatSendReq.getMessage();
         if(chatSendReq.getIsThinking()){

              ZhiPuAiChatOptions zhiPuAiChatOptions = ZhiPuAiChatOptions.builder().model("glm-4.7")
                      .temperature(0.6)
                .thinking(ZhiPuAiApi.ChatCompletionRequest.Thinking.enabled()).build();
              chatClientBuilder.defaultOptions(zhiPuAiChatOptions);
        }
        ChatClient chatClient = chatClientBuilder.build();
        Flux<ChatResponse> aiFlux =chatClient.prompt().user(message)
                .stream().chatResponse();
        return aiFlux
                .onErrorResume(e -> {
                    return Flux.empty();
                });
    }
    @Autowired
    private ChatMessageService chatMessageService;
     @Resource
     private AttachmentReaderUtil attachmentReaderUtil;

    public Flux<String> doChatStreamUser(String message, String chatId)
    {
        ChatClient chatClient = chatClientBuilder.build();
        return chatClient.prompt().user(message)
//                .advisors(advisor->advisor.param(ChatMemory.CONVERSATION_ID,chatId))
//                .advisors(new QuestionAnswerAdvisor(codeAppVectorStore))
                .stream().content();
//        String text = chatResponse.getResult().getOutput().getText();
    }
    record codeReport(String title, List<String> suggestions){}
//     public codeReport doChatWithReport(String message, String chatId)
//        {
//            codeReport entity = chatClient.prompt().advisors(advisor->advisor.param(ChatMemory.CONVERSATION_ID,chatId))
//                    .system(SYSTEM_PROMPT + "每次对话后都要生成恋爱结果，标题为{用户名}的恋爱报告，内容为建议列表")
//                    .user(message)
//              .call().entity(codeReport.class);
//
////            log.info(entity);
//            return entity;
//
//        }


//    public String doChatWithRag(String message,String chatId)
//    {
//        Map<String,Object> params = new HashMap<>();
//        params.put("id",chatId);
//        ChatResponse chatResponse = chatClient
//                .prompt()
//                .user(message)
//                .advisors(new QuestionAnswerAdvisor(codeAppVectorStore)).toolContext(params)
//
//                .call().chatResponse();
//        return chatResponse.getResult().getOutput().getText();
//
//    }
//    @Resource
//    private ToolCallback[] toolCallbacks;
//    public String doChatWithToolCalling(String message,String chatId)
//    {
//        Map<String,Object> params = new HashMap<>();
//        params.put("id",chatId);
//        ChatResponse chatResponse = chatClient
//                .prompt()
//                .user(message)
//                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
//                .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, AiCientConstans.SHORT_MEMORY_LENGTH)
//                        .advisors(new MySimpleLoggerAdvisor())).tools(toolCallbacks)
//
//
//
//                .call().chatResponse();
//        return chatResponse.getResult().getOutput().getText();
//
//    }
    @Resource
    private ToolCallbackProvider toolCallbackProvider;
    public String doChatWithMcp(String message,String chatId)
    {
        ChatClient chatClient = chatClientBuilder.build();
        Map<String,Object> params = new HashMap<>();
        params.put("id",chatId);
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .toolCallbacks(toolCallbackProvider)
//                .advisors(advisor->advisor.param(ChatMemory.CONVERSATION_ID,chatId))
//                .advisors(new MySimpleLoggerAdvisor()).tools(toolCallbackProvider)
                .call().chatResponse();
        return chatResponse.getResult().getOutput().getText();

    }

}
