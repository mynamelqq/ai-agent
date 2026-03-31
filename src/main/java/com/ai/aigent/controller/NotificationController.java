package com.ai.aigent.controller;

import com.ai.aigent.entity.VO.NotificationVO;
import com.ai.aigent.entity.dto.SendBatchNotificationRequest;
import com.ai.aigent.entity.dto.SendNotificationRequest;
import com.ai.aigent.service.NotificationService;
import com.ai.aigent.service.UsersService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;
    private final UsersService usersService;
       @PostMapping("/send/user")
    public String sendToUser(@RequestBody SendNotificationRequest req) {

        notificationService.sendToUser(
                req.getUserId(),
                req.getTitle(),
                req.getContent(),
                req.getType(),
                req.getLink()
        );

        return "发送成功";
    }

    /**
     * 批量发送
     */
    @PostMapping("/send/users")
    public String sendToUsers(@RequestBody SendBatchNotificationRequest req) {

        notificationService.sendToUsers(
                req.getUserIds(),
                req.getTitle(),
                req.getContent(),
                req.getType(),
                req.getLink()
        );

        return "批量发送成功";
    }

    /**
     * 全站广播
     */
    @PostMapping("/broadcast")
    public String broadcast(@RequestBody SendNotificationRequest req) {

        notificationService.broadcast(
                req.getTitle(),
                req.getContent(),
                req.getType(),
                req.getLink()
        );

        return "广播成功";
    }
     @GetMapping
    public IPage<NotificationVO> page(
             @RequestParam(defaultValue = "all") String status,
             @RequestParam(defaultValue = "1") int page,
             @RequestParam(defaultValue = "10") int size, HttpServletRequest request
    ) {
        String userId =usersService.getLoginUsers(request);
        return notificationService.page(
                Long.valueOf(userId),
                status,
                page,
                size
        );
    }
    @PostMapping("/{id}/read")
    public void read(@PathVariable Long id, HttpServletRequest request) {
        String userId =usersService.getLoginUsers(request);
        notificationService.read(Long.valueOf(userId), id);
    }
    @PostMapping("/read-all")
    public void readAll(HttpServletRequest request) {
    String userId =usersService.getLoginUsers(request);
        notificationService.readAll(Long.valueOf(userId));
    }
    @GetMapping("/unread-count")
    public long unreadCount(HttpServletRequest request) {
        String userId =usersService.getLoginUsers(request);
        return notificationService.unreadCount(Long.valueOf(userId));
    }

}
