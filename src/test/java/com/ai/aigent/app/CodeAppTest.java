package com.ai.aigent.app;

import com.ai.aigent.service.ChatSummaryService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
class CodeAppTest {
    @Resource
    private CodeApp codeApp;
    @Resource
    private ChatSummaryService chatSummaryService;
    @Test
    void doChatWithReport() {
//        CodeApp.codeReport codeReport = codeApp.doChatWithReport("你好，我是程序员鱼皮，我想让另一半（编程导航）更爱我，但我不知道该怎么做'", "1");
//        Assertions.assertNotNull(codeReport);
    }

    @Test
    void doChatWithRag() throws InterruptedException {
       String chatId = "4";
//          loveApp.doChatWithRag("我叫李亚奇", chatId);
//          loveApp.doChatWithRag("我叫老婆叫是Efrewew", chatId);
//          loveApp.doChatWithRag("我老婆是英国的", chatId);
//        loveApp.doChatWithRag("我今年 25 岁", chatId);
//        loveApp.doChatWithRag("我在南京工作,我现在状态很糟糕", chatId);

//        System.out.println(s);
//        Thread.sleep(2000);
//        String summary = chatSummaryService.getSummary(chatId);
//        System.out.println("SUMMARY = " + summary);
//                String s = loveApp.doChatWithRag("根据历史的回答，你知道我什么情况吗", chatId);
//        System.out.println(s);

    }
    @Test
    void doChatWithTools() {
    // 测试物联网搜索问题的答案
//    testMessage("周末想带女朋友去上海约会，推荐几个适合情侣的小众打卡地？");

//    // 测试网页抓取: 恋爱案例分析
//    testMessage("最近和对象吵架了，看看编程导航网站 (codefather.cn) 的其他情侣是怎么解决矛盾的？");
//
    // 测试资源下载: 图片下载
    testMessage("直接下载一张适合做手机壁纸的星空情侣图片为文件");

    // 测试终端操作: 执行代码
//    testMessage("执行 Python3 脚本来生成数据分析报告");
//
//    // 测试文件操作: 保存用户档案
//    testMessage("保存我的恋爱档案为文件");
//
    // 测试 PDF 生成
//    testMessage("生成一份‘七夕约会计划’PDF，包含餐厅预订、活动流程和礼物清单");
    }
    void testMessage(String message)
    {
//        String id= UUID.randomUUID().toString();
//        String s = codeApp.doChatWithToolCalling(message, id);
//        System.out.println(s);
    }
    @Test
    void testGenTitle()
    {
        String message="This model provider doesn't serve your region. Visit https://cursor.com/docs/account/regions for more information.";
        System.out.println(codeApp.genTitle(message));

    }


    @Test
    void doChatWithMcp() {
        String id= UUID.randomUUID().toString();
        String s = codeApp.doChatWithMcp("帮我搜索一些电脑的图片", id);
        System.out.println(s);
    }
}