package com.ai.aigent.service.impl;

import com.ai.aigent.entity.domain.AnonymousUser;
import com.ai.aigent.entity.domain.Users;
import com.ai.aigent.mapper.UsersMapper;
import com.ai.aigent.service.AnonymousUserService;
import com.ai.aigent.service.UsersService;
import com.ai.aigent.utils.JWTUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
* @author MECHREVO
* @description 针对表【users】的数据库操作Service实现
* @createDate 2026-02-02 13:24:35
*/
@Service
public class UsersServiceImpl extends ServiceImpl<UsersMapper, Users>
    implements UsersService{

    @Autowired
    private AnonymousUserService anonymousUserService;
    @Override
    public String getLoginUser(HttpServletRequest request) {
        String token=request.getHeader("Authorization");
        if(token!=null&&!token.isEmpty())
        {
            token=JWTUtils.extractToken(token);
            return  JWTUtils.getUserId(token);
        }
        else {
            AnonymousUser user=anonymousUserService.getById(request);
            if(user!=null){
                return  user.getAnonId();
            }
        }
        throw new IllegalArgumentException("未登录");
    }
    @Override
    public String getLoginUsers(HttpServletRequest request) {
        String token=request.getHeader("Authorization");
        if(token!=null&&!token.isEmpty())
        {
            token=JWTUtils.extractToken(token);
            return  JWTUtils.getUserId(token);
        }
        throw new IllegalArgumentException("未登录");
    }

}




