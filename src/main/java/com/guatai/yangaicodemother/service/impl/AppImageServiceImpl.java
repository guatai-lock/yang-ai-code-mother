package com.guatai.yangaicodemother.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.guatai.yangaicodemother.common.AppConstant;
import com.guatai.yangaicodemother.exception.BusinessException;
import com.guatai.yangaicodemother.exception.ErrorCode;
import com.guatai.yangaicodemother.exception.ThrowUtils;
import com.guatai.yangaicodemother.manger.CosManager;
import com.guatai.yangaicodemother.mapper.AppImageMapper;
import com.guatai.yangaicodemother.model.entity.AppImage;
import com.guatai.yangaicodemother.model.entity.User;
import com.guatai.yangaicodemother.model.vo.AppImageVO;
import com.guatai.yangaicodemother.service.AppImageService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 应用图片资源 服务层实现。
 */
@Service
@Slf4j
public class AppImageServiceImpl implements AppImageService {

    private static final Set<String> ALLOWED_TYPES = Set.of("jpg", "jpeg", "png", "gif", "webp", "svg");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L; // 10MB
    private static final long MAX_IMAGES_PER_APP = 50;

    @Resource
    private AppImageMapper appImageMapper;

    @Resource
    private CosManager cosManager;

    @Override
    public AppImageVO uploadImage(MultipartFile file, Long appId, String description, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "上传文件不能为空");
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");

        // 2. 文件类型校验
        String originalFilename = file.getOriginalFilename();
        String ext = getFileExtension(originalFilename);
        ThrowUtils.throwIf(ext == null || !ALLOWED_TYPES.contains(ext.toLowerCase()),
                ErrorCode.PARAMS_ERROR, "不支持的文件类型，仅支持 jpg/png/gif/webp/svg 格式");

        // 3. 文件大小校验
        ThrowUtils.throwIf(file.getSize() > MAX_FILE_SIZE,
                ErrorCode.PARAMS_ERROR, "文件大小超过限制（最大 10MB）");

        // 4. 检查应用图片数量上限
        long count = appImageMapper.selectCountByQuery(
                QueryWrapper.create().eq("appId", appId));
        ThrowUtils.throwIf(count >= MAX_IMAGES_PER_APP,
                ErrorCode.OPERATION_ERROR, "该应用图片数量已达上限（" + MAX_IMAGES_PER_APP + "张）");

        // 5. 保存到临时文件
        File tempFile = null;
        try {
            String fileName = UUID.randomUUID().toString().substring(0, 8) + "." + ext;
            String tempDir = System.getProperty("java.io.tmpdir") + File.separator + "ycm_upload";
            FileUtil.mkdir(tempDir);
            tempFile = new File(tempDir, fileName);
            file.transferTo(tempFile);

            // 6. 上传到 COS
            String cosKey = generateCosKey(appId, ext);
            String cosUrl = cosManager.uploadFile(cosKey, tempFile);
            ThrowUtils.throwIf(StrUtil.isBlank(cosUrl), ErrorCode.OPERATION_ERROR, "图片上传到对象存储失败");

            // 7. 保存记录到数据库
            AppImage appImage = insertImageRecord(appId, loginUser.getId(), originalFilename, cosUrl,
                    file.getSize(), ext.toLowerCase(), description);

            log.info("图片上传成功, appId: {}, cosUrl: {}, size: {}", appId, cosUrl, file.getSize());

            // 8. 返回 VO
            return convertToVO(appImage);

        } catch (IOException e) {
            log.error("图片上传失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片上传失败：" + e.getMessage());
        } finally {
            // 9. 清理临时文件
            if (tempFile != null && tempFile.exists()) {
                FileUtil.del(tempFile);
            }
        }
    }

    @Override
    public List<AppImageVO> getAppImagesByAppId(Long appId) {
        if (appId == null || appId <= 0) {
            return new ArrayList<>();
        }
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId)
                .orderBy("createTime", false);
        List<AppImage> list = appImageMapper.selectListByQuery(queryWrapper);
        return list.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public List<AppImageVO> getRecentImages(Long appId, int limit) {
        if (appId == null || appId <= 0 || limit <= 0) {
            return new ArrayList<>();
        }
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId)
                .orderBy("createTime", false)
                .limit(limit);
        List<AppImage> list = appImageMapper.selectListByQuery(queryWrapper);
        return list.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public void saveChatImageRecord(String cosUrl, String originalName, Long fileSize, String fileType, Long appId, Long userId) {
        if (StrUtil.isBlank(cosUrl) || appId == null || userId == null) {
            log.warn("保存聊天图片记录参数不完整，跳过");
            return;
        }
        try {
            insertImageRecord(appId, userId, originalName, cosUrl, fileSize, fileType, null);
            log.info("聊天图片记录已保存, appId: {}, cosUrl: {}", appId, cosUrl);
        } catch (Exception e) {
            log.warn("保存聊天图片记录失败: {}", e.getMessage());
        }
    }

    @Override
    public void deleteByAppId(Long appId) {
        if (appId == null || appId <= 0) {
            return;
        }
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId);
        appImageMapper.deleteByQuery(queryWrapper);
        log.info("应用图片记录已清理, appId: {}", appId);
    }

    /**
     * 构建并插入图片记录，返回含自增 ID 的实体
     */
    private AppImage insertImageRecord(Long appId, Long userId, String originalName, String cosUrl,
                                       Long fileSize, String fileType, String description) {
        LocalDateTime now = LocalDateTime.now();
        AppImage appImage = AppImage.builder()
                .appId(appId)
                .userId(userId)
                .originalName(originalName)
                .cosUrl(cosUrl)
                .fileSize(fileSize)
                .fileType(fileType)
                .description(description)
                .createTime(now)
                .updateTime(now)
                .build();
        appImageMapper.insert(appImage);
        return appImage;
    }

    private AppImageVO convertToVO(AppImage appImage) {
        if (appImage == null) {
            return null;
        }
        AppImageVO vo = new AppImageVO();
        BeanUtil.copyProperties(appImage, vo);
        return vo;
    }

    private String getFileExtension(String filename) {
        if (StrUtil.isBlank(filename)) {
            return null;
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return null;
        }
        return filename.substring(dotIndex + 1).toLowerCase();
    }

    private String generateCosKey(Long appId, String ext) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String fileName = appId + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + ext;
        return AppConstant.COS_USER_IMAGES_DIR + "/" + datePath + "/" + fileName;
    }
}
