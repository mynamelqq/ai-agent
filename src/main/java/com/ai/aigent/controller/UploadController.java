package com.ai.aigent.controller;


import cn.hutool.core.util.RandomUtil;
import com.ai.aigent.constant.Result;
import com.ai.aigent.entity.AiAttachment;
import com.ai.aigent.entity.dto.UploadFileDTO;
import com.ai.aigent.service.AiAttachmentService;
import com.ai.aigent.service.impl.CosUploadService;
import com.ai.aigent.utils.AttachmentReaderUtil;
import com.ai.aigent.utils.IdGenerator;
import com.ai.aigent.utils.JWTUtils;
import com.alibaba.cloud.ai.document.TextDocumentParser;
import com.google.common.collect.Lists;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.ai.aigent.utils.AttachmentReaderUtil.SMALL_FILE_LIMIT;

@RestController
@RequestMapping("/upload")
public class UploadController {

    @Autowired
    private CosUploadService cosUploadService;
    @Autowired
    private AiAttachmentService attachmentService;

    //    @PostMapping("/file")
//    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) throws IOException, IOException {
//
//        String url = cosUploadService.upload(file, "chat");
//
//        return Map.of(
//                "fileName", file.getOriginalFilename(),
//                "url", url,
//                "size", file.getSize()
//        );
//    }
//     @PostMapping("/file")
//    public AiAttachment upload(@RequestParam("file") MultipartFile file,
//                               @RequestParam("chatId") String chatId,
//                               @RequestParam("conservation_id")String conservation_id,@RequestParam(value = "userId",required = false)
//                                    Long userId) throws IOException {
//        UploadFileDTO dto = new UploadFileDTO();
//        dto.setUserId(userId);
//        dto.setChatId(conservation_id);
//        dto.setMessageId(chatId);
//        return attachmentService.uploadFile(file, dto);
//    }
    @GetMapping("/sign")
    public Map<String, Object> getUploadSign(@RequestParam String suffix) {
        return cosUploadService.createUploadSign(suffix);
    }
    @Autowired
    private COSClient  cosClient;

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
        Object token = request.getHeader("Authorization");
        if (token != null && JWTUtils.validateToken(token.toString())) {
            att.setUserId(Long.parseLong(JWTUtils.getUserId(token.toString())));
        }
        String s = IdGenerator.createDefault().numericId(15);
        att.setId(Long.parseLong(s));
        attachmentService.save(att);
        attachmentService.asyncEmbedding(att);
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return Result.success(att.getId().toString());
    }


}