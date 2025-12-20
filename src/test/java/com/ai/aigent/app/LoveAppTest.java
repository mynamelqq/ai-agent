package com.ai.aigent.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
@SpringBootTest
class LoveAppTest {
    @Resource
    private LoveApp loveApp;
    @Test
    void doChatWithReport() {
        LoveApp.loveReport loveReport = loveApp.doChatWithReport("你好，我是程序员鱼皮，我想让另一半（编程导航）更爱我，但我不知道该怎么做'", "1");
        Assertions.assertNotNull(loveReport);
    }
}