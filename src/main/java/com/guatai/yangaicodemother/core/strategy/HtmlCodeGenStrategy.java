package com.guatai.yangaicodemother.core.strategy;

import com.guatai.yangaicodemother.ai.AiCodeGeneratorService;
import com.guatai.yangaicodemother.ai.model.HtmlCodeResult;
import com.guatai.yangaicodemother.core.handler.SimpleTextStreamHandler;
import com.guatai.yangaicodemother.core.handler.StreamHandler;
import com.guatai.yangaicodemother.core.parser.HtmlCodeParser;
import com.guatai.yangaicodemother.core.saver.HtmlCodeFileSaverTemplate;
import com.guatai.yangaicodemother.model.enums.CodeGenTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * HTML 单文件代码生成策略
 * <p>
 * 负责 HTML 类型的代码生成：流式/非流式调用 AI、解析 AI 输出、保存到文件系统。
 */
@Slf4j
@Component
public class HtmlCodeGenStrategy implements CodeGenStrategy {

    private final HtmlCodeParser parser = new HtmlCodeParser();
    private final HtmlCodeFileSaverTemplate saver = new HtmlCodeFileSaverTemplate();
    private final SimpleTextStreamHandler streamHandler = new SimpleTextStreamHandler();

    @Override
    public CodeGenTypeEnum getType() {
        return CodeGenTypeEnum.HTML;
    }

    @Override//流式生成
    public Flux<String> generateStream(AiCodeGeneratorService service, String enrichedMessage, Long appId) {
        Flux<String> codeStream = service.generateHtmlCodeStream(enrichedMessage);
        return processCodeStream(codeStream, appId);
    }

    @Override//非流式生成
    public File generateAndSave(AiCodeGeneratorService service, String enrichedMessage, Long appId) {
        HtmlCodeResult result = service.generateHtmlCode(enrichedMessage);
        return saver.saveCode(result, appId);
    }

    @Override
    public StreamHandler createStreamHandler() {
        return streamHandler;
    }

    /**
     * 拼接流式返回结果，在 doOnComplete 中解析并保存 HTML 代码
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, Long appId) {
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream
                .doOnNext(codeBuilder::append)
                .doOnComplete(() -> {
                    try {
                        String completeCode = codeBuilder.toString();
                        HtmlCodeResult parsed = parser.parseCode(completeCode);
                        File savedDir = saver.saveCode(parsed, appId);
                        log.info("HTML 代码保存成功: {}", savedDir.getAbsolutePath());
                    } catch (Exception e) {
                        log.error("HTML 代码保存失败: {}", e.getMessage(), e);
                    }
                });
    }
}
