package com.ai.aigent.demo.invoke;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.Builder;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;

public class MultiQueryExpanderDemo {
     private final Builder chatClientBuilder;
     MultiQueryExpanderDemo(@Qualifier(value = "dashScopeChatModel") ChatModel dashscopChatModel)
     {
         this.chatClientBuilder=ChatClient.builder(dashscopChatModel);
     }
     public List<Query> expand(String query)
     {
         MultiQueryExpander build = MultiQueryExpander.builder().chatClientBuilder(chatClientBuilder)
                 .numberOfQueries(3).build();
         List<Query> expand = build.expand(new Query(query));
         return expand;
     }

}
