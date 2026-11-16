package com.ai.aigent.service;

import com.ai.aigent.entity.domain.AiAttachment;
import com.ai.aigent.entity.dto.UploadFileDTO;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
* @author MECHREVO
* @description 针对表【ai_attachment】的数据库操作Service
* @createDate 2026-02-04 22:50:40
*/
public interface AiAttachmentService extends IService<AiAttachment> {
 AiAttachment uploadFileInChat(MultipartFile file, UploadFileDTO dto) throws IOException;
 String uploadFile(MultipartFile file) throws IOException;
 void asyncEmbedding(AiAttachment att);
}
