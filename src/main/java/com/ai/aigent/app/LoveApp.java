package com.ai.aigent.app;

import com.ai.aigent.AiCientConstans;
import com.ai.aigent.advisor.JdbcChatMemoryAdvisor;
import com.ai.aigent.advisor.MySimpleLoggerAdvisor;
import com.ai.aigent.advisor.SummaryMemoryAdvisor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;
@Component
@Slf4j
public class LoveApp {
    private final ChatClient chatClient;
    private  final String SYSTEM_PROMPT="扮演深耕恋爱心理领域的专家。开场向用户表明身份，告知用户可倾诉恋爱难题。"+
"围绕单身、恋爱、己婚三种状态提问：单身状态询问社交圈拓展及追求心仪对象的困扰："+
"恋爱状态询问沟通、习惯差异引发的矛盾：已婚状态询问家庭责任与亲属关系处理的问题。"+
"引导用户详述事情经过、对方反应及自身想法，以便给出专属解决方案。";
    private final JdbcChatMemoryAdvisor chatMemory;
//    @Autowired
//    private OllamaChatModel ollamaChatModel;
    private final SummaryMemoryAdvisor summaryMemoryAdvisor;
    public LoveApp(@Qualifier("dashscopeChatModel") ChatModel dashscpoeChatModel, JdbcChatMemoryAdvisor chatMemory, SummaryMemoryAdvisor summaryMemoryAdvisor) {
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

                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(this.chatMemory),
                        new SafeGuardAdvisor(forbiddenWords,"内容涉及敏感信息，请更换表述",-1),

                        summaryMemoryAdvisor,new MySimpleLoggerAdvisor()
                )
                .build();

    }
    public String doChat(String message,String chatId)
    {
        ChatResponse chatResponse = chatClient.prompt().user(message).advisors
                (spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, AiCientConstans.SHORT_MEMORY_LENGTH))

                .call().chatResponse();
        String text = chatResponse.getResult().getOutput().getText();

        String string = chatResponse.getResult().getOutput().toString();
        return text;

    }

    record loveReport(String title, List<String> suggestions){}
     public  loveReport doChatWithReport(String message,String chatId)
        {
            loveReport entity = chatClient.prompt()
                    .system(SYSTEM_PROMPT + "每次对话后都要生成恋爱结果，标题为{用户名}的恋爱报告，内容为建议列表")
                    .user(message).advisors
                            (spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                                    .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, AiCientConstans.SHORT_MEMORY_LENGTH)).call().entity(loveReport.class);

//            log.info(entity);
            return entity;

        }

    @Resource
    private VectorStore loveAppVectorStore;
    public String doChatWithRag(String message,String chatId)
    {
        Map<String,Object> params = new HashMap<>();
        params.put("id",chatId);
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, AiCientConstans.SHORT_MEMORY_LENGTH)


                .advisors(new QuestionAnswerAdvisor(loveAppVectorStore))).toolContext(params)

                .call().chatResponse();
        return chatResponse.getResult().getOutput().getText();

    }


}
