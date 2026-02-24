package com.ai.aigent.controller;

import com.ai.aigent.entity.VO.NotificationVO;
import com.ai.aigent.service.NotificationService;
import com.ai.aigent.service.UsersService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ServiceLoader;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;
    private final UsersService usersService;
     @GetMapping
    public IPage<NotificationVO> page(
             @RequestParam(defaultValue = "all") String status,
             @RequestParam(defaultValue = "1") int page,
             @RequestParam(defaultValue = "10") int size, HttpServletRequest request
    ) {
        String userId =usersService.getLoginUser(request);
        if(userId.isBlank()){
            throw new IllegalArgumentException("用户未登录");
        }
        return notificationService.page(
                Long.valueOf(userId),
                status,
                page,
                size
        );
    }
    @PostMapping("/{id}/read")
    public void read(@PathVariable Long id, String userId) {

        notificationService.read(Long.valueOf(userId), id);
    }
    @PostMapping("/read-all")
    public void readAll(String userId) {
        notificationService.readAll(Long.valueOf(userId));
    }
    @GetMapping("/unread-count")
    public long unreadCount(String userId) {
        return notificationService.unreadCount(Long.valueOf(userId));
    }

}
