package com.ai.aigent.agent;

import com.ai.aigent.agent.model.AgentState;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Data
@Slf4j
public abstract class BaseAgent {
    private String name;
    private String systemPrompt;
    private String nextStepPrompt;
    private AgentState agentState=AgentState.IDLE;
    private int currentStep=0;
    private int maxStep=16;
    private ChatClient chatClient;
    private List<Message>messageList=new ArrayList<>();
    public String run(String userPrompt)
    {
        if(this.agentState != AgentState.IDLE)
        {
            throw new RuntimeException("Can't run agent from state : "+this.agentState);
        }
        if(StringUtils.isBlank(userPrompt))
        {
            throw new RuntimeException("UserPrompt can't be empty ");
        }
        this.agentState=AgentState.RUNNING;
        messageList.add(new UserMessage(userPrompt));
        List<String>resultList=new ArrayList<>();
        try {
            for(int i=0;i<maxStep&&this.agentState!=AgentState.FINISHED;i++)
            {
                this.currentStep=i+1;
                log.info("Executing step {}/{}",currentStep,maxStep);
                String stepResult=step();
                String result = "Step " + currentStep + stepResult;
                resultList.add(result);
                if(stepResult.equals("思考完成-无需行动"))
                {
                    break;
                }
            }
            if(currentStep>=maxStep)
            {
                this.agentState=AgentState.FINISHED;
                resultList.add("Terminated:Reached max steps ("+maxStep +")");
            }
            return String.join("\n",resultList);
        }
        catch (Exception e)
        {
            this.agentState=AgentState.ERROR;
            log.info("error executing agent",e);
            return "执行错误"+e.getMessage();
        }
        finally {

            this.cleanup();
        }

    }
    public SseEmitter runStream(String userPrompt)
    {
        SseEmitter sseEmitter=new SseEmitter(10000*60*5L);
        CompletableFuture.runAsync(()->{
            try {
             if(this.agentState!=AgentState.IDLE)
            {
                sseEmitter.send("无法从状态运行代理："+this.agentState);
                sseEmitter.complete();
                return ;
            }
            if(StringUtils.isBlank(userPrompt))
            {
                sseEmitter.send("不能使用空提示词");
                sseEmitter.complete();
                return ;
            }
            }catch (Exception e)
            {
                sseEmitter.completeWithError(e);

            }

        this.agentState=AgentState.RUNNING;
        messageList.add(new UserMessage(userPrompt));
        List<String>resultList=new ArrayList<>();
        try {
            for(int i=0;i<maxStep&&this.agentState!=AgentState.FINISHED;i++)
            {
                this.currentStep=i+1;
                log.info("Executing step {}/{}",currentStep,maxStep);
                String stepResult=step();
                String result="Step "+currentStep+stepResult;
                resultList.add(result);
                if(stepResult.equals("思考完成-无需行动"))
                {
                    break;
                }
                sseEmitter.send(result);
            }
            if(currentStep>=maxStep)
            {
                this.agentState=AgentState.FINISHED;
                resultList.add("Terminated:Reached max steps ("+maxStep +")");
                sseEmitter.send("执行结束达到最大步骤"+maxStep+"步");
            }
            sseEmitter.complete();
        }
        catch (Exception e)
        {
            this.agentState=AgentState.ERROR;
            log.info("error executing agent",e);
            try {
                sseEmitter.send("执行错误"+e.getMessage());
                sseEmitter.complete();
            } catch (IOException ex) {
                sseEmitter.completeWithError(ex);
            }
        }
        finally {

            this.cleanup();
        }

        });
        sseEmitter.onTimeout(()->{
            this.agentState=AgentState.ERROR;
            this.cleanup();
            log.error("sse emitter timeout");
        });
        sseEmitter.onCompletion(()->{
            if(this.agentState==AgentState.RUNNING)
            {
                this.agentState=AgentState.FINISHED;
            }
            this.cleanup();
            log.info("sse emitter completed");
        });
        return sseEmitter;
    }
    public abstract String step();
    protected void cleanup(){

    }


}
