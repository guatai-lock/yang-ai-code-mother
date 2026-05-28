package com.guatai.yangaicodemother.utils;
import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.guatai.yangaicodemother.exception.BusinessException;
import com.guatai.yangaicodemother.exception.ErrorCode;
import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
public class WebScreenshotUtils {

    private static final int POOL_SIZE = 3;
    private static final BlockingQueue<WebDriver> driverPool = new LinkedBlockingQueue<>(POOL_SIZE);

    static {
        final int DEFAULT_WIDTH = 1600;
        final int DEFAULT_HEIGHT = 900;
        for (int i = 0; i < POOL_SIZE; i++) {
            try {
                WebDriver driver = initChromeDriver(DEFAULT_WIDTH, DEFAULT_HEIGHT);
                driverPool.offer(driver);
            } catch (Exception e) {
                log.error("初始化第 {} 个 ChromeDriver 失败", i + 1, e);
            }
        }
        log.info("WebDriver 连接池初始化完成，实际可用驱动: {}/{}", driverPool.size(), POOL_SIZE);
    }

    private static WebDriver borrowDriver() {
        try {
            return driverPool.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取 WebDriver 被中断");
        }
    }

    private static void returnDriver(WebDriver driver, boolean success) {
        if (driver == null) return;
        if (success && isDriverHealthy(driver)) {
            driverPool.offer(driver);
        } else {
            quitDriver(driver);
            WebDriver newDriver = initChromeDriver(1600, 900);
            if (newDriver != null) {
                driverPool.offer(newDriver);
            }
        }
    }

    private static boolean isDriverHealthy(WebDriver driver) {
        try {
            driver.getTitle();
            return true;
        } catch (Exception e) {
            log.warn("WebDriver 健康检查失败，将重建", e);
            return false;
        }
    }

    private static void quitDriver(WebDriver driver) {
        try {
            driver.quit();
        } catch (Exception e) {
            log.warn("关闭 WebDriver 时出现异常", e);
        }
    }

    @PreDestroy
    public void destroy() {
        List<WebDriver> drivers = new ArrayList<>();
        driverPool.drainTo(drivers);
        log.info("正在关闭 {} 个 WebDriver 实例", drivers.size());
        for (WebDriver driver : drivers) {
            quitDriver(driver);
        }
    }

    /**
     * 生成网页截图
     *为保证通用性，不传入与业务相关的参数
     * @param webUrl 网页URL
     * @return 压缩后的截图文件路径，失败返回null
     */
    public static String saveWebPageScreenshot(String webUrl) {
        if (StrUtil.isBlank(webUrl)) {
            log.error("网页URL不能为空");
            return null;
        }
        WebDriver driver = borrowDriver();
        boolean success = false;
        try {
            String rootPath = System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "screenshots"
                    + File.separator + UUID.randomUUID().toString().substring(0, 8);
            FileUtil.mkdir(rootPath);
            final String IMAGE_SUFFIX = ".png";
            String imageSavePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + IMAGE_SUFFIX;
            driver.get(webUrl);
            waitForPageLoad(driver);
            byte[] screenshotBytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            saveImage(screenshotBytes, imageSavePath);
            log.info("原始截图保存成功: {}", imageSavePath);
            final String COMPRESSION_SUFFIX = "_compressed.jpg";
            String compressedImagePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + COMPRESSION_SUFFIX;
            compressImage(imageSavePath, compressedImagePath);
            log.info("压缩图片保存成功: {}", compressedImagePath);
            FileUtil.del(imageSavePath);
            success = true;
            return compressedImagePath;
        } catch (Exception e) {
            log.error("网页截图失败: {}", webUrl, e);
            return null;
        } finally {
            returnDriver(driver, success);
        }
    }

    private static WebDriver initChromeDriver(int width, int height) {
        try {
            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--disable-gpu");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments(String.format("--window-size=%d,%d", width, height));
            options.addArguments("--disable-extensions");
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            WebDriver driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            return driver;
        } catch (Exception e) {
            log.error("初始化 chrome 浏览器失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化 chrome 浏览器失败");
        }
    }

    private static void saveImage(byte[] imageBytes, String imagePath) {
        try {
            FileUtil.writeBytes(imageBytes, imagePath);
        } catch (Exception e) {
            log.error("保存图片失败: {}", imagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存图片失败");
        }
    }

    private static void compressImage(String originalImagePath, String compressedImagePath) {
        final float COMPRESSION_QUALITY = 0.3f;
        try {
            ImgUtil.compress(
                    FileUtil.file(originalImagePath),
                    FileUtil.file(compressedImagePath),
                    COMPRESSION_QUALITY
            );
        } catch (Exception e) {
            log.error("压缩图片失败: {} -> {}", originalImagePath, compressedImagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "压缩图片失败");
        }
    }

    private static void waitForPageLoad(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(webDriver ->
                    ((JavascriptExecutor) webDriver).executeScript("return document.readyState")
                            .equals("complete")
            );
            Thread.sleep(2000);
            log.info("页面加载完成");
        } catch (Exception e) {
            log.error("等待页面加载时出现异常，继续执行截图", e);
        }
    }

}
