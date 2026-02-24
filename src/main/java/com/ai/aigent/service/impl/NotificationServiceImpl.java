package com.ai.aigent.service.impl;

import com.ai.aigent.entity.VO.NotificationVO;
import com.ai.aigent.entity.domin.UserNotification;
import com.ai.aigent.mapper.UserNotificationMapper;
import com.ai.aigent.service.NotificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ai.aigent.entity.domin.Notification;
import com.ai.aigent.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
}




