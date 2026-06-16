package com.guatai.yangaicodemother.service;

import com.guatai.yangaicodemother.model.entity.User;
import com.guatai.yangaicodemother.model.vo.AppImageVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 应用图片资源 服务层。
 */
public interface AppImageService {

    /**
     * 上传图片
     *
     * @param file        上传的图片文件
     * @param appId       应用ID
     * @param description 图片描述（可选）
     * @param loginUser   登录用户
     * @return 图片VO
     */
    AppImageVO uploadImage(MultipartFile file, Long appId, String description, User loginUser);

    /**
     * 获取应用的所有图片
     *
     * @param appId 应用ID
     * @return 图片VO列表（按创建时间倒序）
     */
    List<AppImageVO> getAppImagesByAppId(Long appId);

    /**
     * 获取应用最近 N 张图片
     *
     * @param appId 应用ID
     * @param limit 数量限制
     * @return 图片VO列表
     */
    List<AppImageVO> getRecentImages(Long appId, int limit);

    /**
     * 删除应用的图片记录
     *
     * @param appId 应用ID
     */
    void deleteByAppId(Long appId);

    /**
     * 保存聊天上传的图片记录（图片已通过 CosManager 上传到 COS）
     * <p>
     * 用于在聊天过程中上传图片时，直接将图片信息记录到 app_image 表，
     * 避免重复上传到 COS。保存后，
     * {@link com.guatai.yangaicodemother.core.AiCodeGeneratorFacade#enrichWithImageContext}
     * 会在后续对话中自动注入这些图片。
     *
     * @param cosUrl       图片的 COS 访问 URL
     * @param originalName 原始文件名
     * @param fileSize     文件大小（字节）
     * @param fileType     文件类型（扩展名）
     * @param appId        应用ID
     * @param userId       用户ID
     */
    void saveChatImageRecord(String cosUrl, String originalName, Long fileSize, String fileType, Long appId, Long userId);
}
