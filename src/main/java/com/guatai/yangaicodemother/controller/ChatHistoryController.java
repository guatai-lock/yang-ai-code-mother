package com.guatai.yangaicodemother.controller;

import com.guatai.yangaicodemother.annotation.AuthCheck;
import com.guatai.yangaicodemother.common.BaseResponse;
import com.guatai.yangaicodemother.common.ResultUtils;
import com.guatai.yangaicodemother.common.UserConstant;
import com.guatai.yangaicodemother.exception.ErrorCode;
import com.guatai.yangaicodemother.exception.ThrowUtils;
import com.guatai.yangaicodemother.model.dto.chathistory.ChatHistoryQueryRequest;
import com.guatai.yangaicodemother.model.entity.User;
import com.guatai.yangaicodemother.service.UserService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.guatai.yangaicodemother.model.entity.ChatHistory;
import com.guatai.yangaicodemother.service.ChatHistoryService;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话历史 控制层。
 *
 */
@RestController
@RequestMapping("/chatHistory")
public class ChatHistoryController {

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private UserService userService;
    /**
     * 分页查询某个应用的对话历史（游标查询）
     *
     * @param appId          应用ID
     * @param pageSize       页面大小
     * @param lastCreateTime 最后一条记录的创建时间
     * @param request        请求
     * @return 对话历史分页
     */
    @GetMapping("/app/{appId}")
    public BaseResponse<Page<ChatHistory>> listAppChatHistory(@PathVariable Long appId,
                                                              @RequestParam(defaultValue = "10") int pageSize,
                                                              @RequestParam(required = false) LocalDateTime lastCreateTime,
                                                              HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Page<ChatHistory> result = chatHistoryService.listAppChatHistoryByPage(appId, pageSize, lastCreateTime, loginUser);
        return ResultUtils.success(result);
    }
    /**
     * 导出应用对话历史为 Markdown 文件
     */
    @GetMapping("/app/{appId}/export")
    public void exportChatHistory(@PathVariable Long appId,
                                  HttpServletRequest request,
                                  HttpServletResponse response) throws Exception {
        User loginUser = userService.getLoginUser(request);
        String markdown = chatHistoryService.exportChatHistoryAsMarkdown(appId, loginUser);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/markdown; charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"chat_history_" + appId + ".md\"");
        response.getOutputStream().write(markdown.getBytes(StandardCharsets.UTF_8));
        response.getOutputStream().flush();
    }

    /**
     * 管理员分页查询所有对话历史
     *
     * @param chatHistoryQueryRequest 查询请求
     * @return 对话历史分页
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<ChatHistory>> listAllChatHistoryByPageForAdmin(@RequestBody ChatHistoryQueryRequest chatHistoryQueryRequest) {
        ThrowUtils.throwIf(chatHistoryQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = chatHistoryQueryRequest.getPageNum();
        long pageSize = chatHistoryQueryRequest.getPageSize();
        // 查询数据
        QueryWrapper queryWrapper = chatHistoryService.getQueryWrapper(chatHistoryQueryRequest);
        Page<ChatHistory> result = chatHistoryService.page(Page.of(pageNum, pageSize), queryWrapper);
        return ResultUtils.success(result);
    }

}
