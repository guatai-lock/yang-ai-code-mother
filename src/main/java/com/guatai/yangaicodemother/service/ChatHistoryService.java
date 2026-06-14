package com.guatai.yangaicodemother.service;

import com.guatai.yangaicodemother.model.dto.chathistory.ChatHistoryQueryRequest;
import com.guatai.yangaicodemother.model.entity.User;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.guatai.yangaicodemother.model.entity.ChatHistory;
import com.guatai.yangaicodemother.model.vo.ChatHistoryPublicVO;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话历史 服务层。
 *
 */
public interface ChatHistoryService extends IService<ChatHistory> {
    /**
     * 添加对话消息
     * @param appId 应用ID
     * @param message 消息内容
     * @param messageType 消息类型(ai消息，用户消息)
     * @param userId 用户ID
     * @return
     */
    boolean addChatMessage(Long appId, String message, String messageType, Long userId);

    /**
     * 根据应用ID删除对话消息
      * @param appId 应用ID
     * @return 是否删除成功
     */
    boolean deleteByAppId(Long appId);

    /**
     * 根据应用ID分页获取对话消息
      * @param appId 应用ID
     * @param pageSize 页大小
     * @param lastCreateTime 上次创建时间
     * @param loginUser 登录用户
     * @return
     */
    Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                               LocalDateTime lastCreateTime,
                                               User loginUser);

    /**
     * 获取查询包装类
      * @param chatHistoryQueryRequest 查询条件
     * @return 查询包装类
     */
    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);

    /**
     * 按消息ID列表导出选中的对话历史为 Markdown 字符串
     *
     * @param messageIds 选中的消息ID列表（最多200条）
     */
    String exportSelectedChatHistoryAsMarkdown(Long appId, User loginUser, List<Long> messageIds);

    /**
     * 公开查询精选应用的对话历史（无需登录）
     *
     * @param appId          应用ID
     * @param pageSize       页面大小
     * @param lastCreateTime 最后一条记录的创建时间
     * @return 对话历史分页
     */
    Page<ChatHistoryPublicVO> listPublicChatHistory(Long appId, int pageSize,
                                                    LocalDateTime lastCreateTime);

    /**
     * 对话记忆初始化时，需要从数据库中加载对话历史到记忆中
      * @param appId 应用ID
     * @param chatMemory 对话记忆
     * @param maxCount 最大条数
     * @return
     */
    int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount);
}
