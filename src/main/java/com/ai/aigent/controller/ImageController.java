package com.ai.aigent.controller;

import com.ai.aigent.constant.Result;
import com.ai.aigent.entity.domain.Image;
import com.ai.aigent.service.ImageService;
import com.ai.aigent.service.UsersService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/image")
public class ImageController {
    @Autowired
    private ImageService imageService;
    @Autowired
    private UsersService usersService;
    @GetMapping("/list")
    public Result<List<Image>> list(HttpServletRequest request){
        String loginUser = usersService.getLoginUser(request);
        List<Image> list = imageService.list(new LambdaQueryWrapper<Image>().eq(Image::getUserId, loginUser));
        return Result.success(list);
    }
    @PostMapping("/generate")// /api/image/generate
    public Result<String> generate(HttpServletRequest request,String description,String chatId){
        String loginUser = usersService.getLoginUser(request);
        String result = imageService.generateImage(description,loginUser);
        return Result.success(result);
    }

}
