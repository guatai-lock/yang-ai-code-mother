package com.guatai.yangaicodemother;
import com.guatai.yangaicodemother.ai.AiCodeGenTypeRoutingService;
import com.guatai.yangaicodemother.ai.AiCodeGenTypeRoutingServiceFactory;
import com.guatai.yangaicodemother.ai.AiCodeGeneratorService;
import com.guatai.yangaicodemother.ai.model.MultiFileCodeResult;
import com.guatai.yangaicodemother.core.AiCodeGeneratorFacade;
import com.guatai.yangaicodemother.model.enums.CodeGenTypeEnum;
import com.guatai.yangaicodemother.utils.WebScreenshotUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;
import java.util.List;
@SpringBootTest
@Slf4j

class YangAiCodeMotherApplicationTests {
    @Resource
    private AiCodeGeneratorService aiCodeGeneratorService;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;
    @Test
    void contextLoads() {
        MultiFileCodeResult multiFileCodeResult = aiCodeGeneratorService.generateMultiFileCode("生成一个登录页面，一百行代码以内");
        Assertions.assertNotNull(multiFileCodeResult);
    }
    @Test
    void contextLoads2() {
        String property = System.getProperty("user.dir");
        System.out.println(property);
    }
        @Test
        void parseHtmlCode() {
            Flux<String> stream = aiCodeGeneratorFacade
                    .generateAndSaveCodeStream("生成外卖平台页面，一百行代码以内", CodeGenTypeEnum.MULTI_FILE,123l, null);
            //流式输出测试记得阻塞测试
            List<String> block = stream.collectList().block();
            Assertions.assertNotNull(block);
        }
    @Test
    void generateVueProjectCodeStream() {
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(
                "简单的任务记录网站，总代码量不超过 200 行",
                CodeGenTypeEnum.VUE_PROJECT, 123L, null);
        // 阻塞等待所有数据收集完成
        List<String> result = codeStream.collectList().block();
        // 验证结果
        Assertions.assertNotNull(result);
        String completeContent = String.join("", result);
        Assertions.assertNotNull(completeContent);
    }
        @Test
        void saveWebPageScreenshot() {
            String testUrl = "https://www.codefather.cn";
            String webPageScreenshot = WebScreenshotUtils.saveWebPageScreenshot(testUrl);
            Assertions.assertNotNull(webPageScreenshot);
        }

        @Resource
        private AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService;

        @Test
        public void testRouteCodeGenType() {
            String userPrompt = "做一个简单的个人介绍页面";
            CodeGenTypeEnum result = aiCodeGenTypeRoutingService.routeCodeGenType(userPrompt);
            log.info("用户需求: {} -> {}", userPrompt, result.getValue());
            userPrompt = "做一个公司官网，需要首页、关于我们、联系我们三个页面";
            result = aiCodeGenTypeRoutingService.routeCodeGenType(userPrompt);
            log.info("用户需求: {} -> {}", userPrompt, result.getValue());
            userPrompt = "做一个电商管理系统，包含用户管理、商品管理、订单管理，需要路由和状态管理";
            result = aiCodeGenTypeRoutingService.routeCodeGenType(userPrompt);
            log.info("用户需求: {} -> {}", userPrompt, result.getValue());
        }

        @Resource
        private AiCodeGenTypeRoutingServiceFactory routingServiceFactory;

        @Test
        public void testConcurrentRoutingCalls() throws InterruptedException {
            String[] prompts = {
                    "做一个简单的HTML页面",
                    "做一个多页面网站项目",
                    "做一个Vue管理系统"
            };
            // 使用虚拟线程并发执行
            Thread[] threads = new Thread[prompts.length];
            for (int i = 0; i < prompts.length; i++) {
                final String prompt = prompts[i];
                final int index = i + 1;
                threads[i] = Thread.ofVirtual().start(() -> {
                    AiCodeGenTypeRoutingService service = routingServiceFactory.createAiCodeGenTypeRoutingService();
                    var result = service.routeCodeGenType(prompt);
                    log.info("线程 {}: {} -> {}", index, prompt, result.getValue());
                });
            }
            // 等待所有任务完成
            for (Thread thread : threads) {
                thread.join();
            }
        }
    }

