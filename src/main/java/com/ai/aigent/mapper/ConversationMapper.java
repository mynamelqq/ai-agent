package com.ai.aigent.mapper;

import com.ai.aigent.entity.domain.Conversation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
* @author MECHREVO
* @description 针对表【conversation】的数据库操作Mapper
* @createDate 2026-02-02 16:14:05
* @Entity com.ai.aigent.entity.domin.Conversation
*/
public interface ConversationMapper extends BaseMapper<Conversation> {
    @Select("SELECT id FROM conversation WHERE id = #{id} FOR UPDATE")
    String lockById(@Param("id") String id);
}




