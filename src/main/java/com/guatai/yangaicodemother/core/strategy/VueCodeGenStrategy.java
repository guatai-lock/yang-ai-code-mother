package com.guatai.yangaicodemother.core.strategy;

import cn.hutool.json.JSONUtil;
import com.guatai.yangaicodemother.ai.AiCodeGeneratorService;
import com.guatai.yangaicodemother.ai.model.message.AiResponseMessage;
import com.guatai.yangaicodemother.ai.model.message.ToolExecutedMessage;
import com.guatai.yangaicodemother.ai.model.message.ToolRequestMessage;
import com.guatai.yangaicodemother.common.AppConstant;
import com.guatai.yangaicodemother.core.builder.VueProjectBuilder;
import com.guatai.yangaicodemother.core.handler.JsonMessageStreamHandler;
import com.guatai.yangaicodemother.core.handler.StreamHandler;
import com.guatai.yangaicodemother.model.enums.CodeGenTypeEnum;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * Vue 项目代码生成策略
 * <p>
 * 负责 VUE_PROJECT 类型：使用 TokenStream + 工具调用的流式代码生成，
 * 并在完成后执行 npm build 构建 Vue 项目。
 */
@Slf4j
@Component
public class VueCodeGenStrategy implements CodeGenStrategy {

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private JsonMessageStreamHandler jsonMessageStreamHandler;

    @Override
    public CodeGenTypeEnum getType() {
        return CodeGenTypeEnum.VUE_PROJECT;
    }

    @Override//流式生成
    public Flux<String> generateStream(AiCodeGeneratorService service, String enrichedMessage, Long appId) {
        TokenStream tokenStream = service.generateVueProjectCodeStream(appId, enrichedMessage);
        return processTokenStream(tokenStream, appId);
    }

    @Override
    public File generateAndSave(AiCodeGeneratorService service, String enrichedMessage, Long appId) {
        throw new UnsupportedOperationException("Vue 项目不支持非流式代码生成");
    }

    @Override
    public StreamHandler createStreamHandler() {
        return jsonMessageStreamHandler;
    }

    /**
     * 将 TokenStream 转换为 Flux{@literal <String>}，并传递工具调用信息
     * <p>
     * AI 自主调用工具保存文件，此方法仅封装过程中 AI 输出的内容。
     */
    private Flux<String> processTokenStream(TokenStream tokenStream, Long appId) {
        return Flux.create(sink -> {
            tokenStream.onPartialResponse((String partialResponse) -> {
                        // 处理 AI 响应（工具调用之外，结束之前）部分
                        AiResponseMessage msg = new AiResponseMessage(partialResponse);
                        sink.next(JSONUtil.toJsonStr(msg));
                    })
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                        // 处理工具调用请求部分
                        ToolRequestMessage msg = new ToolRequestMessage(toolExecutionRequest);
                        sink.next(JSONUtil.toJsonStr(msg));
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        // 处理工具调用完毕部分
                        ToolExecutedMessage msg = new ToolExecutedMessage(toolExecution);
                        sink.next(JSONUtil.toJsonStr(msg));
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        // 执行 Vue 项目构建（同步执行，确保预览时项目已就绪）
                        String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR
                                + File.separator + "vue_project_" + appId;
                        vueProjectBuilder.buildProject(projectPath);
                        // 响应完成，结束流
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        error.printStackTrace();
                        sink.error(error);
                    })
                    .start();
        });
    }
}
