package com.ai.aigent.agent;

import cn.hutool.json.JSONUtil;
import com.ai.aigent.agent.model.AgentEvent;
import com.ai.aigent.agent.model.AgentSessionRegistry;
import com.ai.aigent.agent.model.AgentState;
import com.ai.aigent.constant.AiConstants;
import com.ai.aigent.entity.domin.AiAttachment;
import com.ai.aigent.service.AgentMessageService;
import com.ai.aigent.service.AgentSessionService;
import com.ai.aigent.service.AiAttachmentService;
import com.ai.aigent.utils.AttachmentReaderUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.qcloud.cos.model.COSObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Data
@Slf4j
public abstract class BaseAgent {
    private String name;
    private String systemPrompt;
    private String nextStepPrompt;
    private AgentState agentState=AgentState.IDLE;
    private int currentStep=0;
    private volatile Disposable currentDisposable;
    private volatile Boolean cancelled;
    private AiAttachmentService attachmentService;
    private AttachmentReaderUtil attachmentReaderUtil;
    private int maxStep=16;
    private String SystemSuffix;
    private ChatClient chatClient;
    private String sessionId;
    private List<Media>mediaList;
    private  AgentSessionRegistry agentSessionRegistry;
    private SseEmitter emitter;
    private Consumer<List<AgentEvent>> onStepFinished;//步骤完成回调
    private  AgentMessageService agentMessageService;
    private  AgentSessionService agentSessionService;
    private List<Message>messageList=new ArrayList<>();
    protected void emit(AgentEvent event) {
        if (agentState == AgentState.FINISHED) return; // 已结束不发
        try {
            emitter.send(SseEmitter.event()
                    .name(event.getType())
                    .data(JSONUtil.toJsonStr(event)));
        }  catch (IOException e) {
        // 客户端已断开，标记取消，后续 doOnNext 不再发送
            log.warn("SSE 客户端已断开，停止推送");
            cancel();
        } catch (Exception e) {
            log.error("SSE 发送失败", e);
        }
    }
     public void cancel() {
        cancelled = true;
        if (currentDisposable != null && !currentDisposable.isDisposed()) {
            currentDisposable.dispose();
        }
    }
    public abstract String step();
    protected void cleanup(){
         agentState = AgentState.IDLE;
    }
     public SseEmitter runStream(String userPrompt,String sessionId,List<Long>attachments) {
        this.sessionId = sessionId;
        emitter = new SseEmitter(1000 * 60 * 8L);
        agentSessionRegistry.register(sessionId, this);
        emitter.onCompletion(() -> agentSessionRegistry.remove(sessionId));
        emitter.onTimeout(() -> {
            agentSessionRegistry.remove(sessionId);
            cancel(); // 超时也取消后台流
        });
        CompletableFuture.runAsync(() -> {
            try {
                this.agentState = AgentState.RUNNING;
                messageList.add(new UserMessage(userPrompt));
                  if (attachments != null && !attachments.isEmpty()) {
                      emit(AgentEvent.attachmentLoading()); // 通知前端"正在读取附件"
                      String attachmentContent = loadAttachments(attachments);
                      if (!attachmentContent.isEmpty()) {
                          this.setSystemSuffix(attachmentContent);
                      }
                      emit(AgentEvent.attachmentLoaded()); // 通知前端"附件读取完成"
                  }
                  this.setSystemPrompt(systemPrompt+(SystemSuffix!=null?"":SystemSuffix));
                  this.getMessageList().add(new SystemMessage(this.getSystemPrompt()));
                for (int i = 0; i < maxStep && agentState != AgentState.FINISHED; i++) {
                    currentStep = i + 1;
                    emit(AgentEvent.stepStart(currentStep));//发送步骤第几
                    log.info("Executing step {}/{}", currentStep, maxStep);
                    String stepResult = step();
                    if ("思考完成-无需行动".equals(stepResult)) break;
                }
                if (currentStep >= maxStep) {
                    agentState = AgentState.FINISHED;
                    emit(new AgentEvent("warn", "已达最大步骤 " + maxStep, null));//发送超步骤警告
                }
                emit(AgentEvent.done());//发送完成a
                emitter.complete();
            } catch (Exception e) {
                agentState = AgentState.ERROR;
                emit(new AgentEvent("error", e.getMessage(), null));
                emitter.completeWithError(e);//发送错误
            } finally {
                cleanup();
            }
        });
        return emitter;
    }
    private String loadAttachments(List<Long> attachmentIds) {
        StringBuilder attachmentContent = new StringBuilder();
        List<AiAttachment> aiAttachments = attachmentService.listByIds(attachmentIds);

        for (AiAttachment aiAttachment : aiAttachments) {
            if (aiAttachment.getFileType().startsWith("image/")) {
                try {
                    COSObject object = attachmentReaderUtil.getObject(AiConstants.bulk, aiAttachment.getFileUrl());
                    byte[] bytes = StreamUtils.copyToByteArray(object.getObjectContent());
                    mediaList.add(Media.builder()
                            .mimeType(MimeTypeUtils.parseMimeType(aiAttachment.getFileType()))
                            .id(aiAttachment.getId().toString())
                            .name(aiAttachment.getFileName())
                            .data(bytes)
                            .build());
                } catch (Exception e) {
                    log.error("读取图片附件失败: {}", aiAttachment.getFileName(), e);
                }
                continue;
            }
            try {
                attachmentContent.append(
                    attachmentReaderUtil.readContent(aiAttachment)
                ).append("\n");
            } catch (Exception e) {
                log.error("读取文本附件失败: {}", aiAttachment.getFileName(), e);
            }
        }
        return attachmentContent.toString();
    }
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

}
