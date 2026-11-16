package com.ai.aigent.utils;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.boot.test.context.SpringBootTest;
@SpringBootTest
class KryoUtilsTest {

    @Test
    void serialize() {
        AssistantMessage assistantMessage=new AssistantMessage("addadsada");
        byte[] serialize = KryoUtils.serialize(assistantMessage);
        Object deserialize = KryoUtils.deserialize(serialize);
//        System.out.println(deserialize.getClass());
    }

    @Test
    void deserialize() {

    }
}