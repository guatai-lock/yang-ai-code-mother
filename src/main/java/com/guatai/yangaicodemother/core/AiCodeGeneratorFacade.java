package com.guatai.yangaicodemother.core;

/**
 * ClassName: AI
 * Package: com.guatai.yangaicodemother.core
 * Description:
 *
 * @Author 尚硅谷-宋红康
 * @Create 2026/4/24 下午10:02
 * @Version 1.0
 */

import cn.hutool.json.JSONUtil;
import com.guatai.yangaicodemother.ai.AiCodeGeneratorService;
import com.guatai.yangaicodemother.ai.AiCodeGeneratorServiceFactory;
import com.guatai.yangaicodemother.ai.model.HtmlCodeResult;
import com.guatai.yangaicodemother.ai.model.MultiFileCodeResult;
import com.guatai.yangaicodemother.ai.model.message.AiResponseMessage;
import com.guatai.yangaicodemother.ai.model.message.ToolExecutedMessage;
import com.guatai.yangaicodemother.ai.model.message.ToolRequestMessage;
import com.guatai.yangaicodemother.core.parser.CodeParserExecutor;
import com.guatai.yangaicodemother.core.saver.CodeFileSaverExecutor;
import com.guatai.yangaicodemother.exception.BusinessException;
import com.guatai.yangaicodemother.exception.ErrorCode;
import com.guatai.yangaicodemother.model.enums.CodeGenTypeEnum;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import java.io.File;

/**
 * AI 代码生成外观类，组合生成和保存功能
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    @Resource
    @Lazy
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;
    /**
     * 统一入口：根据类型生成并保存代码（流式，使用 appId）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId           应用 ID
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        // 根据 appId 获取对应的 AiCodeGeneratorService类实例
        AiCodeGeneratorService codeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId,codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                // 生成 HTML 代码流
                Flux<String> codeStream = codeGeneratorService.generateHtmlCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                // 生成多文件代码流
                Flux<String> codeStream = codeGeneratorService.generateMultiFileCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            case VUE_PROJECT -> {
                // 生成 Vue 项目代码流
                TokenStream codeStream = codeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
                yield processTokenStream(codeStream);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }
    /**
     * 统一入口：根据类型生成并保存代码（使用 appId）非流式返回
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        // 根据 appId 获取对应的 AiCodeGeneratorService
        AiCodeGeneratorService codeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                HtmlCodeResult result = codeGeneratorService.generateHtmlCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = codeGeneratorService.generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }
    /**
     * 通用流式代码处理方法（使用 appId）
     * 拼接流式返回结果并解析保存代码（使用 appId）
     * @param codeStream  代码流
     * @param codeGenType 代码生成类型
     * @param appId       应用 ID
     * @return 流式响应
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType, Long appId) {
        StringBuilder codeBuilder = new StringBuilder();
        // 实时收集代码片段
        return codeStream.
                //返回过程中实时拼接,用于流式返回完成后解析并保存代码
                doOnNext(codeBuilder::append).
                doOnComplete(() -> {
            // 流式返回完成后保存代码
            try {
                String completeCode = codeBuilder.toString();
                // 使用执行器解析代码
                Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                // 使用执行器保存代码
                File savedDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId);
                log.info("保存成功，路径为：" + savedDir.getAbsolutePath());
            } catch (Exception e) {
                log.error("保存失败: {}", e.getMessage());
            }
        });
    }
    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     *此处为ai自主调用工具保存文件，代码仅将过程中ai输出的内容进行封装处理
     * @param tokenStream TokenStream 对象
     * @return Flux<String> 流式响应
     */
    private Flux<String> processTokenStream(TokenStream tokenStream) {
        return Flux.create(sink -> {
            tokenStream.onPartialResponse((String partialResponse) -> {
                        // 处理ai响应（工具调用之外，结束之前）部分
                         // 封装为 AiResponseMessage 对象
                        AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                        // 转换为 JSON 字符串
                        sink.next(JSONUtil.toJsonStr(aiResponseMessage));
                    })
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                        // 处理工具调用请求部分
                        ToolRequestMessage toolRequestMessage = new ToolRequestMessage(toolExecutionRequest);
                        sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        // 处理工具调用完毕部分
                        ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                        sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        // 响应完成，结束流
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        // 处理错误部分
                        error.printStackTrace();
                        sink.error(error);
                    })
                    .start();
        });
    }

}

