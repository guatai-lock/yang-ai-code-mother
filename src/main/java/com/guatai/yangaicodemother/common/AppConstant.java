package com.guatai.yangaicodemother.common;

import java.io.File;

/**
 * ClassName: cons
 * Package: com.guatai.yangaicodemother.common
 * Description:
 *
 * @Author 尚硅谷-宋红康
 * @Create 2026/4/25 下午8:16
 * @Version 1.0
 */
public interface AppConstant {

    /**
     * 精选应用的优先级
     */
    Integer GOOD_APP_PRIORITY = 99;

    /**
     * 默认应用优先级
     */
    Integer DEFAULT_APP_PRIORITY = 0;
    /**
     * 应用生成目录
     */
    String CODE_OUTPUT_ROOT_DIR = System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "code_output";

    /**
     * 应用部署目录
     */
    String CODE_DEPLOY_ROOT_DIR = System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "code_deploy";

    /**
     * 应用归档目录
     */
    String CODE_ARCHIVE_ROOT_DIR = System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "code_archive";

    /**
     * 应用部署域名（含端口）
     * 注意：必须包含端口号，因为 Spring Boot 应用运行在 8123 端口
     */
    String CODE_DEPLOY_HOST = "http://localhost:8123/api/static";

}
