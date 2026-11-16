package com.ai.aigent.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.ai.aigent.entity.domain.Image;
import com.ai.aigent.mapper.ImageMapper;
import com.ai.aigent.service.ImageService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.zhipuai.ZhiPuAiImageModel;
import org.springframework.ai.zhipuai.ZhiPuAiImageOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
* @author MECHREVO
* @description 针对表【image】的数据库操作Service实现
* @createDate 2026-03-09 14:18:40
*/
@Service
public class ImageServiceImpl extends ServiceImpl<ImageMapper, Image>
    implements ImageService{
    @Autowired
    private ZhiPuAiImageModel zhiPuAiImageModel;
    public String generateImage(String description, String userId) {
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
            image.setLink(response.getResult().getOutput().getUrl());
            image.setUserId(userId);
            image.setConservationId(RandomUtil.randomString(32));
//            image.setConservationId(chatId);
            image.setCreatedAt(new Date());
            this.save(image);
            return str;
        }
        catch (Exception e)
        {
            log.error(e.getMessage());
            return "生成图片工具调用失败";
        }
    }
}




