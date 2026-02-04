package com.ai.aigent.app;

import cn.hutool.json.JSONUtil;
import com.ai.aigent.advisor.JdbcChatMemoryAdvisor;
import com.ai.aigent.advisor.MySimpleLoggerAdvisor;
import com.ai.aigent.advisor.SummaryMemoryAdvisor;
import com.ai.aigent.advisor.VectorStoreMemoryAdvisor;
import com.ai.aigent.entity.dto.ChatEmbeddingDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
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
    private final ChatClient chatClient;
    private final JdbcChatMemoryAdvisor chatMemory;
//    @Resource
//    private DashScopeChatModel dashscopeChatModel;
    //    @Autowired
//    @Resource
//    private OllamaChatModel ollamaChatModel;
    @Resource
    @Qualifier("openAiChatModel")
    private OpenAiChatModel openAiChatModel;
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

        chatClient=ChatClient.builder(dashscpoeChatModel)
                .defaultSystem(SystemPrompt.GENERAL_SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                    vectorStoreMemoryAdvisor,
//                        new SafeGuardAdvisor(forbiddenWords,"内容涉及敏感信息，请更换表述",-1),
                        summaryMemoryAdvisor,new MySimpleLoggerAdvisor()
                )
                .build();

    }

    public String genTitle(String args)  {
        return  openAiChatModel.call(new Prompt(ChatPrompt.titleGeneratePrompt+args)).getResult().getOutput().getText();
    }
    public String doChat(String message,String chatId)
    {


        ChatResponse chatResponse = chatClient.prompt().user(message)
                .call().chatResponse();
        String text = chatResponse.getResult().getOutput().getText();

        String string = chatResponse.getResult().getOutput().toString();
        return text;

    }
    @Resource
    private RabbitTemplate rabbitTemplate;
    public Flux<String> doChatStream(String message, String chatId,String userId)
    {
        StringBuilder full = new StringBuilder();
        Flux<String> content = chatClient.prompt().user(message)

                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, chatId))

                .stream().content()
                .doOnNext((chunk)->{full.append(chunk);})
                .doOnComplete(()->{
                    String finalAnswer = full.toString();


                    ChatEmbeddingDTO chatEmbeddingDTO = new ChatEmbeddingDTO();
                    chatEmbeddingDTO.setUserId(userId);
                    chatEmbeddingDTO.setChatId(chatId);
                    chatEmbeddingDTO.setUserMessage(message);
                    chatEmbeddingDTO.setMemoryType("chat");
                    chatEmbeddingDTO.setAssistantMessage(finalAnswer);
                    String jsonStr = JSONUtil.toJsonStr(chatEmbeddingDTO);

                    rabbitTemplate.convertAndSend(
                              "ChatVectorStoreExchange",  // Exchange 名称
                        "ChatVectorStore",jsonStr
                    );
                });
        return content;
    }
    public Flux<String> doChatStreamUser(String message, String chatId)
    {
        return chatClient.prompt().user(message)
                .advisors(advisor->advisor.param(ChatMemory.CONVERSATION_ID,chatId))
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
        Map<String,Object> params = new HashMap<>();
        params.put("id",chatId);
        ChatResponse chatResponse = chatClient

                .prompt()
                .user(message)
                .advisors(advisor->advisor.param(ChatMemory.CONVERSATION_ID,chatId))
                .advisors(new MySimpleLoggerAdvisor()).tools(toolCallbackProvider)
                .call().chatResponse();
        return chatResponse.getResult().getOutput().getText();

    }
}
