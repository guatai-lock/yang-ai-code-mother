package com.guatai.yangaicodemother.service;

import jakarta.servlet.http.HttpServletResponse;

/**
 * ClassName: ProjectDownloadService
 * Package: com.guatai.yangaicodemother.service
 * Description:
 *
 */
public interface ProjectDownloadService {
    /***
     *  根据项目路径下载项目压缩包
     * @param projectPath 项目路径
     * @param downloadFileName  下载文件名
     * @param response HTTP响应对象
     */
    void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response);
}
