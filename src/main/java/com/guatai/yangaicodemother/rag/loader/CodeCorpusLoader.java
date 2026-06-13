package com.guatai.yangaicodemother.rag.loader;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.guatai.yangaicodemother.common.AppConstant;
import com.guatai.yangaicodemother.mapper.AppMapper;
import com.guatai.yangaicodemother.model.entity.App;
import com.guatai.yangaicodemother.model.enums.DeployStatusEnum;
import com.guatai.yangaicodemother.rag.config.RagProperties;
import com.guatai.yangaicodemother.rag.model.CodeDocument;
import com.guatai.yangaicodemother.rag.splitter.CodeSplitter;
import com.mybatisflex.core.query.QueryWrapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 精选应用语料库加载器
 *
 * <p>查询 {@code App} 表中 {@code priority=99} 且已部署的精选应用，
 * 读取代码文件并按语义拆分后向量化存储。</p>
 */
@Slf4j
@Component
public class CodeCorpusLoader {

    @Resource
    private AppMapper appMapper;

    @Resource
    private EmbeddingStore<TextSegment> embeddingStore;

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private RagProperties ragProperties;

    @Resource
    private List<CodeSplitter> splitters;

    /**
     * 加载所有精选应用的代码到向量库
     *
     * <p>幂等操作：按文件路径 + 修改时间去重，避免重复索引。</p>
     */
    public void loadFeaturedAppsCorpus() {
        if (!ragProperties.isEnabled()) {
            log.info("RAG 功能已禁用，跳过语料库加载");
            return;
        }

        // 查询所有精选且已部署的应用（逻辑删除的数据被自动过滤）
        List<App> featuredApps = appMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("priority", AppConstant.GOOD_APP_PRIORITY)
                        .isNotNull("deployKey")
                        .eq("isDelete", 0)
        );

        if (featuredApps.isEmpty()) {
            log.info("未查询到精选应用，语料库为空");
            return;
        }

        log.info("查询到 {} 个精选应用，开始加载语料库", featuredApps.size());

        // 记录已处理的文件路径，避免重复
        Set<String> processedFiles = new HashSet<>();
        int totalChunks = 0;

        for (App app : featuredApps) {
            try {
                totalChunks += loadSingleApp(app, processedFiles);
            } catch (Exception e) {
                log.error("加载精选应用 {} 的代码失败: {}", app.getId(), e.getMessage(), e);
            }
        }

