package com.ai.aigent.agent;

import cn.hutool.json.JSONUtil;
import com.ai.aigent.agent.model.AgentAttachment;
import com.ai.aigent.agent.model.AgentEvent;
import com.ai.aigent.agent.model.AgentSessionRegistry;
import com.ai.aigent.agent.model.AgentState;
import com.ai.aigent.constant.AiConstants;
import com.ai.aigent.constant.FileConstant;
import com.ai.aigent.entity.domain.AiAttachment;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

@Data
@Slf4j
public abstract class BaseAgent {
    private static final int MAX_ATTACHMENT_PROMPT_LENGTH = 1024;
    private com.ai.aigent.sandbox.SandboxManager sandboxManager;
    private String name;
    private String systemPrompt;
    private String nextStepPrompt;
    private AgentState agentState=AgentState.IDLE;
    private int currentStep=0;
    private volatile Disposable currentDisposable;
    private volatile Boolean cancelled = false;
    private AiAttachmentService attachmentService;
    private AttachmentReaderUtil attachmentReaderUtil;
    private int maxStep=20;
    private String systemSuffix;
    private String userId;
    private ChatClient chatClient;
    private String sessionId;
    private Long runId;
    private String sandboxRunToken;
    private List<Media>mediaList = new ArrayList<>();
    private  AgentSessionRegistry agentSessionRegistry;
    private SseEmitter emitter;
    private Executor agentExecutor = ForkJoinPool.commonPool();
    private Consumer<List<AgentEvent>> onStepFinished;//步骤完成回调
    private  AgentMessageService agentMessageService;
    private  AgentSessionService agentSessionService;
    private List<Message>messageList=new ArrayList<>();
    private List<AgentAttachment> sessionAttachments = new ArrayList<>();

