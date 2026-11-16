package com.ai.aigent.service;

import com.ai.aigent.entity.domain.Users;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

/**
* @author MECHREVO
* @description 针对表【users】的数据库操作Service
* @createDate 2026-02-02 13:24:35
*/
public interface UsersService extends IService<Users> {
    String getLoginUser(HttpServletRequest request);
    String getLoginUsers(HttpServletRequest request);


}
