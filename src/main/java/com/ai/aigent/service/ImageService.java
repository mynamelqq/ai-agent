package com.ai.aigent.service;

import com.ai.aigent.entity.domain.Image;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author MECHREVO
* @description 针对表【image】的数据库操作Service
* @createDate 2026-03-09 14:18:40
*/
public interface ImageService extends IService<Image> {

    public String generateImage(String description,String userId);

}
