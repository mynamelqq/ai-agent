package com.ai.aigent.controller;

import com.ai.aigent.constant.Result;
import com.ai.aigent.entity.VO.UserVo;
import com.ai.aigent.entity.domin.UserSetting;
import com.ai.aigent.entity.domin.Users;
import com.ai.aigent.exception.BusinessException;
import com.ai.aigent.mapper.UsersMapper;
import com.ai.aigent.service.UserSettingService;
import com.ai.aigent.service.UsersService;
import com.ai.aigent.utils.JWTUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/user")
public class UserController {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final int PASSWORD_MIN_LENGTH = 6;
    private static final int PASSWORD_MAX_LENGTH = 64;
    private static final int NICKNAME_MAX_LENGTH = 32;

    @Autowired
    private UsersMapper usersMapper;
    @Autowired
    private UserSettingService userSettingService;
    @Autowired
    private UsersService usersService;
    @PostMapping("/register")
    public Result<?> register(@RequestBody Map<String, String> body,HttpServletRequest request) {
        String email = requireEmail(body.get("email"), "email");
        String password = requirePassword(body.get("password"));
        String avatarUrl = normalizeOptional(body.get("avatar_url"));
        String nickName = requireNickname(body.get("nick_name"));
        Users users = usersMapper.selectOne(new LambdaQueryWrapper<Users>().eq(Users::getUsername, email));
        if (users!=null) {
            throw BusinessException.conflict("用户名已被注册");
        }
        Users user = new Users();
        user.setAvatarUrl(avatarUrl);

        user.setIp(request.getRemoteAddr());
        user.setNickname(nickName);
        user.setUsername(email);
        user.setDailyTokenLimit(500000);
        user.setTotalTokensUsed(0L);
        user.setPassword(password);
        user.setCreatedAt(LocalDateTime.now());
        usersMapper.insert(user);
        userSettingService.createDefaultSetting(user.getId().toString(),"user");
        return Result.success();
    }
     @PostMapping("/setting/update")
    public Result<?> update(@RequestBody UserSetting setting) {
        userSettingService.updateUserSetting(setting);
        return Result.success();
    }
    @GetMapping("/setting/{userId}")
    public Result<UserSetting> get(@PathVariable Long userId) {
        Users byId = usersService.getById(userId);
        String type="user";
        if(byId== null)type="anno";
        return Result.success(userSettingService.getByUserId(userId.toString(),type));
    }
    private static final long DEFAULT_EXPIRATION = 24 * 60 * 60 * 1000L; // 24小时
    private static final String ISSUER = "ai-agent-system";
    @PostMapping("/login")
    public Result<UserVo> login(@RequestBody Map<String, String> body) {
        String email = requireEmail(body.get("username"), "username");
        String password = requirePassword(body.get("password"));

        Users user = usersMapper.selectOne(new LambdaQueryWrapper<Users>().eq(Users::getUsername,email));
        if(user==null){
            throw BusinessException.notFound("用户不存在");
        }

        if (!password.equals(user.getPassword())) {
            throw BusinessException.badRequest("密码错误");
        }
        user.setLastLoginAt(LocalDateTime.now());
        usersMapper.updateById(user);
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
    @PostMapping("/password/update")
    public Result<?> updatePassword(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String userId = usersService.getLoginUsers(request);
        Users user = usersMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }

        String oldPassword = requirePassword(getRequiredField(body, "oldPassword", "old_password", "password"));
        String newPassword = requirePassword(getRequiredField(body, "newPassword", "new_password"));
        String confirmPassword = getOptionalField(body, "confirmPassword", "confirm_password");

        if (!oldPassword.equals(user.getPassword())) {
            throw BusinessException.badRequest("原密码错误");
        }
        if (oldPassword.equals(newPassword)) {
            throw BusinessException.badRequest("新密码不能与原密码相同");
        }
        if (confirmPassword != null && !newPassword.equals(requirePassword(confirmPassword))) {
            throw BusinessException.badRequest("两次输入的新密码不一致");
        }

        user.setPassword(newPassword);
        usersMapper.updateById(user);
        return Result.success();
    }
    @PostMapping("/password/forgot/update")
    public Result<?> forgotPasswordUpdate(@RequestBody Map<String, String> body) {
        String email = requireEmail(getRequiredField(body, "email", "username"), "email");
        String newPassword = requirePassword(getRequiredField(body, "newPassword", "new_password"));
        String confirmPassword = requirePassword(getRequiredField(body, "confirmPassword", "confirm_password"));

        Users user = usersMapper.selectOne(new LambdaQueryWrapper<Users>().eq(Users::getUsername, email));
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw BusinessException.badRequest("两次输入的新密码不一致");
        }
        if (newPassword.equals(user.getPassword())) {
            throw BusinessException.badRequest("新密码不能与原密码相同");
        }

        user.setPassword(newPassword);
        usersMapper.updateById(user);
        return Result.success();
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

    private String requireEmail(String value, String fieldName) {
        String normalized = requireText(value, fieldName + "不能为空");
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw BusinessException.badRequest(fieldName + "格式不正确");
        }
        return normalized;
    }

    private String requirePassword(String value) {
        String normalized = requireText(value, "密码不能为空");
        if (normalized.length() < PASSWORD_MIN_LENGTH || normalized.length() > PASSWORD_MAX_LENGTH) {
            throw BusinessException.badRequest("密码长度需在" + PASSWORD_MIN_LENGTH + "-" + PASSWORD_MAX_LENGTH + "位之间");
        }
        return normalized;
    }

    private String requireNickname(String value) {
        String normalized = requireText(value, "昵称不能为空");
        if (normalized.length() > NICKNAME_MAX_LENGTH) {
            throw BusinessException.badRequest("昵称长度不能超过" + NICKNAME_MAX_LENGTH + "位");
        }
        return normalized;
    }

    private String requireText(String value, String message) {
        if (value == null) {
            throw BusinessException.badRequest(message);
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw BusinessException.badRequest(message);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String getRequiredField(Map<String, String> body, String... fieldNames) {
        String value = getOptionalField(body, fieldNames);
        if (value == null) {
            throw BusinessException.badRequest(fieldNames[0] + "不能为空");
        }
        return value;
    }

    private String getOptionalField(Map<String, String> body, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = body.get(fieldName);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

}
