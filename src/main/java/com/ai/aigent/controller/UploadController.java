package com.ai.aigent.controller;


import com.ai.aigent.constant.Result;
import com.ai.aigent.entity.domin.AiAttachment;
import com.ai.aigent.entity.dto.UploadFileDTO;
import com.ai.aigent.service.AiAttachmentService;
import com.ai.aigent.service.UsersService;
import com.ai.aigent.service.impl.CosUploadService;
import com.ai.aigent.utils.IdGenerator;
import com.ai.aigent.utils.JWTUtils;
import com.qcloud.cos.COSClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/upload")
public class UploadController {

    @Autowired
    private CosUploadService cosUploadService;
    @Autowired
    private AiAttachmentService attachmentService;

    @PostMapping("/file")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) throws IOException {

        String url = cosUploadService.upload(file, "chat");
        return Map.of(
                "fileName", file.getOriginalFilename(),
                "url", url,
                "size", file.getSize()
        );
    }
//     @PostMapping("/file")
//    public AiAttachment upload(@RequestParam("file") MultipartFile file,
//                               @RequestParam("chatId") String chatId,
//                               @RequestParam("conservation_id")String conservation_id,@RequestParam(value = "userId",required = false)
//                                    Long userId) throws IOException {
//        UploadFileDTO dto = new UploadFileDTO();
//        dto.setConservation_id(conservation_id);
//        dto.setChat_id(chatId);
//        return attachmentService.uploadFileInChat(file, dto);
//    }
    @GetMapping("/sign")
    public Map<String, Object> getUploadSign(@RequestParam String suffix) {
        return cosUploadService.createUploadSign(suffix);
    }
    @GetMapping("/sign/avatar")
    public Map<String, Object> getUploadSignAvatar(@RequestParam String suffix) {
        return cosUploadService.createUploadAvatarSign(suffix);
    }
    @Autowired
    private COSClient  cosClient;
    @Autowired
    private UsersService usersService;
    @PostMapping("/confirm")
    @Transactional
    public Result<String> confirmUpload(@RequestBody UploadFileDTO req, HttpServletRequest request) {
        AiAttachment att = new AiAttachment();
        att.setFileName(req.getFile_name());
        att.setFileUrl(req.getFile_url());
        att.setFileSize(req.getFile_size());
        att.setFileType(req.getFile_type());
        att.setChatId(req.getChat_id());
        att.setConservationId(req.getConservation_id());
        String userId = usersService.getLoginUser(request);
        if(userId.isBlank()){
            return Result.error("请先登录");
        }
        String s = IdGenerator.createDefault().numericId(15);
        att.setId(Long.parseLong(s));
        attachmentService.save(att);
        return Result.success(att.getId().toString());
    }


}