package com.alipay.ticketbacked.common.dal.mapper;

import com.alipay.ticketbacked.core.model.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatSessionMapper {

    @Select("SELECT * FROM hx_chat_sessions WHERE session_id = #{sessionId}")
    ChatSession findBySessionId(@Param("sessionId") String sessionId);

    @Select("SELECT * FROM hx_chat_sessions WHERE user_id = #{userId} ORDER BY gmt_modify DESC")
    List<ChatSession> findByUserId(@Param("userId") Integer userId);

    int insert(ChatSession chatSession);

    int updateSlotsAndContext(@Param("sessionId") String sessionId, @Param("slots") String slots, @Param("lastIntent") String lastIntent, @Param("context") String context);

    int appendMessage(@Param("sessionId") String sessionId, @Param("messages") String messages);

    int updateExpire(@Param("sessionId") String sessionId, @Param("gmtExpire") String gmtExpire);

    int deleteBySessionId(@Param("sessionId") String sessionId);
}