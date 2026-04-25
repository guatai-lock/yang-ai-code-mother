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
        File file = aiCodeGeneratorFacade.
                generateAndSaveCode("生成一个个人博客页面，一百行代码以内", CodeGenTypeEnum.MULTI_FILE);
        Assertions.assertNotNull(file );
    }
        @Test
        void parseHtmlCode() {
            Flux<String> stream = aiCodeGeneratorFacade
                    .generateAndSaveCodeStream("生成外卖平台页面，一百行代码以内", CodeGenTypeEnum.MULTI_FILE);
            //流式输出测试记得阻塞测试
            List<String> block = stream.collectList().block();
            Assertions.assertNotNull(block);
        }
}


