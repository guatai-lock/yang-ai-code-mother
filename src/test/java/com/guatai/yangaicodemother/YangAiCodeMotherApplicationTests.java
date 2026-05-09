package com.guatai.yangaicodemother;
import com.guatai.yangaicodemother.ai.AiCodeGeneratorService;
import com.guatai.yangaicodemother.ai.model.MultiFileCodeResult;
import com.guatai.yangaicodemother.core.AiCodeGeneratorFacade;
import com.guatai.yangaicodemother.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;
import java.io.File;
import java.util.List;
@SpringBootTest
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
                    .generateAndSaveCodeStream("生成外卖平台页面，一百行代码以内", CodeGenTypeEnum.MULTI_FILE,123l);
            //流式输出测试记得阻塞测试
            List<String> block = stream.collectList().block();
            Assertions.assertNotNull(block);
        }
    @Test
    void generateVueProjectCodeStream() {
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(
                "简单的任务记录网站，总代码量不超过 200 行",
                CodeGenTypeEnum.VUE_PROJECT, 123L);
        // 阻塞等待所有数据收集完成
        List<String> result = codeStream.collectList().block();
        // 验证结果
        Assertions.assertNotNull(result);
        String completeContent = String.join("", result);
        Assertions.assertNotNull(completeContent);
    }

}