        log.info("语料库加载完成: {} 个应用, {} 个代码片段", featuredApps.size(), totalChunks);
    }

    /**
     * 增量加载单个精选应用的代码
     *
     * @param appId 应用 ID
     */
    public void loadSingleApp(Long appId) {
        if (!ragProperties.isEnabled()) return;

        App app = appMapper.selectOneById(appId);
        if (app == null || !AppConstant.GOOD_APP_PRIORITY.equals(app.getPriority())
                || StrUtil.isBlank(app.getDeployKey())) {
            log.warn("应用 {} 不是精选应用或未部署，跳过加载", appId);
            return;
        }

        Set<String> processedFiles = new HashSet<>();
        int chunks = loadSingleApp(app, processedFiles);
        log.info("增量加载应用 {} 完成: {} 个代码片段", appId, chunks);
    }

    /**
     * 更新精选应用的 embedding（先删旧条目再新增）
     *
     * @param appId 应用 ID
     */
    public void updateApp(Long appId) {
        if (!ragProperties.isEnabled()) return;

        removeApp(appId);
        loadSingleApp(appId);
    }

    /**
     * 从向量库移除指定应用的 embedding
     *
     * @param appId 应用 ID
     */
    public void removeApp(Long appId) {
        if (!ragProperties.isEnabled()) return;

        // 使用 InMemoryEmbeddingStore 的 removeAll(Filter) 方法按 metadata 过滤删除
        if (embeddingStore instanceof dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore) {
            @SuppressWarnings("unchecked")
            var memStore = (dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore<TextSegment>) embeddingStore;
            Filter filter = metadataKey("appId").isEqualTo(String.valueOf(appId));
            memStore.removeAll(filter);
        }

        log.info("已从向量库移除应用 {} 的 embedding", appId);
    }

    // ======================== 私有方法 ========================

    private int loadSingleApp(App app, Set<String> processedFiles) {
        String appId = String.valueOf(app.getId());
        String codeGenType = app.getCodeGenType();
        String projectType = normalizeProjectType(codeGenType);

        String dirPath = resolveAppDir(app);
        if (dirPath == null) {
            log.warn("应用 {} 的代码目录不存在，跳过", app.getId());
            return 0;
        }

        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            log.warn("应用 {} 的代码目录不存在: {}", app.getId(), dirPath);
            return 0;
        }

        // 递归读取所有代码文件（最大深度 3 层）
        List<File> files = FileUtil.loopFiles(dir, 3, file -> {
            String name = file.getName().toLowerCase();
            return name.endsWith(".html") || name.endsWith(".vue")
                    || name.endsWith(".js") || name.endsWith(".css")
                    || name.endsWith(".java") || name.endsWith(".ts")
                    || name.endsWith(".json") || name.endsWith(".xml")
                    || name.endsWith(".yaml") || name.endsWith(".yml")
                    || name.endsWith(".properties");
        });

        if (files.isEmpty()) {
            log.debug("应用 {} 的代码目录无匹配文件: {}", app.getId(), dirPath);
            return 0;
        }

        List<TextSegment> allSegments = new ArrayList<>();

        for (File file : files) {
            String filePath = file.getAbsolutePath();
            String fileKey = appId + ":" + filePath + ":" + file.lastModified();
            if (processedFiles.contains(fileKey)) continue;
            processedFiles.add(fileKey);

            try {
                String content = FileUtil.readString(file, StandardCharsets.UTF_8);
                if (content.isBlank()) continue;

                List<CodeDocument> docs = splitFile(filePath, content, appId, projectType);
                for (CodeDocument doc : docs) {
                    TextSegment segment = TextSegment.from(
                            doc.getContent(),
                            new Metadata(
                                    Map.of("appId", doc.getAppId(),
                                            "projectType", doc.getProjectType(),
                                            "section", doc.getSection() != null ? doc.getSection() : "full")
                            )
                    );
                    allSegments.add(segment);
                }
            } catch (Exception e) {
                log.warn("读取文件 {} 失败: {}", filePath, e.getMessage());
            }
        }

        if (allSegments.isEmpty()) return 0;

        List<Embedding> embeddings = embeddingModel.embedAll(allSegments).content();
        embeddingStore.addAll(embeddings, allSegments);

        log.info("加载应用 {} 的代码: {} 文件 → {} chunks", app.getId(), files.size(), allSegments.size());
        return allSegments.size();
    }

    private String resolveAppDir(App app) {
        if (DeployStatusEnum.ONLINE.getValue().equals(app.getDeployStatus())) {
            return AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + app.getDeployKey();
        } else if (DeployStatusEnum.OFFLINE.getValue().equals(app.getDeployStatus())
                && StrUtil.isNotBlank(app.getArchivePath())) {
            return app.getArchivePath();
        }
        return null;
    }

    private List<CodeDocument> splitFile(String filePath, String content, String appId, String projectType) {
        for (CodeSplitter splitter : splitters) {
            if (splitter.supports(filePath)) {
                return splitter.chunk(filePath, content, appId, projectType);
            }
        }
        return Collections.emptyList();
    }

    private String normalizeProjectType(String codeGenType) {
        if (codeGenType == null) return "unknown";
        if (codeGenType.contains("vue")) return "vue";
        if (codeGenType.contains("html")) return "html";
        if (codeGenType.contains("multi")) return "multi";
        return codeGenType.toLowerCase();
    }
}
