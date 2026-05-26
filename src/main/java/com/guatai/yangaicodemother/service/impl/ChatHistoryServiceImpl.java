package com.guatai.yangaicodemother.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.guatai.yangaicodemother.common.UserConstant;
import com.guatai.yangaicodemother.exception.ErrorCode;
import com.guatai.yangaicodemother.exception.ThrowUtils;
import com.guatai.yangaicodemother.model.dto.chathistory.ChatHistoryQueryRequest;
import com.guatai.yangaicodemother.model.entity.App;
import com.guatai.yangaicodemother.model.entity.User;
import com.guatai.yangaicodemother.model.enums.ChatHistoryMessageTypeEnum;
import com.guatai.yangaicodemother.service.AppService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.guatai.yangaicodemother.model.entity.ChatHistory;
import com.guatai.yangaicodemother.mapper.ChatHistoryMapper;
import com.guatai.yangaicodemother.service.ChatHistoryService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 对话历史 服务层实现。
 *
 */
@Service
@Slf4j
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory>  implements ChatHistoryService{
    @Resource
    private AppService appService;

    private static final int MESSAGE_LENGTH_WARNING_THRESHOLD = 50000;
    private static final int MESSAGE_LENGTH_ERROR_THRESHOLD = 60000;

    @Override
    public boolean addChatMessage(Long appId, String message, String messageType, Long userId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(messageType), ErrorCode.PARAMS_ERROR, "消息类型不能为空");
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        // 验证消息类型是否有效
        ChatHistoryMessageTypeEnum messageTypeEnum = ChatHistoryMessageTypeEnum.getEnumByValue(messageType);
        ThrowUtils.throwIf(messageTypeEnum == null, ErrorCode.PARAMS_ERROR, "不支持的消息类型: " + messageType);
        // 消息长度控制（MySQL TEXT 限制约64KB，留一些余量）,截断限流逻辑，防止用户消息过长导致数据库保存失败
        int messageLength = message.length();
        if (messageLength > MESSAGE_LENGTH_ERROR_THRESHOLD) {
            log.error("消息长度超过安全阈值({}): {}字符, appId: {}",
                    MESSAGE_LENGTH_ERROR_THRESHOLD, messageLength, appId);
            // 可以选择截断或抛出异常
            log.warn("消息过长({}字符)，进行截断保存，appId: {}", message.length(), appId);
            message = message.substring(0, MESSAGE_LENGTH_ERROR_THRESHOLD) + "\n...[内容过长已截断]";
        } else if (messageLength > MESSAGE_LENGTH_WARNING_THRESHOLD) {
            log.warn("消息长度接近限制({}): {}字符, appId: {}",
                    MESSAGE_LENGTH_WARNING_THRESHOLD, messageLength, appId);
        }
        ChatHistory chatHistory = ChatHistory.builder()
                .appId(appId)
                .message(message)
                .messageType(messageType)
                .userId(userId)
                .build();
        return this.save(chatHistory);
    }
    @Override
    public boolean deleteByAppId(Long appId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId);
        return this.remove(queryWrapper);
    }
    @Override
    public Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                                      LocalDateTime lastCreateTime,
                                                      User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(pageSize <= 0 || pageSize > 50, ErrorCode.PARAMS_ERROR, "页面大小必须在1-50之间");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        // 验证权限：只有应用创建者和管理员可以查看
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        boolean isCreator = app.getUserId().equals(loginUser.getId());
        ThrowUtils.throwIf(!isAdmin && !isCreator, ErrorCode.NO_AUTH_ERROR, "无权查看该应用的对话历史");
        // 构建查询条件
        ChatHistoryQueryRequest queryRequest = new ChatHistoryQueryRequest();
        queryRequest.setAppId(appId);
        queryRequest.setLastCreateTime(lastCreateTime);
        QueryWrapper queryWrapper = this.getQueryWrapper(queryRequest);
        // 查询数据
        return this.page(Page.of(1, pageSize), queryWrapper);
    }
    /**
     * 获取查询包装类
     *
     * @param chatHistoryQueryRequest
     * @return
     */
    @Override
    public QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        QueryWrapper queryWrapper = QueryWrapper.create();
        if (chatHistoryQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chatHistoryQueryRequest.getId();
        String message = chatHistoryQueryRequest.getMessage();
        String messageType = chatHistoryQueryRequest.getMessageType();
        Long appId = chatHistoryQueryRequest.getAppId();
        Long userId = chatHistoryQueryRequest.getUserId();
        LocalDateTime lastCreateTime = chatHistoryQueryRequest.getLastCreateTime();
        String sortField = chatHistoryQueryRequest.getSortField();
        String sortOrder = chatHistoryQueryRequest.getSortOrder();
        // 拼接查询条件
        queryWrapper.eq("id", id)
                .like("message", message)
                .eq("messageType", messageType)
                .eq("appId", appId)
                .eq("userId", userId);
        // 游标查询逻辑 - 只使用 createTime 作为游标
        if (lastCreateTime != null) {
            queryWrapper.lt("createTime", lastCreateTime);
        }
        // 排序
        if (StrUtil.isNotBlank(sortField)) {
            queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        } else {
            // 默认按创建时间降序排列
            queryWrapper.orderBy("createTime", false);
        }
        return queryWrapper;
    }
    @Override
    public List<ChatHistory> listAllChatHistoryForExport(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        // 验证权限：只有应用创建者和管理员可以查看
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        boolean isCreator = app.getUserId().equals(loginUser.getId());
        ThrowUtils.throwIf(!isAdmin && !isCreator, ErrorCode.NO_AUTH_ERROR, "无权查看该应用的对话历史");
        // 查询所有对话历史，按创建时间升序
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId)
                .orderBy("createTime", true);
        return this.list(queryWrapper);
    }
    @Override
    public String exportChatHistoryAsMarkdown(Long appId, User loginUser) {
        List<ChatHistory> historyList = listAllChatHistoryForExport(appId, loginUser);
        if (historyList == null || historyList.isEmpty()) {
            return "# 对话记录\n\n暂无对话记录。";
        }
        App app = appService.getById(appId);
        String appName = app != null ? app.getAppName() : "未知应用";
        String userName = StrUtil.blankToDefault(loginUser.getUserName(), "未知用户");
        String exportTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        StringBuilder sb = new StringBuilder();
        // 文件头
        sb.append("# 对话记录：").append(appName).append("\n\n");
        sb.append("- **应用 ID**：").append(appId).append("\n");
        sb.append("- **创建者**：").append(userName).append("\n");
        sb.append("- **导出时间**：").append(exportTime).append("\n");
        sb.append("- **对话轮次**：").append((historyList.size() + 1) / 2).append("\n\n");
        sb.append("---\n\n");
        // 按 (user, ai) 配对输出
        int round = 0;
        for (int i = 0; i < historyList.size(); i++) {
            ChatHistory msg = historyList.get(i);
            if ("user".equals(msg.getMessageType())) {
                round++;
                sb.append("## 第 ").append(round).append(" 轮\n\n");
                sb.append("### 用户\n\n").append(msg.getMessage()).append("\n\n");
                // 检查下一条是否为 AI 响应
                if (i + 1 < historyList.size() && "ai".equals(historyList.get(i + 1).getMessageType())) {
                    sb.append("### AI\n\n").append(historyList.get(i + 1).getMessage()).append("\n\n");
                    i++; // 跳过已处理的 AI 消息
                }
                sb.append("---\n\n");
            }
        }
        return sb.toString();
    }
    @Override
    public int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount) {
        try {
            // 1. 查询最近 maxCount 条历史（最新的在前
            // 直接构造查询条件，起始点为 1 而不是 0，用于排除最新的用户消息
            QueryWrapper queryWrapper = QueryWrapper.create()
                    .eq(ChatHistory::getAppId, appId)
                    .orderBy(ChatHistory::getCreateTime, false)
                    .limit(1, maxCount);
            List<ChatHistory> historyList = this.list(queryWrapper);
            if (CollUtil.isEmpty(historyList)) {
                return 0;
            }
            final int MAX_TOKENS = 8000;
            int totalTokens = 0;
            // 用于保存保留的消息
            List<ChatHistory> keepMessages = new ArrayList<>();
            // 2. 倒着遍历 → 保留最新消息，丢弃最旧消息（核心！）
            for (int i = 0; i < historyList.size(); i++) {
                ChatHistory msg = historyList.get(i);
                int msgTokens = estimateTokenCount(msg.getMessage());

                // 超过上限就不再保留更早的消息
                if (totalTokens + msgTokens > MAX_TOKENS) {
                    log.info("appId: {} 消息token超限，丢弃较早的历史消息，只保留最新", appId);
                    break;
                }
                keepMessages.add(msg);
                totalTokens += msgTokens;
            }
            // 3. 反转 → 变成【最早 → 最新】顺序，才能加入 chatMemory
            Collections.reverse(keepMessages);
            // 按时间顺序添加到记忆中
            int loadedCount = 0;
            // 4. 清空并加载到内存
            chatMemory.clear();
            for (ChatHistory history : keepMessages) {
                if (ChatHistoryMessageTypeEnum.USER.getValue().equals(history.getMessageType())) {
                    chatMemory.add(UserMessage.from(history.getMessage()));
                    loadedCount++;
                } else if (ChatHistoryMessageTypeEnum.AI.getValue().equals(history.getMessageType())) {
                    chatMemory.add(AiMessage.from(history.getMessage()));
                    loadedCount++;
                }
            }
            log.info("appId: {} 加载完成，保留最新 {} 条消息，总token：{}", appId, loadedCount, totalTokens);
            return loadedCount;
        } catch (Exception e) {
            log.error("加载历史对话失败，appId:{}", appId, e);
            return 0;
        }
    }
    // 简单的token估算方法
    private int estimateTokenCount(String text) {
        // 粗略估算：英文约4字符/token，中文约1.5字符/token
        if (text == null) return 0;
        int chineseChars = 0;
        int otherChars = 0;
        for (char c : text.toCharArray()) {
            if (c >= 0x4e00 && c <= 0x9fff) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        return chineseChars + (otherChars / 4);
    }
}
