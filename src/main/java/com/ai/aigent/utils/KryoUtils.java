package com.ai.aigent.utils;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy;
import org.objenesis.strategy.StdInstantiatorStrategy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
public final class KryoUtils {
    private static Kryo createKryo() {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        kryo.setReferences(true);
        kryo.setInstantiatorStrategy(new DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
        // Spring AI Message 体系
        kryo.register(org.springframework.ai.chat.messages.UserMessage.class);
        kryo.register(org.springframework.ai.chat.messages.AssistantMessage.class);
        kryo.register(org.springframework.ai.chat.messages.SystemMessage.class);
//        kryo.register(org.springframework.ai.chat.messages.ToolMessage.class);

        // 可能用到的集合
        kryo.register(java.util.ArrayList.class);
        kryo.register(java.util.HashMap.class);

        return kryo;
    }

private static final ThreadLocal<Kryo> KRYO =
        ThreadLocal.withInitial(KryoUtils::createKryo);

    public static byte[] serialize(Object obj) {
        Kryo kryo = KRYO.get();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             Output output = new Output(baos)) {
            kryo.writeClassAndObject(output, obj);
            output.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Serialization failed", e);
        }
    }

    public static <T> T deserialize(byte[] bytes) {
        Kryo kryo = KRYO.get();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             Input input = new Input(bais)) {
            return (T) kryo.readClassAndObject(input);
        } catch (Exception e) {
            throw new RuntimeException("Deserialization failed", e);
        }
    }
}