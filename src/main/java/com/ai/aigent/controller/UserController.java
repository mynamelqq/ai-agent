package com.ai.aigent.controller;

import cn.hutool.core.lang.UUID;
import com.ai.aigent.constant.AiCientConstans;
import com.ai.aigent.constant.Result;
import com.ai.aigent.entity.VO.UserVo;
import com.ai.aigent.entity.domin.UserSetting;
import com.ai.aigent.entity.domin.Users;
import com.ai.aigent.exception.BusinessException;
import com.ai.aigent.mapper.UsersMapper;
import com.ai.aigent.service.UserSettingService;
import com.ai.aigent.utils.JWTUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UsersMapper usersMapper;
    @Autowired
    private UserSettingService userSettingService;
    @PostMapping("/register")
    public Result<?> register(@RequestBody Map<String, String> body,HttpServletRequest request) {
        String email = body.get("email");
        String password = body.get("password");
        String avatarUrl = body.get("avatar_url");
        String nickName=body.get("nick_name");
        Users users=usersMapper.selectById(email);
        if (users!=null) {
            throw new RuntimeException("用户名已被注册");
        }
        Users user = new Users();
        user.setAvatarUrl(avatarUrl);
        user.setIp(request.getRemoteAddr());
        user.setNickname(nickName);
        user.setUsername(email);
        user.setDailyTokenLimit(500000);user.setTotalTokensUsed(0L);
        user.setId(UUID.randomUUID().getMostSignificantBits());
        user.setPassword(password);
        user.setCreatedAt(LocalDateTime.now());
        usersMapper.insert(user);
        userSettingService.createDefaultSetting(user.getId());
        return Result.success();
    }
     @PostMapping("/setting/update")
    public Result<?> update(@RequestBody UserSetting setting) {
        userSettingService.updateUserSetting(setting);
        return Result.success();
    }
    @GetMapping("/setting/{userId}")
    public Result<UserSetting> get(@PathVariable Long userId) {
        return Result.success(userSettingService.getByUserId(userId));
    }
    private static final long DEFAULT_EXPIRATION = 24 * 60 * 60 * 1000L; // 24小时
    private static final String ISSUER = "ai-agent-system";
    @PostMapping("/login")
    public Result<UserVo> login(@RequestBody Map<String, String> body) {
        String email = body.get("username");
        String password = body.get("password");

        Users user = usersMapper.selectOne(new LambdaQueryWrapper<Users>().eq(Users::getUsername,email));
        if(user==null){
            throw new RuntimeException("用户不存在");
        }

        if (!password.equals(user.getPassword())) {
            throw new RuntimeException("密码错误");
        }
        user.setLastLoginAt(LocalDateTime.now());
        UserVo userVo = new UserVo();
        BeanUtils.copyProperties(user, userVo);
        userVo.setId(user.getId().toString());
       String accessToken = JWTUtils.createUserToken(
                user.getId().toString(),
                user.getUsername()
        );
        userVo.setAccessToken( accessToken);
        userVo.setRefreshToken(JWTUtils.createRefreshToken(user.getId().toString()));
        return Result.success(userVo);
    }
    @PostMapping("/update")
    public Result<?> update(@RequestBody Map<String, Object> body) {//jwt
        String ids = body.get("id").toString();
        if (ids==null||ids.isEmpty()) {
            throw new RuntimeException("用户ID不能为空");
        }
        Long id=Long.parseLong(ids);
        // 根据ID查询用户是否存在
        Users user = usersMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        // 获取并更新用户名（邮箱），如果需要修改用户名
        String username = body.get("username").toString();
        if (username != null && !username.isEmpty()&&!username.equals(user.getUsername())) {
            Users checkUser = usersMapper.selectOne(new LambdaQueryWrapper<Users>().eq(Users::getUsername, username));
            if (checkUser != null && !checkUser.getId().toString().equals(id)) {
                throw new RuntimeException("该用户名已被占用");
            }
            user.setUsername(username);
        }
        Object avatarUrl=body.get("avatar_url");
        if (avatarUrl != null) {
            String url=avatarUrl.toString();
            if (!url.isEmpty()) {user.setAvatarUrl(url);}

        }
        // 执行更新操作
        usersMapper.updateById(user);
        return Result.success();
    }
    @PostMapping("/logout")
    public Result<?> logout(HttpServletRequest request) {
      String token = request.getHeader("Authorization");
        if (token == null) {
            throw new RuntimeException("未登录");
        }
        return Result.success("退出登录成功");
    }
     @PostMapping("/refresh")
    @Operation(summary = "刷新Token", description = "使用刷新Token获取新的访问Token")
    public Result<String> refreshToken(@RequestBody Map<String, String> requestBody) {
        String refreshToken = requestBody.get("refreshToken");
        if (!JWTUtils.validateToken(refreshToken) ||
            !"refresh".equals(JWTUtils.getTokenType(refreshToken))) {
            throw new BusinessException(202,"无效refreshToken");
        }
        // 获取用户信息
        String userId = JWTUtils.getUserId(refreshToken);
        // 验证用户是否存在
        Users user = usersMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(203,"用户不存在");
        }
         String s = JWTUtils.refreshToken(refreshToken);
         return Result.success("Token刷新成功", s);
    }
}