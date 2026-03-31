package com.ai.aigent.app;

import cn.hutool.core.io.resource.InputStreamResource;
import cn.hutool.json.JSONUtil;
import com.ai.aigent.advisor.JdbcChatMemoryAdvisor;
import com.ai.aigent.advisor.MySimpleLoggerAdvisor;
import com.ai.aigent.advisor.SummaryMemoryAdvisor;
import com.ai.aigent.advisor.VectorStoreMemoryAdvisor;
import com.ai.aigent.constant.AiConstants;
import com.ai.aigent.entity.VO.ChatResult;
import com.ai.aigent.entity.domin.AiAttachment;
import com.ai.aigent.entity.domin.ChatMessage;
import com.ai.aigent.entity.domin.UserSetting;
import com.ai.aigent.entity.dto.ChatEmbeddingDTO;
import com.ai.aigent.entity.dto.ChatSendReq;
import com.ai.aigent.entity.dto.RegenDTO;
import com.ai.aigent.service.*;
import com.ai.aigent.tools.FileOperationTool;
import com.ai.aigent.tools.ImageGenerateTool;
import com.ai.aigent.tools.WebScrapingTool;
import com.ai.aigent.tools.WebSearchTool;
import com.ai.aigent.utils.AttachmentReaderUtil;
import com.ai.aigent.utils.KryoUtils;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qcloud.cos.model.COSObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
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
import prompt.TonePrompt;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
@Slf4j
public class SpringAiCode {
    static final String ANALYSIS_MARKER = "analysis";
    static final String ASSISTANT_ANALYSIS_MARKER = "assistantanalysis";
    static final String ASSISTANT_COMMENTARY_MARKER = "assistantcommentary";
    static final String ASSISTANT_FINAL_MARKER = "assistantfinal";
    enum WrappedTextMode {
        UNDECIDED,
        NORMAL,
        REASONING,
        COMMENTARY,
        FINAL
    }
    static final class StreamTextParseState {
        private WrappedTextMode mode = WrappedTextMode.UNDECIDED;
        private final StringBuilder pending = new StringBuilder();
    }

    static final class ParsedTextChunk {
        private final String reasoning;
        private final String text;

        ParsedTextChunk(String reasoning, String text) {
            this.reasoning = reasoning;
            this.text = text;
        }

        String reasoning() {
            return reasoning;
        }

        String text() {
            return text;
        }
    }

