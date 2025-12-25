package com.ai.aigent.advisor;

import com.ai.aigent.service.ChatSummaryService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class SummaryMemoryAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {
    @Resource
    private ChatSummaryService chatSummaryService;
    @Override
    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
         advisedRequest = this.before(advisedRequest);
        AdvisedResponse advisedResponse = chain.nextAroundCall(advisedRequest);
        return advisedResponse;
    }
    private AdvisedRequest before(AdvisedRequest request) {
        String id = (String)request.toolContext().get("id");
        String summary = chatSummaryService.getSummary(id);
       if (summary == null || summary.isBlank()) {
            return request;
        }

        return AdvisedRequest.from(request).systemText(request.systemText()+ "\n【长期记忆摘要】\n" +summary+
            "【结束】").build();
    }
    private void observeAfter(AdvisedResponse advisedResponse) {
    }
    @Override
    public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
        advisedRequest = this.before(advisedRequest);
        Flux<AdvisedResponse> advisedResponses = chain.nextAroundStream(advisedRequest);
        return (new MessageAggregator()).aggregateAdvisedResponse(advisedResponses, this::observeAfter);
    }

     public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return 100;
    }
}
