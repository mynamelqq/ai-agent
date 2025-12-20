package com.ai.aigent.demo.invoke;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

public class HutoolHttp {
    public static void main(String[] args) {
        String key="sk-c095dbe374fa456eac3c91a0f85fe206";
        String url ="https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";
        JSONObject jsonObject=new JSONObject();
        JSONObject messageObject=new JSONObject();
        JSONObject systemMessage=new JSONObject();
        systemMessage.set("role","system");
        systemMessage.set("content","You are a helpful assistant.");
        JSONObject userMessage = new JSONObject();
        userMessage.set("role","user");
        userMessage.set("content","你是谁？");
        messageObject.set("messages", JSONUtil.createArray().set(systemMessage).set(userMessage));

        JSONObject parametersJson = new JSONObject();
        parametersJson.set("result_format","message");
        JSONObject requestJson = new JSONObject();
        requestJson.set("model","qwen-plus");
        requestJson.set("input",messageObject);
        requestJson.set("parameters",parametersJson);

        String result = HttpRequest.post(url).header("Authorization","Bearer "+key)
        .header("Content-Type","application/json")
        .body(requestJson.toString())
        .execute().body();
        System.out.println(result);
    }
}
