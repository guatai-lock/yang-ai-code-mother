package com.guatai.yangaicodemother.service;

/**
 * ClassName: ScreenshotServiceImpl
 * Package: com.guatai.yangaicodemother.service
 * Description:
 *
 * @Author 尚硅谷-宋红康
 * @Create 2026/5/11 下午4:08
 * @Version 1.0
 */
public interface ScreenshotService {
    // 根据网页地址，生成截图并上传至阿里云OSS
    String generateAndUploadScreenshot(String webUrl);
}
