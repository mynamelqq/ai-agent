package com.ai.aigent.app;

import com.ai.aigent.advisor.JdbcChatMemoryAdvisor;
import com.ai.aigent.advisor.MySimpleLoggerAdvisor;
import com.ai.aigent.advisor.SummaryMemoryAdvisor;
import com.ai.aigent.advisor.VectorStoreMemoryAdvisor;
import com.ai.aigent.entity.AiAttachment;
import com.ai.aigent.entity.dto.ChatEmbeddingDTO;
import com.ai.aigent.entity.dto.ChatSendReq;
import com.ai.aigent.service.AiAttachmentService;
import com.ai.aigent.service.ChatMessageService;
import com.ai.aigent.tools.FileOperationTool;
import com.ai.aigent.tools.ImageGenerateTool;
import com.ai.aigent.tools.WebSearchTool;
import com.ai.aigent.utils.AttachmentReaderUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.qcloud.cos.demo.ci.ImageAuditingDemo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.zhipuai.ZhiPuAiAssistantMessage;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import prompt.ChatPrompt;
import prompt.SystemPrompt;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

@Component
@Slf4j
public class CodeApp {
    private final ChatClient.Builder chatClientBuilder;
    private final JdbcChatMemoryAdvisor chatMemory;
//    @Resource
//    private DashScopeChatModel dashscopeChatModel;
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
                .defaultSystem(SystemPrompt.GENERAL_SYSTEM_PROMPT)

                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                    vectorStoreMemoryAdvisor,
//////                        new SafeGuardAdvisor(forbiddenWords,"内容涉及敏感信息，请更换表述",-1),
//                        summaryMemoryAdvisor,
                        new MySimpleLoggerAdvisor()
                );
    }

    public String genTitle(String args)  {
        return  openAiChatModel.call(new Prompt(ChatPrompt.titleGeneratePrompt+args)).getResult().getOutput().getText();
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

    @Retryable(
    value = {WebClientResponseException.TooManyRequests.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 2000, multiplier = 2)
)
    @Transactional
    public Flux<ChatResponse> doChatStream(ChatSendReq chatSendReq, String userId) {
        String chatId = chatSendReq.getChatId();
        Long messageId = Long.parseLong(chatSendReq.getMessageId());

        // 附件内容拼接
        StringBuilder attachmentContent = new StringBuilder();
        Flux<ChatResponse> prefixFlux = Flux.empty();
        if (!chatSendReq.getAttachments().isEmpty()) {
            prefixFlux = Flux.just(
                    ChatResponse.builder()
                            .generations(List.of(
                                    new Generation(new AssistantMessage("<<<ATTACHMENT_LOADING>>>"))
                            ))
                            .build()
            );
        }
        if (!chatSendReq.getAttachments().isEmpty()) {
            List<AiAttachment> aiAttachments = attachmentService.listByIds(chatSendReq.getAttachments());
            for (AiAttachment aiAttachment : aiAttachments) {
                attachmentContent.append(
                        attachmentReaderUtil.readContent(aiAttachment, messageId.toString(), chatId, chatSendReq.getMessage())
                ).append("\n");
            }
        }
        // Thinking 模式配置
        if (chatSendReq.getIsThinking() != null && chatSendReq.getIsThinking()) {
            DashScopeChatOptions dashScopeChatOptions=DashScopeChatOptions.builder().
                    model("qwq-32b").enableThinking(true).temperature(0.7).build();
//            ZhiPuAiChatOptions zhiPuAiChatOptions = ZhiPuAiChatOptions.builder()
//                    .model("glm-4.7-FlashX")
//                    .temperature(0.6)
//                    .thinking(ZhiPuAiApi.ChatCompletionRequest.Thinking.enabled())
//                    .build();
//            chatClientBuilder.defaultOptions(zhiPuAiChatOptions);
            chatClientBuilder.defaultOptions(dashScopeChatOptions);
        }
        ChatClient chatClient = chatClientBuilder.build();

        // 动态工具挂载（图片生成）
        ToolCallback[] localToolCallbacks = null;


        String message = chatSendReq.getMessage();
        StringBuilder full = new StringBuilder();
        StringBuilder reasoningContent = new StringBuilder();
        chatMessageService.saveMessage(chatId, new UserMessage(message), messageId.toString());
        // 构建 AI 流
        if(chatSendReq.getIsImageGen() != null && chatSendReq.getIsImageGen()){
            localToolCallbacks=ToolCallbacks.from(imageGenerateTool);
        } else if (chatSendReq.getIsSearch()) {
            localToolCallbacks=ToolCallbacks.from(webSearchTool);//联网搜索工具
        }
        else if(chatSendReq.getIsGenerateFile()){
            localToolCallbacks=ToolCallbacks.from(fileOperationTool);
        }
        var promptBuilder = chatClient.prompt()
                .user(message)
                .system(SystemPrompt.GENERAL_SYSTEM_PROMPT + (attachmentContent.isEmpty() ? "" : attachmentContent.toString()))
                .advisors(advisor -> advisor
                        .param(ChatMemory.CONVERSATION_ID, chatId)
                        .param("messageId", messageId)
                );

        if (localToolCallbacks != null) {
            promptBuilder.toolCallbacks(localToolCallbacks);
        }
        Map<String,Object>toolContext=Collections.singletonMap("chatId",chatId);
        Flux<ChatResponse> aiFlux = promptBuilder.toolContext(toolContext)
                .stream()
                .chatResponse()
                .doOnNext(chatResponse -> {
                    var msg = chatResponse.getResult().getOutput();
                    Object reasoning = msg.getMetadata().get("reasoningContent");
                    if (reasoning!=null) reasoningContent.append(reasoning);
                    if (!msg.getText().isEmpty()) full.append(msg.getText());
//                    if (msg instanceof ZhiPuAiAssistantMessage zhipuMsg) {
//                        String content = zhipuMsg.getText() != null ? zhipuMsg.getText() : "";
//                        String reasoning = zhipuMsg.getReasoningContent() != null ? zhipuMsg.getReasoningContent() : "";
//                        if (!reasoning.isEmpty()) reasoningContent.append(reasoning);
//                        if (!content.isEmpty()) full.append(content);
//                    }
//                    if(!msg.getText().isEmpty())full.append(msg.getText());
                })
                .doFinally(signal -> {
                    // 保存聊天记录
                    if(chatSendReq.getIsImageGen())return;
                    String finalAnswer = "";
                    if (!reasoningContent.isEmpty()) {
                        reasoningContent.append("</Think>");
                        finalAnswer = "<Think>" + reasoningContent;
                    }
                    finalAnswer += full;
                    ChatEmbeddingDTO dto = new ChatEmbeddingDTO();
                    dto.setUserId(userId);
                    dto.setChatId(chatId);
                    dto.setUserMessage(message);
                    dto.setMemoryType("chat");
                    dto.setAssistantMessage(finalAnswer);
                    chatMessageService.saveAssistantMessage(chatId, finalAnswer);
                        // 可选：向向量库发送
    //                    rabbitTemplate.convertAndSend(
    //                            "ChatVectorStoreExchange",
    //                            "ChatVectorStore",
    //                            JSONUtil.toJsonStr(dto)
    //                    );
                })
                .cache(); // 热流

            return Flux.concat(prefixFlux, aiFlux)
            .onErrorResume(e -> Flux.empty());
        }

//     @Transactional
//    public Flux<ChatResponse> doChatStreamRegenerate(RegenDTO regenDTO, String userId) {
//         String userMessageId = regenDTO.getUserMessageId();
//         ChatMessage chatMessage = chatMessageService.getById(userMessageId);
//         UserMessage userMessage =KryoUtils.deserialize(chatMessage.getPayload());
//         String chatId = regenDTO.getChatId();
//        String message =userMessage.getText();
//        StringBuilder full = new StringBuilder();
//        StringBuilder attachmentContent = new StringBuilder();
//        StringBuilder reasoningContent=new StringBuilder();
//         List<AiAttachment> list = attachmentService.list(new LambdaQueryWrapper<AiAttachment>().eq(AiAttachment::getChatId, userMessageId));
//         if(list!=null&&!list.isEmpty())
//        {
//            for (AiAttachment aiAttachment : list) {
//                attachmentContent.append(attachmentReaderUtil.readContent(aiAttachment, regenDTO.getMessageId(), chatId, message));
//                attachmentContent.append("\n");
//            }
//        }
//        if(regenDTO.getIsThinking()){
//
//              ZhiPuAiChatOptions zhiPuAiChatOptions = ZhiPuAiChatOptions.builder().model("glm-4.7-FlashX")
//                      .temperature(0.6)
//                .thinking(ZhiPuAiApi.ChatCompletionRequest.Thinking.enabled()).build();
//              chatClientBuilder.defaultOptions(zhiPuAiChatOptions);
//        }
//        ChatClient chatClient = chatClientBuilder.build();
//        Flux<ChatResponse> aiFlux = chatClient.prompt().user(message)
//                .system(SystemPrompt.GENERAL_SYSTEM_PROMPT+(regenDTO.getAttachments().isEmpty()?"":attachmentContent.toString()))
//                .advisors(advisor -> advisor
//                        .param(ChatMemory.CONVERSATION_ID, chatId)
//                        .param("messageId", regenDTO.getMessageId())
//                )
//                .stream().chatResponse()
//                .doOnNext(chatResponse -> {
//                    String content = "";
//                    var msg = chatResponse.getResult().getOutput();
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
//                }
//                )
//                .doFinally(signal -> {
//                    String finalAnswer="";
//                    // 无论前端取消 / 断连 / 正常完成，这里都会执行
//                    if (!reasoningContent.isEmpty()) {
//                        reasoningContent.append("</Think>");
//                        finalAnswer="<Think>"+reasoningContent;
//                    }
//                    finalAnswer+=full;
//
//                    if (!finalAnswer.isEmpty()) {
//                        ChatEmbeddingDTO dto = new ChatEmbeddingDTO();
//                        dto.setUserId(userId);
//                        dto.setChatId(chatId);
//                        dto.setUserMessage(message);
//                        dto.setMemoryType("chat");
//                        dto.setAssistantMessage(finalAnswer);
//                        chatMessageService.updateMessage(chatId,finalAnswer,regenDTO.getAssistantMessageId(),"assistant");
//                        //修改助手回答，用户不需要
////                        rabbitTemplate.convertAndSend(
////                                "ChatVectorStoreExchange",
////                                "ChatVectorStore",
////                                JSONUtil.toJsonStr(dto)
////                        );
//                    }
//                })
//                .cache(); // 关键：让 Flux 变成“可复用的热流”
//        // 返回给前端的流（前端取消不会影响 aiFlux）
//        return aiFlux
//                .onErrorResume(e -> {
//                    // 前端断连时忽略 IOException
//                    return Flux.empty();
//                });
//    }
    @Transactional
    public Flux<ChatResponse> doChatStreamThinking(ChatSendReq chatSendReq, String userId) {

        String chatId = chatSendReq.getChatId();
        String message = chatSendReq.getMessage();
        Long messageId = Long.parseLong(chatSendReq.getMessageId());
        StringBuilder full = new StringBuilder();
        // AI 生成流永远跑完整
        StringBuilder attachmentContent = new StringBuilder();

        if(!chatSendReq.getAttachments().isEmpty())
        {
            List<AiAttachment> aiAttachments = attachmentService.listByIds(chatSendReq.getAttachments());
            for (AiAttachment aiAttachment : aiAttachments) {
                attachmentContent.append(attachmentReaderUtil.readContent(aiAttachment, messageId.toString(), chatId, message));
                attachmentContent.append("\n");
            }
        }
          if(chatSendReq.getIsThinking()){

              ZhiPuAiChatOptions zhiPuAiChatOptions = ZhiPuAiChatOptions.builder().model("glm-4.7")
                      .temperature(0.6)
                .thinking(ZhiPuAiApi.ChatCompletionRequest.Thinking.enabled()).build();
              chatClientBuilder.defaultOptions(zhiPuAiChatOptions);
        }
        ChatClient chatClient = chatClientBuilder.build();
        Flux<ChatResponse> aiFlux = chatClient.prompt().user(message)
//                .system(SystemPrompt.GENERAL_SYSTEM_PROMPT+(chatSendReq.getAttachments().isEmpty()?"":attachmentContent.toString()))
//                .advisors(advisor -> advisor
//                        .param(ChatMemory.CONVERSATION_ID, chatId)
//                        .param("messageId", messageId)
//                )
                .stream().chatResponse()
                .doOnNext(chunk -> full.append(chunk))
                .doFinally(signal -> {
                    // 无论前端取消 / 断连 / 正常完成，这里都会执行
                    String finalAnswer = full.toString();
//                    if (!finalAnswer.isEmpty()) {
//                        ChatEmbeddingDTO dto = new ChatEmbeddingDTO();
//                        dto.setUserId(userId);
//                        dto.setChatId(chatId);
//                        dto.setUserMessage(message);
//                        dto.setMemoryType("chat");
//                        dto.setAssistantMessage(finalAnswer);
//                        rabbitTemplate.convertAndSend(
//                                "ChatVectorStoreExchange",
//                                "ChatVectorStore",
//                                JSONUtil.toJsonStr(dto)
//                        );
//                    }
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

    public String doChatWithMcp(String message,String chatId)
    {
        ChatClient chatClient = chatClientBuilder.build();
        Map<String,Object> params = new HashMap<>();
        params.put("id",chatId);
        ChatResponse chatResponse = chatClient

                .prompt()
                .user(message)
//                .advisors(advisor->advisor.param(ChatMemory.CONVERSATION_ID,chatId))
//                .advisors(new MySimpleLoggerAdvisor()).tools(toolCallbackProvider)
                .call().chatResponse();
        return chatResponse.getResult().getOutput().getText();

    }
}
