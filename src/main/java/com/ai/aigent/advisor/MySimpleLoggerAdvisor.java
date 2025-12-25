package com.ai.aigent.advisor;


import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
@Slf4j
@Component
public class MySimpleLoggerAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {


    public String getName() {
        return this.getClass().getSimpleName();
    }

    public int getOrder() {
        return 10000;
    }
    private AdvisedRequest before(AdvisedRequest request) {
        log.info("AI Request :{}",request.toPrompt().getContents());


        return request;
    }

    private void observeAfter(AdvisedResponse advisedResponse) {
//        log.info("AI Response :{}",advisedResponse.response().getResult().getOutput().getText());
    }


    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
        advisedRequest = this.before(advisedRequest);
        AdvisedResponse advisedResponse = chain.nextAroundCall(advisedRequest);
        this.observeAfter(advisedResponse);
        return advisedResponse;
    }

    public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
        advisedRequest = this.before(advisedRequest);
        Flux<AdvisedResponse> advisedResponses = chain.nextAroundStream(advisedRequest);
        return (new MessageAggregator()).aggregateAdvisedResponse(advisedResponses, this::observeAfter);
    }
}
