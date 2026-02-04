package com.ai.aigent.controller;



import com.ai.aigent.entity.AiAttachment;
import com.ai.aigent.entity.dto.UploadCallbackDTO;
import com.ai.aigent.service.AiAttachmentService;
import com.ai.aigent.service.impl.CosUploadService;
import com.ai.aigent.utils.CosStsUtil;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/upload")
public class UploadController {

    @Autowired
    private CosUploadService cosUploadService;

    @PostMapping("/file")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) throws IOException, IOException {

        String url = cosUploadService.upload(file, "chat");

        return Map.of(
                "fileName", file.getOriginalFilename(),
                "url", url,
                "size", file.getSize()
        );
    }
}