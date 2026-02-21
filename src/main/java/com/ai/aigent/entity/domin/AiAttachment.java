package com.ai.aigent.entity.domin;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 
 * @TableName ai_attachment
 */
@TableName(value ="ai_attachment")
@Data
public class AiAttachment {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 
     */
  private Long userId;            // user_id -> userId
    private String chatId;          // chat_id -> chatId
    private String conservationId;  // conservation_id -> conservationId
    private String fileName;        // file_name -> fileName
    private String fileUrl;         // file_url -> fileUrl
    private Long fileSize;          // file_size -> fileSize
    private String fileType;        // file_type -> fileType
    private Date createdAt;         // created_at -> createdAt
}