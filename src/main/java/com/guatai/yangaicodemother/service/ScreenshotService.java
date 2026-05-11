package com.guatai.yangaicodemother.service;

/**
 * ClassName: ScreenshotServiceImpl
 * Package: com.guatai.yangaicodemother.service
 * Description:
 *
 */
public interface ScreenshotService {
    // 根据网页地址，生成截图并上传至阿里云OSS
    String generateAndUploadScreenshot(String webUrl);
}
