package com.ai.aigent.tools;

import cn.hutool.core.util.RandomUtil;
import com.ai.aigent.entity.domin.Image;
import com.ai.aigent.service.ImageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.zhipuai.ZhiPuAiImageModel;
import org.springframework.ai.zhipuai.ZhiPuAiImageOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

@Slf4j
@Component
public class ImageGenerateTool {
    @Autowired
    private ZhiPuAiImageModel zhiPuAiImageModel;
    @Autowired
    private ImageService imageService;
    @Tool(name = "generate_image", description = "根据文本描述生成图片，返回markdown图片格式,不要用引号包裹结果",returnDirect = true)
    public String generateImage(
            @ToolParam(description = "图片生成提示词，可适当优化用户的提示词生成更精确的图片") String description,ToolContext toolContext) {
        try {
             ImageResponse response = zhiPuAiImageModel.call(
                new ImagePrompt(
                        description,
                        ZhiPuAiImageOptions.builder()
                                .model("glm-image")
                                .build()         )
                );
                       String str=String.format("![图片](%s \"按照提示词生成的图片\")",
                    response.getResult().getOutput().getUrl()
            );
            Image image=new Image();
            image.setId(RandomUtil.randomInt());
            Map<String, Object> context = toolContext.getContext();
            image.setLink(response.getResult().getOutput().getUrl());
            image.setUserId(context.get("userId").toString());
            image.setConservationId(context.get("chatId").toString());
            image.setCreatedAt(new Date());
            imageService.save(image);
            return str;
        }
        catch (Exception e)
        {
            log.error(e.getMessage());
            return "生成图片工具调用失败";
        }
    }
}