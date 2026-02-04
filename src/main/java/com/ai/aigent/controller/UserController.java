package com.ai.aigent.controller;

import cn.hutool.core.lang.UUID;
import com.ai.aigent.constant.Result;
import com.ai.aigent.entity.Users;
import com.ai.aigent.entity.VO.UserVo;
import com.ai.aigent.exception.BusinessException;
import com.ai.aigent.mapper.UsersMapper;
import com.ai.aigent.utils.JWTUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UsersMapper usersMapper;


    @PostMapping("/register")
    public Result<?> register(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");
        Users users=usersMapper.selectById(email);
        if (users!=null) {
            throw new RuntimeException("邮箱已注册");
        }

        Users user = new Users();
        user.setUsername(email);
        user.setId(UUID.randomUUID().getMostSignificantBits());
        user.setPassword(password);
        user.setCreated_at(LocalDateTime.now());
        usersMapper.insert(user);

        String token = JWTUtils.createUserToken(user.getId().toString(),user.getUsername());
        return Result.success();
    }

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

        UserVo userVo = new UserVo();
        BeanUtils.copyProperties(user, userVo);
        String token = JWTUtils.createRefreshToken(user.getId().toString());
        userVo.setToken(token);
        return Result.success(userVo);
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
        if(!JWTUtils.validateToken(refreshToken))
        {
            throw new BusinessException(202,"无效token");
        }
        // 获取用户信息
        String userId = JWTUtils.getUserId(refreshToken);
        String username = JWTUtils.getUsername(refreshToken);
        // 验证用户是否存在
        Users user = usersMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(203,"用户不存在");
        }
         String s = JWTUtils.refreshToken(refreshToken);
         return Result.success("Token刷新成功", s);
    }
}