    protected void emit(AgentEvent event) {
        try {
            Map<String, Object> meta = event.getMeta() == null
                    ? new HashMap<>() : new HashMap<>(event.getMeta());
            event.setMeta(meta);
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
     public synchronized void cancel() {
        cancelled = true;
        try {
            if (sandboxManager != null && sessionId != null && sandboxRunToken != null)
                sandboxManager.cancel(sessionId, sandboxRunToken);
        } finally {
            if (currentDisposable != null && !currentDisposable.isDisposed()) currentDisposable.dispose();
        }
    }
    public abstract String step();
    protected void cleanup(){
         agentState = AgentState.IDLE;
         currentStep = 0;
         currentDisposable = null;
         cancelled = false;
         systemSuffix = null;
         userId = null;
         sessionId = null;
         runId = null;
         sandboxRunToken = null;
         emitter = null;
         mediaList.clear();
         messageList.clear();
         sessionAttachments.clear();
    }
     public SseEmitter agentRun(String userPrompt, String sessionId, Long runId, List<Long>attachments, String userId) {
        synchronized (this) {
            initializeRunState(sessionId, userId);
            this.runId = Objects.requireNonNull(runId, "runId 不能为空");
            if (Boolean.TRUE.equals(cancelled)) throw new java.util.concurrent.CancellationException("任务已取消");
            if (sandboxManager != null) sandboxRunToken = sandboxManager.beginRun(sessionId, userId, runId);
        }

        emitter = new SseEmitter(1000 * 60 * 8L);
        emitter.onTimeout(() -> {
            cancel(); // 超时也取消后台流
        });
        try {
        CompletableFuture.runAsync(() -> {
            try {
                this.agentState = AgentState.RUNNING;
                restoreSessionAttachments();
                if (attachments != null && !attachments.isEmpty()) {
                    emit(AgentEvent.attachmentLoading()); // 通知前端"正在读取附件"
                    mergeSessionAttachments(loadAttachments(attachments));
                    emit(AgentEvent.attachmentLoaded()); // 通知前端"附件读取完成"
                }
                this.getMessageList().add(new SystemMessage(buildRuntimeSystemPrompt()));
                this.getMessageList().add(new UserMessage(userPrompt));
                for (int i = 0; i < maxStep && agentState != AgentState.FINISHED && !Boolean.TRUE.equals(cancelled); i++) {
                    currentStep = i + 1;
                    emit(AgentEvent.stepStart(currentStep));//发送步骤第几
//                    log.info("Executing step {}/{}", currentStep, maxStep);
                    String stepResult = step();
                    if ("思考完成-无需行动".equals(stepResult)) break;
                }
                if (Boolean.TRUE.equals(cancelled)) {
                    emit(new AgentEvent("cancelled", "任务已取消", null));
                } else {
                    if (currentStep >= maxStep) {
                        agentState = AgentState.FINISHED;
                        emit(new AgentEvent("warn", "已达最大步骤 " + maxStep, null));
                    }
                    emit(AgentEvent.done());
                }
                emitter.complete();
            } catch (Exception e) {
                agentState = AgentState.ERROR;
                emit(new AgentEvent("error", e.getMessage(), null));
                emitter.completeWithError(e);//发送错误
            } finally {
                if (sandboxManager != null) sandboxManager.finishRun(sessionId, sandboxRunToken);
                agentSessionRegistry.remove(sessionId, this);
                cleanup();
            }
        }, agentExecutor);
        } catch (RuntimeException | Error e) {
            if (sandboxManager != null) sandboxManager.finishRun(sessionId, sandboxRunToken);
            throw e;
        }
        return emitter;
    }
    private void initializeRunState(String sessionId, String userId) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.currentStep = 0;
        this.currentDisposable = null;
        this.systemSuffix = null;
        this.mediaList = new ArrayList<>();
        this.messageList = new ArrayList<>();
        this.sessionAttachments = new ArrayList<>();
    }
    private String buildRuntimeSystemPrompt() {
        return systemPrompt + (systemSuffix == null ? "" : systemSuffix) + formatSessionAttachments();
    }
    private void restoreSessionAttachments() {
        if (attachmentService == null || sessionId == null) {
            return;
        }
        List<AiAttachment> attachments = attachmentService.lambdaQuery()
                .eq(AiAttachment::getConservationId, sessionId)//恢复该会话历轮附件
                .eq(AiAttachment::getUserId, userId)
                .orderByAsc(AiAttachment::getCreatedAt)
                .list();
        if (attachments != null) {
            for (AiAttachment attachment : attachments) {
                if (attachment.getFileType() != null && attachment.getFileType().startsWith("image/")) {
                    sessionAttachments.add(toAgentAttachment(attachment));
                } else if (attachment.getFileName() != null && Files.isRegularFile(
                        Paths.get(FileConstant.ATTACHMENTS_SAVE_DIR, sessionId, attachment.getFileName()))) {
                    sessionAttachments.add(toAgentAttachment(attachment));
                }
            }
        }
    }
    private void mergeSessionAttachments(List<AgentAttachment> attachments) {
        Map<Long, AgentAttachment> merged = new LinkedHashMap<>();
        for (AgentAttachment attachment : sessionAttachments) {
            merged.put(attachment.getId(), attachment);
        }
        for (AgentAttachment attachment : attachments) {
            merged.put(attachment.getId(), attachment);
        }
        sessionAttachments = new ArrayList<>(merged.values());
    }
    String formatSessionAttachments() {
        if (sessionAttachments.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder("\n当前会话可用的附件（按最近添加顺序）：\n");
        List<AgentAttachment> recentFirst = new ArrayList<>(sessionAttachments);
        Collections.reverse(recentFirst);
        for (AgentAttachment attachment : recentFirst) {
            String name = String.valueOf(attachment.getFileName()).replace('\n', ' ').replace('\r', ' ');
            String type = String.valueOf(attachment.getFileType()).replace('\n', ' ').replace('\r', ' ');
            String line = "- 文件名: " + name + "；ID: " + attachment.getId()
                    + "；类型: " + type + "；大小: " + attachment.getFileSize() + " 字节";
            if (type.startsWith("image/")) {
                line += "；图片附件";
            } else {
                line += "；路径: /attachments/" + name;
            }
            line += "\n";
            if (result.length() + line.length() > MAX_ATTACHMENT_PROMPT_LENGTH) {
                result.append(line, 0, MAX_ATTACHMENT_PROMPT_LENGTH - result.length());
                break;
            }
            result.append(line);
        }
        return result.toString();
    }
    private AgentAttachment toAgentAttachment(AiAttachment source) {
        AgentAttachment attachment = new AgentAttachment();
        attachment.setId(source.getId());
        attachment.setFileName(source.getFileName());
        attachment.setFileSize(source.getFileSize());
        attachment.setFileType(source.getFileType());
        return attachment;
    }
    private List<AgentAttachment> loadAttachments(List<Long> attachmentIds) {
        List<AiAttachment> aiAttachments = attachmentService.listByIds(attachmentIds);
        List<AgentAttachment>agentAttachments=new ArrayList<>();
        for (AiAttachment aiAttachment : aiAttachments) {
            if (!Objects.equals(aiAttachment.getConservationId(), sessionId) ||
                !Objects.equals(String.valueOf(aiAttachment.getUserId()), userId))
                throw new SecurityException("附件不属于当前会话用户");
            if (!Objects.equals(aiAttachment.getChatId(), String.valueOf(runId))) {
                throw new IllegalArgumentException("附件不属于当前 runId");
            }
            if (aiAttachment.getFileName() == null || aiAttachment.getFileName().contains("/") ||
                aiAttachment.getFileName().contains("\\") || aiAttachment.getFileName().equals(".."))
                throw new IllegalArgumentException("附件文件名非法");
            if (aiAttachment.getFileType().startsWith("image/")) {//图片上传cos直接放到记忆数组
                try {
                    COSObject object = attachmentReaderUtil.getObject(AiConstants.bulk, aiAttachment.getFileUrl());
                    byte[] bytes = StreamUtils.copyToByteArray(object.getObjectContent());
                    mediaList.add(Media.builder()
                            .mimeType(MimeTypeUtils.parseMimeType(aiAttachment.getFileType()))
                            .id(aiAttachment.getId().toString())
                            .name(aiAttachment.getFileName())
                            .data(bytes)
                            .build());
                    agentAttachments.add(toAgentAttachment(aiAttachment));//图片也最好保存下
                } catch (Exception e) {
                    log.error("读取图片附件失败: {}", aiAttachment.getFileName(), e);
                }
                continue;
            }
            try {
                Path targetPath = Paths.get(FileConstant.ATTACHMENTS_SAVE_DIR, sessionId,aiAttachment.getFileName());//保存这个文件
                COSObject object = attachmentReaderUtil.getObject(AiConstants.bulk, aiAttachment.getFileUrl());
                try (InputStream inputStream = object.getObjectContent()) {
                    Files.copy(
                            inputStream,
                            targetPath,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                }
                agentAttachments.add(toAgentAttachment(aiAttachment));
            } catch (Exception e) {
                log.error("读取文本附件失败: {}", aiAttachment.getFileName(), e);
            }
        }
        return agentAttachments;
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
        messageList = new ArrayList<>();
        messageList.add(new SystemMessage(buildRuntimeSystemPrompt()));
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
