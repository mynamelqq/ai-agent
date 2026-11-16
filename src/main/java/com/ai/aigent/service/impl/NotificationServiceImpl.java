package com.ai.aigent.service.impl;

import com.ai.aigent.entity.VO.NotificationVO;
import com.ai.aigent.entity.domain.Notification;
import com.ai.aigent.entity.domain.UserNotification;
import com.ai.aigent.entity.domain.Users;
import com.ai.aigent.entity.dto.NotificationPushDTO;
import com.ai.aigent.mapper.NotificationMapper;
import com.ai.aigent.mapper.UserNotificationMapper;
import com.ai.aigent.mapper.UsersMapper;
import com.ai.aigent.service.NotificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
* @author MECHREVO
* @description 针对表【notification】的数据库操作Service实现
* @createDate 2026-02-23 14:30:30
*/
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
    implements NotificationService {

    private final UserNotificationMapper userNotificationMapper;
    private final NotificationMapper notificationMapper;

    @Override
    public IPage<NotificationVO> page(
            Long userId,
            String status,
            int page,
            int size
    ) {
        Page<UserNotification> p = new Page<>(page, size);
        LambdaQueryWrapper<UserNotification> qw =
                new LambdaQueryWrapper<UserNotification>()
                        .eq(UserNotification::getUserId, userId)
                        .orderByDesc(UserNotification::getCreatedAt);
        if ("read".equals(status)) {
            qw.eq(UserNotification::getIsRead, true);
        } else if ("unread".equals(status)) {
            qw.eq(UserNotification::getIsRead, false);
        }
        IPage<UserNotification> upage =
                userNotificationMapper.selectPage(p, qw);

        List<NotificationVO> list = upage.getRecords()
                .stream()
                .map(un -> {

                    Notification n =
                            notificationMapper.selectById(
                                    un.getNotificationId());

                    NotificationVO vo = new NotificationVO();
                    vo.setId(un.getId());
                    vo.setTitle(n.getTitle());
                    vo.setContent(n.getContent());
                    vo.setType(n.getType());
                    vo.setLink(n.getLink());
                    vo.setIsRead(un.getIsRead());
                    vo.setTime(un.getCreatedAt());

                    return vo;
                }).toList();

        Page<NotificationVO> result =
                new Page<>(page, size, upage.getTotal());

        result.setRecords(list);

        return result;
    }

    @Override
    public void read(Long userId, Long id) {

        UserNotification un =
                userNotificationMapper.selectById(id);

        if (un == null || !un.getUserId().equals(userId)) {
            return;
        }

        un.setIsRead(true);
        un.setReadTime(LocalDateTime.now());

        userNotificationMapper.updateById(un);
    }

    @Override
    public void readAll(Long userId) {

        LambdaUpdateWrapper<UserNotification> uw =
                new LambdaUpdateWrapper<UserNotification>()
                        .eq(UserNotification::getUserId, userId)
                        .eq(UserNotification::getIsRead, false)
                        .set(UserNotification::getIsRead, true)
                        .set(UserNotification::getReadTime,
                                LocalDateTime.now());

        userNotificationMapper.update(null, uw);
    }

    @Override
    public long unreadCount(Long userId) {

        return userNotificationMapper.selectCount(
                new LambdaQueryWrapper<UserNotification>()
                        .eq(UserNotification::getUserId, userId)
                        .eq(UserNotification::getIsRead, false)
        );
    }
    private final UsersMapper userMapper;
    /**
     * 单用户发送
     */
    @Override
    @Transactional
    public void sendToUser(Long userId,
                           String title,
                           String content,
                           String type,
                           String link) {
        Notification notification = new Notification();
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(type);
        notification.setLink(link);
        notification.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(notification);
        UserNotification un = new UserNotification();
        un.setUserId(userId);
        un.setNotificationId(notification.getId());
        un.setIsRead(false);
        un.setCreatedAt(LocalDateTime.now());
        userNotificationMapper.insert(un);
        // 查询未读数量
        long unread =this.unreadCount(userId);
        // 推送
        pushToUser(
                userId,
                new NotificationPushDTO(
                        title,
                        content,
                        type,
                        link,
                        unread
                )
        );
    }
     private final SimpMessagingTemplate messagingTemplate;
    public void pushToUser(Long userId, NotificationPushDTO dto) {

        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/notification",
                dto
        );
    }
    /**
     * 批量发送
     */
    @Override
    @Transactional
    public void sendToUsers(List<Long> userIds,
                            String title,
                            String content,
                            String type,
                            String link) {

        Notification notification = new Notification();
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(type);
        notification.setLink(link);
        notification.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(notification);
        List<UserNotification> list = userIds.stream()
                .map(uid -> {
                    UserNotification un = new UserNotification();
                    un.setUserId(uid);
                    un.setNotificationId(notification.getId());
                    un.setIsRead(false);
                    un.setCreatedAt(LocalDateTime.now());

                    return un;

                }).toList();
        // MyBatisPlus批量
        list.forEach(userNotificationMapper::insert);
    }
    /**
     * 全站通知
     */
   @Override
    @Transactional
    public void broadcast(String title,
                          String content,
                          String type,
                          String link) {

        // 1 保存通知
        Notification notification = new Notification();
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(type);
        notification.setLink(link);
        notification.setCreatedAt(LocalDateTime.now());
        // 2 查询用户
        List<Long> userIds = userMapper.selectList(null)
                .stream()
                .map(Users::getId)
                .toList();

        // 3 保存用户通知
        sendToUsers(userIds, title, content, type, link);

        // 4 WebSocket推送
        for (Long userId : userIds) {
            pushToUser(
                    userId,
                    new NotificationPushDTO(
                            title,
                            content,
                            type,
                            link,
                            0L
                    )
            );
        }
    }
}