    private ChatClient.Builder chatClientBuilder;
    private final JdbcChatMemoryAdvisor chatMemory;
    @Resource
    private ToolCallbackProvider toolCallbackProvider;
    //    @Autowired
//    @Resource
//    private OllamaChatModel ollamaChatModel;
    public OpenAiChatModel openAiChatModel;
    private OpenAiChatOptions.Builder chatOptionsBuilder;
    @Resource
    @Qualifier("zhiPuAiChatModel")
    private ZhiPuAiChatModel zhiPuAiChatModel;
    @Resource
    private VectorStore codeAppVectorStore;
    private final SummaryMemoryAdvisor summaryMemoryAdvisor;
    private final VectorStoreMemoryAdvisor vectorStoreMemoryAdvisor;
    @Autowired
    private DashScopeChatModel dashScopeChatModel;
    @Autowired
    private UserUsageDailyService dailyService;
    @Autowired
    private UsersService usersService;
    @Autowired
    private UserSettingService userSettingService;
    public SpringAiCode(JdbcChatMemoryAdvisor chatMemory,
                        SummaryMemoryAdvisor summaryMemoryAdvisor, VectorStoreMemoryAdvisor vectorStoreMemoryAdvisor, OpenAiChatModel openAiChatModel) {
        this.chatMemory=chatMemory;
        this.summaryMemoryAdvisor = summaryMemoryAdvisor;
        this.vectorStoreMemoryAdvisor=vectorStoreMemoryAdvisor;
        chatClientBuilder = ChatClient.builder(openAiChatModel).defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build(), new MySimpleLoggerAdvisor());
//        InputStream resourceAsStream = this.getClass().getResourceAsStream("/forbiddenWords.txt");
//        List<String> forbiddenWords=new ArrayList<String>();
//        try {
//            BufferedReader br=new BufferedReader(new InputStreamReader(resourceAsStream));
//            String line;
//            while ((line = br.readLine()) != null) {
//                line = line.trim().toLowerCase();
//                if (!line.isEmpty() && !line.startsWith("#")) { // 可跳过空行和注释
//                    forbiddenWords.add(line);
//                }
//            }
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
    }
    public String genTitle(String args)  {
        return  zhiPuAiChatModel
                .stream(new Prompt(ChatPrompt.titleGeneratePrompt+args))
                .collectList().block().stream().map(response -> response.getResult().getOutput().getText())
            .collect(Collectors.joining());
    }
    @Autowired
    private WebSearchTool webSearchTool;
    @Autowired
    private WebScrapingTool webScrapingTool;
    @Resource
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private ImageGenerateTool imageGenerateTool;
    @Resource
    private AiAttachmentService attachmentService;
    @Autowired
    private FileOperationTool fileOperationTool;
    public DashScopeChatOptions thinking()
    {
        return DashScopeChatOptions.builder()
                    .model("qwq-plus")
                    .enableThinking(true).build();
    }
    @Transactional
   public Flux<ChatResult> doChatStream(ChatSendReq chatSendReq, String userId, String type) {
        String chatId = chatSendReq.getChatId();
        Long messageId = Long.parseLong(chatSendReq.getMessageId());
        String message = chatSendReq.getMessage();
        UserSetting setting;
        AtomicReference<Boolean>isToolAuto=new AtomicReference<>(true);
        // 1️⃣ 前缀 SSE：附件加载骨架
        Flux<ChatResult> prefixFlux = Flux.empty();
        if (!chatSendReq.getAttachments().isEmpty()) {
            ChatResult attachmentChat = new ChatResult();
            attachmentChat.setText("<<<ATTACHMENT_LOADING>>>");
            prefixFlux = Flux.just(attachmentChat);
        }
        chatOptionsBuilder= OpenAiChatOptions.builder();
        String style= "";
        String extendedPrompt="";
         setting= userSettingService.getByUserId(userId,type);
         if(setting!=null){
             chatOptionsBuilder.temperature(setting.getTemperature())
//                     .maxCompletionTokens(setting.getMaxTokens())
                     .frequencyPenalty(setting.getFrequencyPenalty())
//                         .topK(setting.getTopk())
                     .model(setting.getChatModel());//maxTokens
//             if(supportsReasoning(setting.getChatModel())){
//                 chatOptionsBuilder.maxCompletionTokens(setting.getMaxTokens());
//             }
//             else chatOptionsBuilder.maxTokens(setting.getMaxTokens());
             style = switch (setting.getTone()) {
                 case "professional" -> TonePrompt.professional;
                 case "detailed" -> TonePrompt.detailed;
                 case "teaching" -> TonePrompt.teaching;
                 case "friendly"-> TonePrompt.friendly;
                 case "funny"-> TonePrompt.funny;
                 case "neutral"-> TonePrompt.neutral;
                 case "sharp"-> TonePrompt.sharp;
                 case "critical"-> TonePrompt.critical;
                 case "poisonous"->TonePrompt.poisonous;
                 default -> TonePrompt.concise;
             };
             if(setting.getIsSummary()){
                 chatClientBuilder.defaultAdvisors(summaryMemoryAdvisor);
             }
             if(setting.getIsExtend()){
                 extendedPrompt=SystemPrompt.ExtendPrompt;
             }
             if(setting.getToolMode().equals("off")){isToolAuto.set(false);}
         }
        ChatClient chatClient = chatClientBuilder.build();
        // 3️⃣ 动态工具挂载
        ToolCallback[] localToolCallbacks;
        if (Boolean.TRUE.equals(chatSendReq.getIsImageGen())) {
            localToolCallbacks = ToolCallbacks.from(imageGenerateTool,webScrapingTool);
        } else if (Boolean.TRUE.equals(chatSendReq.getIsSearch())) {
            localToolCallbacks = ToolCallbacks.from(webSearchTool,webScrapingTool);
        } else if (Boolean.TRUE.equals(chatSendReq.getIsGenerateFile())) {
            localToolCallbacks = ToolCallbacks.from(fileOperationTool,webSearchTool);
        } else {
            localToolCallbacks = ToolCallbacks.from(webScrapingTool);
        }
        // 4️⃣ 先存用户消息
        chatMessageService.saveMessage(chatSendReq);
        // 提到外面，生命周期清晰
        StringBuilder full = new StringBuilder();
        StringBuilder reasoningContent = new StringBuilder();
        List<Media> mediaList = new ArrayList<>();
        AtomicReference<Usage> finalUsage = new AtomicReference<>();
        AtomicReference<Integer> token = new AtomicReference<>(0);
        AtomicReference<Integer> prompt_tokens = new AtomicReference<>(0);
        AtomicReference<Integer> completion_tokens = new AtomicReference<>(0);
        AtomicReference<Boolean>isThinking = new AtomicReference<>(false);
        AtomicReference<Long>thinkingStartTime=new AtomicReference<>(System.currentTimeMillis());
        AtomicReference<Long>thinkingEndTime=new AtomicReference<>(System.currentTimeMillis());
        AtomicReference<Integer> cached_tokens = new AtomicReference<>(0);
        AtomicReference<Long> startTime = new AtomicReference<>(System.currentTimeMillis());
        AtomicReference<Long> endTime = new AtomicReference<>(System.currentTimeMillis());
        StreamTextParseState wrappedTextState = new StreamTextParseState();
        // 5️⃣ 响应式读取附件
        Mono<String> attachmentMono = Mono.fromCallable(() -> {
            if (chatSendReq.getAttachments().isEmpty()) return "";
            StringBuilder attachmentContent = new StringBuilder();
            List<AiAttachment> aiAttachments = attachmentService.listByIds(chatSendReq.getAttachments());
            for (AiAttachment aiAttachment : aiAttachments) {
                if (aiAttachment.getFileType().startsWith("image/")) {
                    COSObject object = attachmentReaderUtil.getObject(AiConstants.bulk, aiAttachment.getFileUrl());
                    cn.hutool.core.io.resource.Resource resource = new InputStreamResource(object.getObjectContent());
                    byte[] bytes = StreamUtils.copyToByteArray(resource.getStream());
                    mediaList.add(Media.builder()
                            .mimeType(MimeTypeUtils.parseMimeType(aiAttachment.getFileType()))
                            .id(aiAttachment.getId().toString())
                            .name(aiAttachment.getFileName())
                            .data(bytes).build());
                    continue;
                }
                attachmentContent.append(
                        attachmentReaderUtil.readContent(aiAttachment, message)
                ).append("\n");
            }
            return attachmentContent.toString();
        }).subscribeOn(Schedulers.boundedElastic());
        // 6️⃣ system prompt
        String system = chatSendReq.getIsEcharts()
                ? SystemPrompt.ECHARTS_SYSTEM_PROMPT
                : SystemPrompt.GENERAL_SYSTEM_PROMPT+style+extendedPrompt;
         if (Boolean.TRUE.equals(chatSendReq.getIsThinking())&& supportsReasoning(setting.getChatModel())) {
             system += "。请逐步推理并给出最终答案。";
         }
        // 7️⃣ Sink：解耦 AI 流和 SSE 连接
        Sinks.Many<ChatResult> sink = Sinks.many().unicast().onBackpressureBuffer();
        Map<String, Object> toolContext = Map.of("chatId", chatId,"userId",userId);

        // 8️⃣ 后台独立订阅 AI 流，生命周期与 SSE 无关
        String finalSystem = system;
        Disposable disposable = attachmentMono.<ChatResponse>flatMapMany(attachmentContent -> {
            var promptBuilder = chatClient.prompt().options(chatOptionsBuilder.build())

                    .user(promptUserSpec -> promptUserSpec
                            .text(message)
                            .media(mediaList.toArray(new Media[0])))
                    .system(finalSystem + attachmentContent)
                    .advisors(advisor -> advisor
                            .param(ChatMemory.CONVERSATION_ID, chatId)
                            .param("messageId", messageId));
            if(setting!=null&&setting.getUseMcp()){//useMcp
                promptBuilder.toolCallbacks(toolCallbackProvider);
            }
            if (localToolCallbacks != null&&isToolAuto.get()) {
                promptBuilder.toolCallbacks(localToolCallbacks);
            }
            if(setting !=null&&setting.getChatMemory()){
                promptBuilder.advisors(this.vectorStoreMemoryAdvisor);
            }
            return promptBuilder
                    .toolContext(toolContext)
                    .stream()
                    .chatResponse();
        })
        .doOnError(throwable -> log.error("AI流异常: {}", throwable.getMessage()))
        .onErrorContinue((throwable, o) -> log.error("AI流异常跳过: {}", throwable.getMessage()))
        .switchIfEmpty(Flux.error(new RuntimeException("AI 响应失败")))
        .doOnNext(chatResponse -> {
            try {
                if (chatResponse == null) return;
                Usage usage = chatResponse.getMetadata().getUsage();
                if (usage != null && usage.getTotalTokens() != null) {
                    finalUsage.set(usage);
                }
                var result = chatResponse.getResult();
                if(result==null)return;
                var msg = result.getOutput();
                String metadataReasoning = Optional.ofNullable(msg.getMetadata())
                        .map(meta -> meta.get("reasoningContent"))
                        .map(Object::toString)
                        .orElse("");
                if (!metadataReasoning.isEmpty()) {
                    reasoningContent.append(metadataReasoning);
                    if(!isThinking.get()){
                        isThinking.set(true);
                        thinkingStartTime.set(System.currentTimeMillis());
                    }
                }
                ParsedTextChunk parsedTextChunk = parseStreamTextChunk(
                        wrappedTextState,
                        msg.getText(),
                        metadataReasoning.isEmpty() && reasoningContent.isEmpty() && full.isEmpty()
                );
                if (!parsedTextChunk.reasoning().isEmpty()) {
                    reasoningContent.append(parsedTextChunk.reasoning());
                    if(!isThinking.get()){
                        isThinking.set(true);
                        thinkingStartTime.set(System.currentTimeMillis());
                    }
                }
                if (!parsedTextChunk.text().isEmpty()) {
                    if(isThinking.get()){
                        isThinking.set(false);
                        thinkingEndTime.set(System.currentTimeMillis());
                    }
                    full.append(parsedTextChunk.text());
                }
                // 构建并推入 sink
                ChatResult chatResult = new ChatResult();
                chatResult.setReasoning(metadataReasoning + parsedTextChunk.reasoning());
                if (!parsedTextChunk.text().isEmpty()) {
                    chatResult.setText(parsedTextChunk.text());
                }
                sink.tryEmitNext(chatResult);
            } catch (Throwable e) {
                log.error("解析 chatResponse 时发生异常", e);
            }
        })
        .doOnComplete(() -> {
            // 只处理 usage，不调 sink.tryEmitComplete()，交给 doFinally 统一处理
            ParsedTextChunk tailChunk = flushStreamTextChunk(wrappedTextState);
            if (!tailChunk.reasoning().isEmpty()) {
                reasoningContent.append(tailChunk.reasoning());
            }
            if (!tailChunk.text().isEmpty()) {
                if(isThinking.get()){
                    isThinking.set(false);
                    thinkingEndTime.set(System.currentTimeMillis());
                }
                full.append(tailChunk.text());
                ChatResult chatResult = new ChatResult();
                chatResult.setText(tailChunk.text());
                sink.tryEmitNext(chatResult);
            }
            endTime.set(System.currentTimeMillis());
            Usage usage = finalUsage.get();
            if (usage == null) return;
            if (usage.getPromptTokens() != null) prompt_tokens.set(usage.getPromptTokens());
            if (usage.getCompletionTokens() != null) completion_tokens.set(usage.getCompletionTokens());
            if (usage.getTotalTokens() != null) token.set(usage.getTotalTokens());
            if (usage.getNativeUsage() instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) usage.getNativeUsage();
                Object promptTokensDetails = map.get("prompt_tokens_details");
                if (promptTokensDetails instanceof Map) {
                    Map<String, Object> detailsMap = (Map<String, Object>) promptTokensDetails;
                    cached_tokens.set(Integer.parseInt(detailsMap.get("cached_tokens").toString()));
                }
            }
        })
        .doFinally(signal -> {
            String finalAnswer = buildFinalAnswer(reasoningContent, full);
            if (signal == SignalType.ON_COMPLETE) {
                // 正常跑完：保存完整内容
                if (!finalAnswer.isEmpty()) {
                    if(setting!=null&&setting.getChatMemory()){
                        ChatEmbeddingDTO dto = new ChatEmbeddingDTO();
                        dto.setUserId(userId);
                        dto.setChatId(chatId);
                        dto.setUserMessage(message);
                        dto.setMemoryType("chat");
                        dto.setAssistantMessage(full.toString());
                        rabbitTemplate.convertAndSend(
                                "ChatVectorStoreExchange",
                                "ChatVectorStore",
                                JSONUtil.toJsonStr(dto)
                        );
                    }
                    saveAndRecord(chatId, userId, type, finalAnswer, token,
                            prompt_tokens, completion_tokens, cached_tokens, startTime, endTime,
                            (thinkingEndTime.get()-thinkingStartTime.get()),(setting!=null?setting.getChatModel():""));
                }
                sink.tryEmitComplete();

            } else if (signal == SignalType.CANCEL) {
                if (cancelTokenRegistry.isCancelled(chatId)) {
                    endTime.set(System.currentTimeMillis());
                    Usage usage = finalUsage.get();
                    thinkingEndTime.set(System.currentTimeMillis());
                    if (usage == null) return;
                    if (usage.getPromptTokens() != null) prompt_tokens.set(usage.getPromptTokens());
                    if (usage.getCompletionTokens() != null) completion_tokens.set(usage.getCompletionTokens());
                    if (usage.getTotalTokens() != null) token.set(usage.getTotalTokens());
                    if (usage.getNativeUsage() instanceof Map) {
                        Map<String, Object> map = (Map<String, Object>) usage.getNativeUsage();
                        Object promptTokensDetails = map.get("prompt_tokens_details");
                        if (promptTokensDetails instanceof Map) {
                            Map<String, Object> detailsMap = (Map<String, Object>) promptTokensDetails;
                            cached_tokens.set(Integer.parseInt(detailsMap.get("cached_tokens").toString()));
                        }
                    }
                    // 主动取消：保存已有内容
                    cancelTokenRegistry.clear(chatId);
                    if (!finalAnswer.isEmpty()) {
                        saveAndRecord(chatId, userId, type, finalAnswer, token,
                                prompt_tokens, completion_tokens, cached_tokens, startTime,
                                endTime,(thinkingEndTime.get()-thinkingStartTime.get()),(setting!=null?setting.getChatModel():"qwq-plus"));
                    }
                    sink.tryEmitComplete();
                }
                // 被动断开（SSE 断开）：不处理，流继续跑到 ON_COMPLETE
            } else {
                log.warn("AI 流未正常完成，signal={}", signal);
                sink.tryEmitComplete();
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .subscribe(); // 独立订阅，脱离 SSE 生命周期

        cancelTokenRegistry.register(chatId, disposable);

        return Flux.concat(prefixFlux, sink.asFlux())
                .onErrorResume(e -> {
                    log.error("SSE 流异常", e);
                    return Flux.empty();
                });
    }
    @Autowired
    private CancelTokenRegistry cancelTokenRegistry;

    static ParsedTextChunk parseStreamTextChunk(StreamTextParseState state, String chunk, boolean allowWrappedReasoningDetection) {
        if (chunk == null || chunk.isEmpty()) {
            return new ParsedTextChunk("", "");
        }
        state.pending.append(chunk);
        StringBuilder reasoning = new StringBuilder();
        StringBuilder text = new StringBuilder();
        while (state.pending.length() > 0) {
            if (state.mode == WrappedTextMode.UNDECIDED) {
                if (!allowWrappedReasoningDetection) {
                    text.append(state.pending);
                    state.pending.setLength(0);
                    state.mode = WrappedTextMode.NORMAL;
                    break;
                }
                int leadingWhitespaceLength = leadingWhitespaceLength(state.pending);
                CharSequence candidate = state.pending.subSequence(leadingWhitespaceLength, state.pending.length());
                String marker = detectLeadingWrappedMarker(candidate);
                if (marker != null) {
                    if (candidate.length() < marker.length()) {
                        break;
                    }
                    state.pending.delete(0, leadingWhitespaceLength + marker.length());
                    state.mode = switch (marker) {
                        case ASSISTANT_COMMENTARY_MARKER -> WrappedTextMode.COMMENTARY;
                        case ASSISTANT_FINAL_MARKER -> WrappedTextMode.FINAL;
                        default -> WrappedTextMode.REASONING;
                    };
                    continue;
                }
                text.append(state.pending);
                state.pending.setLength(0);
                state.mode = WrappedTextMode.NORMAL;
                break;
            }
            if (state.mode == WrappedTextMode.NORMAL || state.mode == WrappedTextMode.FINAL) {
                text.append(state.pending);
                state.pending.setLength(0);
                break;
            }
            MarkerMatch markerMatch = findNextWrappedMarker(state.pending);
            if (markerMatch != null) {
                appendWrappedSegment(reasoning, state.pending, markerMatch.index(), state.mode == WrappedTextMode.REASONING);
                state.pending.delete(0, markerMatch.index() + markerMatch.marker().length());
                state.mode = switch (markerMatch.marker()) {
                    case ASSISTANT_FINAL_MARKER -> WrappedTextMode.FINAL;
                    case ASSISTANT_COMMENTARY_MARKER -> WrappedTextMode.COMMENTARY;
                    default -> WrappedTextMode.REASONING;
                };
                continue;
            }
            int keepLength = longestWrappedMarkerSuffixPrefixLength(state.pending);
            int emitLength = state.pending.length() - keepLength;
            if (emitLength <= 0) {
                break;
            }
            appendWrappedSegment(reasoning, state.pending, emitLength, state.mode == WrappedTextMode.REASONING);
            state.pending.delete(0, emitLength);
        }
        return new ParsedTextChunk(reasoning.toString(), text.toString());
    }

    static ParsedTextChunk flushStreamTextChunk(StreamTextParseState state) {
        if (state.pending.isEmpty()) {
            return new ParsedTextChunk("", "");
        }
        String remainder = state.pending.toString();
        state.pending.setLength(0);
        if (state.mode == WrappedTextMode.REASONING) {
            return new ParsedTextChunk(remainder, "");
        }
        return new ParsedTextChunk("", remainder);
    }

    private static boolean startsWithOrIsPrefix(CharSequence source, String target) {
        int length = Math.min(source.length(), target.length());
        for (int i = 0; i < length; i++) {
            if (source.charAt(i) != target.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static String detectLeadingWrappedMarker(CharSequence source) {
        for (String marker : wrappedMarkersByPriority()) {
            if (startsWithOrIsPrefix(source, marker)) {
                return marker;
            }
        }
        return null;
    }

    private static MarkerMatch findNextWrappedMarker(CharSequence source) {
        int bestIndex = Integer.MAX_VALUE;
        String bestMarker = null;
        for (String marker : wrappedMarkersByPriority()) {
            int index = indexOf(source, marker);
            if (index >= 0 && index < bestIndex) {
                bestIndex = index;
                bestMarker = marker;
            }
        }
        return bestMarker == null ? null : new MarkerMatch(bestIndex, bestMarker);
    }

    private static int longestWrappedMarkerSuffixPrefixLength(CharSequence source) {
        int max = 0;
        for (String marker : wrappedMarkersByPriority()) {
            max = Math.max(max, longestSuffixPrefixLength(source, marker));
        }
        return max;
    }

    private static List<String> wrappedMarkersByPriority() {
        return List.of(
                ASSISTANT_COMMENTARY_MARKER,
                ASSISTANT_ANALYSIS_MARKER,
                ASSISTANT_FINAL_MARKER,
                ANALYSIS_MARKER
        );
    }

    private static void appendWrappedSegment(StringBuilder reasoning, CharSequence source, int endExclusive, boolean keepContent) {
        if (keepContent && endExclusive > 0) {
            reasoning.append(source, 0, endExclusive);
        }
    }

    private static int indexOf(CharSequence source, String target) {
        if (target.isEmpty() || source.length() < target.length()) {
            return -1;
        }
        for (int i = 0; i <= source.length() - target.length(); i++) {
            boolean matches = true;
            for (int j = 0; j < target.length(); j++) {
                if (source.charAt(i + j) != target.charAt(j)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return i;
            }
        }
        return -1;
    }

    private static int longestSuffixPrefixLength(CharSequence source, String target) {
        int max = Math.min(source.length(), target.length() - 1);
        for (int len = max; len > 0; len--) {
            boolean matches = true;
            for (int i = 0; i < len; i++) {
                if (source.charAt(source.length() - len + i) != target.charAt(i)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return len;
            }
        }
        return 0;
    }

    private static int leadingWhitespaceLength(CharSequence source) {
        int index = 0;
        while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
            index++;
        }
        return index;
    }

    record MarkerMatch(int index, String marker) {
    }

    private String buildFinalAnswer(StringBuilder reasoningContent, StringBuilder full) {
        String finalAnswer = "";
        if (!reasoningContent.isEmpty()) {
            finalAnswer = "<Think>" + reasoningContent + "</Think>";
        }
        return finalAnswer + full;
    }
    private void saveAndRecord(String chatId, String userId, String type, String finalAnswer,
            AtomicReference<Integer> token, AtomicReference<Integer> prompt_tokens,
            AtomicReference<Integer> completion_tokens, AtomicReference<Integer> cached_tokens,
            AtomicReference<Long> startTime, AtomicReference<Long> endTime,Long thinkingTime,String model) {
        dailyService.addTotalUsage(userId, type, token.get());
        dailyService.addUsage(userId, type, token.get(), prompt_tokens.get(),
                              completion_tokens.get(), cached_tokens.get(),"chat",model);
        chatMessageService.saveAssistantMessage(chatId, finalAnswer, token.get(),
                (int) (endTime.get() - startTime.get()),prompt_tokens.get(), completion_tokens.get(), cached_tokens.get(),
                thinkingTime);
    }
     @Transactional
    public Flux<ChatResult> chatAiStreamRegenerate(RegenDTO regenDTO, String userId, String type) {
        String userMessageId = regenDTO.getUserMessageId();
        ChatMessage chatMessage = chatMessageService.getById(userMessageId);
        UserMessage userMessage = KryoUtils.deserialize(chatMessage.getPayload());
        String chatId = regenDTO.getChatId();
        String message = userMessage.getText();
        UserSetting setting = null;
        AtomicReference<Boolean> isToolAuto = new AtomicReference<>(true);
        chatOptionsBuilder = OpenAiChatOptions.builder();
        String style = "";
        String extendedPrompt = "";
        List<AiAttachment> list = attachmentService.list(
                new LambdaQueryWrapper<AiAttachment>().eq(AiAttachment::getChatId, userMessageId));

        if (Objects.equals(type, "user")) {
            setting = userSettingService.getByUserId(userId,"user");
            if (setting != null) {
                chatOptionsBuilder.temperature(setting.getTemperature())
                        .frequencyPenalty(setting.getFrequencyPenalty())
                        .model(setting.getChatModel());
                if (supportsReasoning(setting.getChatModel())) {
                    chatOptionsBuilder.maxCompletionTokens(setting.getMaxTokens());
                } else {
                    chatOptionsBuilder.maxTokens(setting.getMaxTokens());
                }
                style = switch (setting.getTone()) {
                    case "professional" -> TonePrompt.professional;
                    case "detailed" -> TonePrompt.detailed;
                    case "teaching" -> TonePrompt.teaching;
                    case "friendly" -> TonePrompt.friendly;
                    case "funny" -> TonePrompt.funny;
                    case "neutral" -> TonePrompt.neutral;
                    case "sharp" -> TonePrompt.sharp;
                    case "critical" -> TonePrompt.critical;
                    case "poisonous" -> TonePrompt.poisonous;
                    default -> TonePrompt.concise;
                };
                if (setting.getIsSummary()) {
                    chatClientBuilder.defaultAdvisors(summaryMemoryAdvisor);
                }
                if (setting.getIsExtend()) {
                    extendedPrompt = SystemPrompt.ExtendPrompt;
                }
                if (Objects.equals(setting.getToolMode(), "off")) {
                    isToolAuto.set(false);
                }
            }
        }

        Flux<ChatResult> prefixFlux = Flux.empty();
        if (!list.isEmpty()) {
            ChatResult attachmentChat = new ChatResult();
            attachmentChat.setText("<<<ATTACHMENT_LOADING>>>");
            prefixFlux = Flux.just(attachmentChat);
        }

        ChatClient chatClient = chatClientBuilder.build();
        ToolCallback[] localToolCallbacks;
        if (Boolean.TRUE.equals(chatMessage.getIsImage())) {
            localToolCallbacks = ToolCallbacks.from(imageGenerateTool,webScrapingTool);
        } else if (Boolean.TRUE.equals(chatMessage.getIsSearch())) {
            localToolCallbacks = ToolCallbacks.from(webSearchTool,webScrapingTool);
        } else if (Boolean.TRUE.equals(chatMessage.getIsGenerate())) {
            localToolCallbacks = ToolCallbacks.from(fileOperationTool,webScrapingTool);
        } else {
            localToolCallbacks = ToolCallbacks.from(webScrapingTool);
        }

        StringBuilder full = new StringBuilder();
        StringBuilder reasoningContent = new StringBuilder();
        List<Media> mediaList = new ArrayList<>();
        AtomicReference<Boolean> hasToolCall = new AtomicReference<>(false);
        AtomicReference<Usage> finalUsage = new AtomicReference<>();
        AtomicReference<Integer> token = new AtomicReference<>(0);
        AtomicReference<Integer> prompt_tokens = new AtomicReference<>(0);
        AtomicReference<Integer> completion_tokens = new AtomicReference<>(0);
        AtomicReference<Boolean> isThinking = new AtomicReference<>(false);
        AtomicReference<Long> thinkingStartTime = new AtomicReference<>(System.currentTimeMillis());
        AtomicReference<Long> thinkingEndTime = new AtomicReference<>(System.currentTimeMillis());
        AtomicReference<Integer> cached_tokens = new AtomicReference<>(0);
        AtomicReference<Long> startTime = new AtomicReference<>(System.currentTimeMillis());
        AtomicReference<Long> endTime = new AtomicReference<>(System.currentTimeMillis());
        StreamTextParseState wrappedTextState = new StreamTextParseState();

        Mono<String> attachmentMono = Mono.fromCallable(() -> {
            if (list.isEmpty()) {
                return "";
            }
            StringBuilder attachmentContent = new StringBuilder();
            for (AiAttachment aiAttachment : list) {
                if (aiAttachment.getFileType().startsWith("image/")) {
                    COSObject object = attachmentReaderUtil.getObject(AiConstants.bulk, aiAttachment.getFileUrl());
                    cn.hutool.core.io.resource.Resource resource = new InputStreamResource(object.getObjectContent());
                    byte[] bytes = StreamUtils.copyToByteArray(resource.getStream());
                    mediaList.add(Media.builder()
                            .mimeType(MimeTypeUtils.parseMimeType(aiAttachment.getFileType()))
                            .id(aiAttachment.getId().toString())
                            .name(aiAttachment.getFileName())
                            .data(bytes)
                            .build());
                    continue;
                }
                attachmentContent.append(attachmentReaderUtil.readContent(aiAttachment, message)).append("\n");
            }
            return attachmentContent.toString();
        }).subscribeOn(Schedulers.boundedElastic());

        String system = Boolean.TRUE.equals(chatMessage.getIsEcharts())
                ? SystemPrompt.ECHARTS_SYSTEM_PROMPT
                : SystemPrompt.GENERAL_SYSTEM_PROMPT + style + extendedPrompt;
        if (Boolean.TRUE.equals(chatMessage.getIsThinking())
                && setting != null
                && supportsReasoning(setting.getChatModel())) {
            system += "。请逐步推理并给出最终答案。";
        }

        Sinks.Many<ChatResult> sink = Sinks.many().unicast().onBackpressureBuffer();
        Map<String, Object> toolContext = Map.of("chatId", chatId,"userId",userId);
        String finalSystem = system;

        UserSetting finalSetting = setting;
        Disposable disposable = attachmentMono.<ChatResponse>flatMapMany(attachmentContent -> {
            var promptBuilder = chatClient.prompt()
                    .options(chatOptionsBuilder.build())
                    .tools(new WebScrapingTool())
                    .user(promptUserSpec -> promptUserSpec
                            .text(message)
                            .media(mediaList.toArray(new Media[0])))
                    .system(finalSystem + attachmentContent)
                    .advisors(advisor -> advisor
                            .param(ChatMemory.CONVERSATION_ID, chatId)
                            .param("messageId", regenDTO.getAssistantMessageId()));
            if (finalSetting != null && finalSetting.getUseMcp()) {
                promptBuilder.toolCallbacks(toolCallbackProvider);
            }
            if (localToolCallbacks != null && isToolAuto.get()) {
                promptBuilder.toolCallbacks(localToolCallbacks);
            }
            if (finalSetting != null && finalSetting.getChatMemory()) {
                promptBuilder.advisors(this.vectorStoreMemoryAdvisor);
            }
            return promptBuilder
                    .toolContext(toolContext)
                    .stream()
                    .chatResponse();
        })
                .doOnError(throwable -> log.error("AI流异常: {}", throwable.getMessage()))
                .onErrorContinue((throwable, o) -> log.error("AI流异常跳过: {}", throwable.getMessage()))
                .switchIfEmpty(Flux.error(new RuntimeException("AI 响应失败")))
                .doOnNext(chatResponse -> {
                    try {
                        if (chatResponse == null) return;
                        Usage usage = chatResponse.getMetadata().getUsage();
                        if (usage != null && usage.getTotalTokens() != null) {
                            finalUsage.set(usage);
                        }
                        var result = chatResponse.getResult();
                        if (result == null) return;
                        var msg = result.getOutput();
                        String metadataReasoning = Optional.ofNullable(msg.getMetadata())
                                .map(meta -> meta.get("reasoningContent"))
                                .map(Object::toString)
                                .orElse("");
                        if (!metadataReasoning.isEmpty()) {
                            reasoningContent.append(metadataReasoning);
                            if (!isThinking.get()) {
                                isThinking.set(true);
                                thinkingStartTime.set(System.currentTimeMillis());
                            }
                        }
                        ParsedTextChunk parsedTextChunk = parseStreamTextChunk(
                                wrappedTextState,
                                msg.getText(),
                                metadataReasoning.isEmpty() && reasoningContent.isEmpty() && full.isEmpty()
                        );
                        if (!parsedTextChunk.reasoning().isEmpty()) {
                            reasoningContent.append(parsedTextChunk.reasoning());
                            if (!isThinking.get()) {
                                isThinking.set(true);
                                thinkingStartTime.set(System.currentTimeMillis());
                            }
                        }
                        if (!parsedTextChunk.text().isEmpty()) {
                            if (isThinking.get()) {
                                isThinking.set(false);
                                thinkingEndTime.set(System.currentTimeMillis());
                            }
                            full.append(parsedTextChunk.text());
                        }
                        ChatResult chatResult = new ChatResult();
                        chatResult.setReasoning(metadataReasoning + parsedTextChunk.reasoning());
                        if (!parsedTextChunk.text().isEmpty()) {
                            chatResult.setText(parsedTextChunk.text());
                        }
                        sink.tryEmitNext(chatResult);
                        if (chatResponse.hasToolCalls()) {
                            hasToolCall.set(true);
                        }
                    } catch (Throwable e) {
                        log.error("解析 chatResponse 时发生异常", e);
                    }
                })
                .doOnComplete(() -> {
                    ParsedTextChunk tailChunk = flushStreamTextChunk(wrappedTextState);
                    if (!tailChunk.reasoning().isEmpty()) {
                        reasoningContent.append(tailChunk.reasoning());
                    }
                    if (!tailChunk.text().isEmpty()) {
                        if (isThinking.get()) {
                            isThinking.set(false);
                            thinkingEndTime.set(System.currentTimeMillis());
                        }
                        full.append(tailChunk.text());
                        ChatResult chatResult = new ChatResult();
                        chatResult.setText(tailChunk.text());
                        sink.tryEmitNext(chatResult);
                    }
                    endTime.set(System.currentTimeMillis());
                    Usage usage = finalUsage.get();
                    if (usage == null) return;
                    if (usage.getPromptTokens() != null) prompt_tokens.set(usage.getPromptTokens());
                    if (usage.getCompletionTokens() != null) completion_tokens.set(usage.getCompletionTokens());
                    if (usage.getTotalTokens() != null) token.set(usage.getTotalTokens());
                    if (usage.getNativeUsage() instanceof Map) {
                        Map<String, Object> map = (Map<String, Object>) usage.getNativeUsage();
                        Object promptTokensDetails = map.get("prompt_tokens_details");
                        if (promptTokensDetails instanceof Map) {
                            Map<String, Object> detailsMap = (Map<String, Object>) promptTokensDetails;
                            cached_tokens.set(Integer.parseInt(detailsMap.get("cached_tokens").toString()));
                        }
                    }
                })
                .doFinally(signal -> {
                    if (hasToolCall.get() && Boolean.TRUE.equals(chatMessage.getIsImage())) {
                        sink.tryEmitComplete();
                        return;
                    }
                    String finalAnswer = buildFinalAnswer(reasoningContent, full);
                    String model = finalSetting != null ? finalSetting.getChatModel() : "";
                    if (signal == SignalType.ON_COMPLETE) {
                        if (!finalAnswer.isEmpty()) {
                            dailyService.addTotalUsage(userId, type, token.get());
                            dailyService.addUsage(userId, type, token.get(), prompt_tokens.get(),
                                    completion_tokens.get(), cached_tokens.get(), "chat", model);
                            chatMessageService.updateMessage(chatId, finalAnswer, regenDTO.getAssistantMessageId(), "assistant",
                                    token.get(), (int) (endTime.get() - startTime.get()), prompt_tokens.get(),
                                    completion_tokens.get(), cached_tokens.get(),
                                    (thinkingEndTime.get() - thinkingStartTime.get()));
                        }
                        sink.tryEmitComplete();
                    } else if (signal == SignalType.CANCEL) {
                        if (cancelTokenRegistry.isCancelled(chatId)) {
                            endTime.set(System.currentTimeMillis());
                            thinkingEndTime.set(System.currentTimeMillis());
                            Usage usage = finalUsage.get();
                            if (usage == null) return;
                            if (usage.getPromptTokens() != null) prompt_tokens.set(usage.getPromptTokens());
                            if (usage.getCompletionTokens() != null) completion_tokens.set(usage.getCompletionTokens());
                            if (usage.getTotalTokens() != null) token.set(usage.getTotalTokens());
                            if (usage.getNativeUsage() instanceof Map) {
                                Map<String, Object> map = (Map<String, Object>) usage.getNativeUsage();
                                Object promptTokensDetails = map.get("prompt_tokens_details");
                                if (promptTokensDetails instanceof Map) {
                                    Map<String, Object> detailsMap = (Map<String, Object>) promptTokensDetails;
                                    cached_tokens.set(Integer.parseInt(detailsMap.get("cached_tokens").toString()));
                                }
                            }
                            cancelTokenRegistry.clear(chatId);
                            if (!finalAnswer.isEmpty()) {
                                dailyService.addTotalUsage(userId, type, token.get());
                                dailyService.addUsage(userId, type, token.get(), prompt_tokens.get(),
                                        completion_tokens.get(), cached_tokens.get(), "chat", model);
                                chatMessageService.updateMessage(chatId, finalAnswer, regenDTO.getAssistantMessageId(),
                                        "assistant", token.get(), (int) (endTime.get() - startTime.get()),
                                        prompt_tokens.get(), completion_tokens.get(), cached_tokens.get(),
                                        (thinkingEndTime.get() - thinkingStartTime.get()));
                            }
                            sink.tryEmitComplete();
                        }
                    } else {
                        log.warn("AI 流未正常完成，signal={}", signal);
                        sink.tryEmitComplete();
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();

        cancelTokenRegistry.register(chatId, disposable);
        return Flux.concat(prefixFlux, sink.asFlux())
                .onErrorResume(e -> {
                    log.error("SSE 流异常", e);
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
    private boolean supportsReasoning(String model) {
        return REASONING_MODELS.contains(model);
    }
    private static final Set<String> REASONING_MODELS = Set.of(
            "gpt-5-mini",
            "gpt-5.1-codex",
            "gpt-5"
    );
}
