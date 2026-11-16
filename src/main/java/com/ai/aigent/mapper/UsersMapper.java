package com.ai.aigent.mapper;

import com.ai.aigent.entity.domain.Users;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
* @author MECHREVO
* @description 针对表【users】的数据库操作Mapper
* @createDate 2026-02-02 13:24:35
* @Entity com.ai.aigent.entity.domin.Users
*/
public interface UsersMapper extends BaseMapper<Users> {
    Users selectUserById(@Param("id")Long id);
    List<Users> selectUserByIds(@Param("ids") List<Long>ids);
}




