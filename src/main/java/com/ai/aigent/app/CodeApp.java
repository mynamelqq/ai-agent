package com.ai.aigent.app;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.ai.aigent.advisor.JdbcChatMemoryAdvisor;
import com.ai.aigent.advisor.MySimpleLoggerAdvisor;
import com.ai.aigent.advisor.SummaryMemoryAdvisor;
import com.ai.aigent.advisor.VectorStoreMemoryAdvisor;
import com.ai.aigent.entity.AiAttachment;
import com.ai.aigent.entity.ChatMessage;
import com.ai.aigent.entity.dto.ChatEmbeddingDTO;
import com.ai.aigent.entity.dto.ChatSendReq;
import com.ai.aigent.service.AiAttachmentService;
import com.ai.aigent.service.ChatMessageService;
import com.ai.aigent.utils.AttachmentReaderUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.DefaultChatClientBuilder;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.ThinkOption;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.zhipuai.ZhiPuAiAssistantMessage;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    public CodeApp(@Qualifier("zhiPuAiChatModel") ChatModel dashscpoeChatModel, JdbcChatMemoryAdvisor chatMemory, SummaryMemoryAdvisor summaryMemoryAdvisor,VectorStoreMemoryAdvisor vectorStoreMemoryAdvisor) {
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
//    public String doChat(String message,String chatId)
//    {
//        ChatResponse chatResponse = chatClient.prompt().user(message)
//                .call().chatResponse();
//        String text = chatResponse.getResult().getOutput().getText();
//
//        String string = chatResponse.getResult().getOutput().toString();
//        return text;
//
//    }
    @Resource
    private RabbitTemplate rabbitTemplate;
    @Resource
    private AiAttachmentService attachmentService;
    @Retryable(
    value = {WebClientResponseException.TooManyRequests.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 2000, multiplier = 2)
)
    @Transactional
    public Flux<ChatResponse> doChatStream(ChatSendReq chatSendReq, String userId) {
        String chatId = chatSendReq.getChatId();
        String message = chatSendReq.getMessage();
        Long messageId = Long.parseLong(chatSendReq.getMessageId());
        StringBuilder full = new StringBuilder();
        // AI 生成流永远跑完整
        StringBuilder attachmentContent = new StringBuilder();
        StringBuilder reasoningContent=new StringBuilder();
        if(!chatSendReq.getAttachments().isEmpty())
        {
            List<AiAttachment> aiAttachments = attachmentService.listByIds(chatSendReq.getAttachments());
            for (AiAttachment aiAttachment : aiAttachments) {
                attachmentContent.append(attachmentReaderUtil.readContent(aiAttachment, messageId.toString(), chatId, message));
                attachmentContent.append("\n");
            }
        }

        if(chatSendReq.getIsThinking()){

              ZhiPuAiChatOptions zhiPuAiChatOptions = ZhiPuAiChatOptions.builder().model("glm-4.7-FlashX")
                      .temperature(0.6)
                .thinking(ZhiPuAiApi.ChatCompletionRequest.Thinking.enabled()).build();
              chatClientBuilder.defaultOptions(zhiPuAiChatOptions);
        }
        ChatClient chatClient = chatClientBuilder.build();
        Flux<ChatResponse> aiFlux = chatClient.prompt().user(message)
                .system(SystemPrompt.GENERAL_SYSTEM_PROMPT+(chatSendReq.getAttachments().isEmpty()?"":attachmentContent.toString()))
                .advisors(advisor -> advisor
                        .param(ChatMemory.CONVERSATION_ID, chatId)
                        .param("messageId", messageId)
                )
                .stream().chatResponse()
                .doOnNext(chatResponse -> {
                    String content = "";
                    var msg = chatResponse.getResult().getOutput();
                   if (msg instanceof ZhiPuAiAssistantMessage zhipuMsg) {
                       if (zhipuMsg.getText()!=null) {
                            content = zhipuMsg.getText();
                        }
                        String reasoning = "";
                        if (zhipuMsg.getReasoningContent()!=null) {
                            reasoning = zhipuMsg.getReasoningContent();
                        }
                        if (!reasoning.isEmpty()) reasoningContent.append(reasoning);
                        if (!content.isEmpty()) full.append(content);
                   }
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
                        chatMessageService.saveAssistantMessage(chatId, finalAnswer);
                        chatMessageService.saveMessage(chatId,new UserMessage(message),messageId.toString());
//                        rabbitTemplate.convertAndSend(
//                                "ChatVectorStoreExchange",
//                                "ChatVectorStore",
//                                JSONUtil.toJsonStr(dto)
//                        );
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
     @Transactional
    public Flux<ChatResponse> doChatStreamRegenerate(ChatSendReq chatSendReq, String userId) {
        String chatId = chatSendReq.getChatId();
        String message = chatSendReq.getMessage();
        Long messageId = Long.parseLong(chatSendReq.getMessageId());
        StringBuilder full = new StringBuilder();
        StringBuilder attachmentContent = new StringBuilder();
        StringBuilder reasoningContent=new StringBuilder();
        if(!chatSendReq.getAttachments().isEmpty())
        {
            List<AiAttachment> aiAttachments = attachmentService.listByIds(chatSendReq.getAttachments());
            for (AiAttachment aiAttachment : aiAttachments) {
                attachmentContent.append(attachmentReaderUtil.readContent(aiAttachment, messageId.toString(), chatId, message));
                attachmentContent.append("\n");
            }
        }
        if(chatSendReq.getIsThinking()){

              ZhiPuAiChatOptions zhiPuAiChatOptions = ZhiPuAiChatOptions.builder().model("glm-4.7-FlashX")
                      .temperature(0.6)
                .thinking(ZhiPuAiApi.ChatCompletionRequest.Thinking.enabled()).build();
              chatClientBuilder.defaultOptions(zhiPuAiChatOptions);
        }
        ChatClient chatClient = chatClientBuilder.build();
        Flux<ChatResponse> aiFlux = chatClient.prompt().user(message)
                .system(SystemPrompt.GENERAL_SYSTEM_PROMPT+(chatSendReq.getAttachments().isEmpty()?"":attachmentContent.toString()))
                .advisors(advisor -> advisor
                        .param(ChatMemory.CONVERSATION_ID, chatId)
                        .param("messageId", messageId)
                )
                .stream().chatResponse()
                .doOnNext(chatResponse -> {
                    String content = "";
                    var msg = chatResponse.getResult().getOutput();
                   if (msg instanceof ZhiPuAiAssistantMessage zhipuMsg) {
                       if (zhipuMsg.getText()!=null) {
                            content = zhipuMsg.getText();
                        }
                        String reasoning = "";
                        if (zhipuMsg.getReasoningContent()!=null) {
                            reasoning = zhipuMsg.getReasoningContent();
                        }
                        if (!reasoning.isEmpty()) reasoningContent.append(reasoning);
                        if (!content.isEmpty()) full.append(content);
                   }
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
                        chatMessageService.saveAssistantMessage(chatId, finalAnswer);
                        chatMessageService.saveMessage(chatId,new UserMessage(message),messageId.toString());
//                        rabbitTemplate.convertAndSend(
//                                "ChatVectorStoreExchange",
//                                "ChatVectorStore",
//                                JSONUtil.toJsonStr(dto)
//                        );
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
//                .advisors(advisor->advisor.param(ChatMemory.CONVERSATION_ID,chatId))
//                .advisors(new MySimpleLoggerAdvisor()).tools(toolCallbackProvider)
                .call().chatResponse();
        return chatResponse.getResult().getOutput().getText();

    }
}
