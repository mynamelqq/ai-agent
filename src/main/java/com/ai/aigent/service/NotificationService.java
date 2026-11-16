package com.ai.aigent.service;

import com.ai.aigent.entity.VO.NotificationVO;
import com.ai.aigent.entity.domain.Notification;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author MECHREVO
* @description 针对表【notification】的数据库操作Service
* @createDate 2026-02-23 14:30:30
*/

public interface NotificationService extends IService<Notification> {
    IPage<NotificationVO> page(
                Long userId,
                String status,
                int page,
                int size
        );

    void read(Long userId,Long id);

    void readAll(Long userId);

    long unreadCount(Long userId);
     void sendToUser(Long userId,
                    String title,
                    String content,
                    String type,
                    String link);

    /**
     * 批量发送
     */
    void sendToUsers(List<Long> userIds,
                     String title,
                     String content,
                     String type,
                     String link);

    /**
     * 全站广播
     */
    void broadcast(String title,
                   String content,
                   String type,
                   String link);
}
