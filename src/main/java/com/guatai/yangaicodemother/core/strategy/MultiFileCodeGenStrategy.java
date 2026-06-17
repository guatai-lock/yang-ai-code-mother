package com.guatai.yangaicodemother.core.strategy;

import com.guatai.yangaicodemother.ai.AiCodeGeneratorService;
import com.guatai.yangaicodemother.ai.model.MultiFileCodeResult;
import com.guatai.yangaicodemother.core.handler.SimpleTextStreamHandler;
import com.guatai.yangaicodemother.core.handler.StreamHandler;
import com.guatai.yangaicodemother.core.parser.MultiFileCodeParser;
import com.guatai.yangaicodemother.core.saver.MultiFileCodeFileSaverTemplate;
import com.guatai.yangaicodemother.model.enums.CodeGenTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * 多文件代码生成策略
 * <p>
 * 负责 MULTI_FILE 类型的代码生成：流式/非流式调用 AI、解析 AI 输出、保存到文件系统。
 */
@Slf4j
@Component
public class MultiFileCodeGenStrategy implements CodeGenStrategy {

    private final MultiFileCodeParser parser = new MultiFileCodeParser();
    private final MultiFileCodeFileSaverTemplate saver = new MultiFileCodeFileSaverTemplate();
    private final SimpleTextStreamHandler streamHandler = new SimpleTextStreamHandler();

    @Override
    public CodeGenTypeEnum getType() {
        return CodeGenTypeEnum.MULTI_FILE;
    }

    @Override//流式生成
    public Flux<String> generateStream(AiCodeGeneratorService service, String enrichedMessage, Long appId) {
        Flux<String> codeStream = service.generateMultiFileCodeStream(enrichedMessage);
        return processCodeStream(codeStream, appId);
    }

    @Override//非流式生成
    public File generateAndSave(AiCodeGeneratorService service, String enrichedMessage, Long appId) {
        MultiFileCodeResult result = service.generateMultiFileCode(enrichedMessage);
        return saver.saveCode(result, appId);
    }

    @Override
    public StreamHandler createStreamHandler() {
        return streamHandler;
    }

    /**
     * 拼接流式返回结果，在 doOnComplete 中解析并保存多文件代码
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, Long appId) {
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream
                .doOnNext(codeBuilder::append)
                .doOnComplete(() -> {
                    try {
                        String completeCode = codeBuilder.toString();
                        MultiFileCodeResult parsed = parser.parseCode(completeCode);
                        File savedDir = saver.saveCode(parsed, appId);
                        log.info("多文件代码保存成功: {}", savedDir.getAbsolutePath());
                    } catch (Exception e) {
                        log.error("多文件代码保存失败: {}", e.getMessage(), e);
                    }
                });
    }
}
