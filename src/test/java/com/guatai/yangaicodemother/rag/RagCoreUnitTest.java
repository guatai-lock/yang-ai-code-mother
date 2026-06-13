package com.guatai.yangaicodemother.rag;

import com.guatai.yangaicodemother.rag.model.CodeDocument;
import com.guatai.yangaicodemother.rag.retriever.ConditionalContentRetriever;
import com.guatai.yangaicodemother.rag.splitter.HtmlSplitter;
import com.guatai.yangaicodemother.rag.splitter.MultiSplitter;
import com.guatai.yangaicodemother.rag.splitter.VueSplitter;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RAG 核心组件单元测试
 *
 * <p>覆盖范围：
 * <ul>
 *   <li>{@link RagSwitchHolder} — ThreadLocal 开关行为</li>
 *   <li>{@link ConditionalContentRetriever} — 条件检索开关</li>
 *   <li>{@link HtmlSplitter} — HTML 语义区域拆分</li>
 *   <li>{@link VueSplitter} — Vue SFC 拆分</li>
 *   <li>{@link MultiSplitter} — 多文件兜底拆分</li>
 * </ul>
 */
@DisplayName("RAG 核心组件单元测试")
class RagCoreUnitTest {

    // ====================================================================
    //  RagSwitchHolder: ThreadLocal 开关
    // ====================================================================

    @Nested
    @DisplayName("RagSwitchHolder — ThreadLocal 开关")
    class RagSwitchHolderTest {

        @AfterEach
        void cleanUp() {
            RagSwitchHolder.clear();
        }

        @Test
        @DisplayName("默认状态为 false（禁用）")
        void defaultState_shouldBeDisabled() {
            assertFalse(RagSwitchHolder.isEnabled(), "默认 RAG 应禁用");
        }

        @Test
        @DisplayName("设为 true 后 isEnabled 返回 true")
        void setEnabled_shouldReflectState() {
            RagSwitchHolder.setEnabled(true);
            assertTrue(RagSwitchHolder.isEnabled(), "设置 true 后应返回 true");
        }

        @Test
        @DisplayName("clear 后恢复为 false")
        void clear_shouldResetToFalse() {
            RagSwitchHolder.setEnabled(true);
            RagSwitchHolder.clear();
            assertFalse(RagSwitchHolder.isEnabled(), "clear 后应恢复为 false");
        }

        @Test
        @DisplayName("再次设为 false 后 isEnabled 返回 false")
        void setDisabled_shouldReflectState() {
            RagSwitchHolder.setEnabled(true);
            RagSwitchHolder.setEnabled(false);
            assertFalse(RagSwitchHolder.isEnabled(), "设为 false 后应返回 false");
        }

        @Test
        @DisplayName("多线程隔离：各线程独立状态")
        void threadLocal_shouldBeIsolated() throws Exception {
            RagSwitchHolder.setEnabled(true);

            Thread other = new Thread(() -> {
                assertFalse(RagSwitchHolder.isEnabled(), "子线程默认应为 false");
                RagSwitchHolder.setEnabled(true);
                assertTrue(RagSwitchHolder.isEnabled(), "子线程设置 true 后应有变化");
                RagSwitchHolder.clear();
                assertFalse(RagSwitchHolder.isEnabled(), "子线程 clear 后应恢复");
            });
            other.start();
            other.join(1000);

            assertTrue(RagSwitchHolder.isEnabled(), "主线程状态应不受子线程影响");
        }
    }

    // ====================================================================
    //  ConditionalContentRetriever: 条件检索
    // ====================================================================

    @Nested
    @DisplayName("ConditionalContentRetriever — 条件检索")
    class ConditionalContentRetrieverTest {

        @AfterEach
        void cleanUp() {
            RagSwitchHolder.clear();
        }

        @Test
        @DisplayName("RAG 禁用时返回空列表，不调用 delegate")
        void disabled_shouldReturnEmptyAndNotCallDelegate() {
            ContentRetriever delegate = mock(ContentRetriever.class);
            ConditionalContentRetriever retriever = new ConditionalContentRetriever(delegate);

            RagSwitchHolder.setEnabled(false);
            List<Content> result = retriever.retrieve(Query.from("test"));

            assertTrue(result.isEmpty(), "禁用时应返回空列表");
            verify(delegate, never()).retrieve(any());
        }

        @Test
        @DisplayName("RAG 启用时调用 delegate 并返回结果")
        void enabled_shouldDelegateAndReturnResults() {
            ContentRetriever delegate = mock(ContentRetriever.class);
            List<Content> mockResults = List.of(Content.from("result1"), Content.from("result2"));
            when(delegate.retrieve(any())).thenReturn(mockResults);

            ConditionalContentRetriever retriever = new ConditionalContentRetriever(delegate);
            RagSwitchHolder.setEnabled(true);
            List<Content> result = retriever.retrieve(Query.from("test query"));

            assertEquals(2, result.size(), "应返回 delegate 的检索结果");
            assertEquals("result1", result.get(0).textSegment().text());
            verify(delegate, times(1)).retrieve(any());
        }

        @Test
        @DisplayName("多实例独立工作：每个实例包装自己的 delegate")
        void multipleInstances_shouldWorkIndependently() {
            ContentRetriever delegate1 = mock(ContentRetriever.class);
            ContentRetriever delegate2 = mock(ContentRetriever.class);
            when(delegate1.retrieve(any())).thenReturn(List.of(Content.from("a")));
            when(delegate2.retrieve(any())).thenReturn(List.of(Content.from("b")));

            ConditionalContentRetriever r1 = new ConditionalContentRetriever(delegate1);
            ConditionalContentRetriever r2 = new ConditionalContentRetriever(delegate2);

            RagSwitchHolder.setEnabled(true);

            assertEquals("a", r1.retrieve(Query.from("q")).get(0).textSegment().text());
            assertEquals("b", r2.retrieve(Query.from("q")).get(0).textSegment().text());
            verify(delegate1, times(1)).retrieve(any());
            verify(delegate2, times(1)).retrieve(any());
        }
    }

    // ====================================================================
    //  HtmlSplitter: HTML 语义拆分
    // ====================================================================

    @Nested
    @DisplayName("HtmlSplitter — HTML 语义区域拆分")
    class HtmlSplitterTest {

        private final HtmlSplitter splitter = new HtmlSplitter();
        private static final String APP_ID = "app123";
        private static final String PROJECT_TYPE = "html";

        @Test
        @DisplayName("supports 仅匹配 .html 文件")
        void supports_shouldMatchHtmlOnly() {
            assertTrue(splitter.supports("index.html"));
            assertTrue(splitter.supports("page.HTML"));
            assertTrue(splitter.supports("src/components/Header.html"));
            assertFalse(splitter.supports("index.vue"));
            assertFalse(splitter.supports("script.js"));
            assertFalse(splitter.supports("style.css"));
            assertFalse(splitter.supports(null));
        }

        @Test
        @DisplayName("null 或空内容返回空列表")
        void nullOrEmptyContent_shouldReturnEmpty() {
            assertTrue(splitter.chunk("test.html", null, APP_ID, PROJECT_TYPE).isEmpty());
            assertTrue(splitter.chunk("test.html", "", APP_ID, PROJECT_TYPE).isEmpty());
            assertTrue(splitter.chunk("test.html", "   ", APP_ID, PROJECT_TYPE).isEmpty());
        }

        @Test
        @DisplayName("小文件（≤200 字符）整体保留为 full")
        void smallFile_shouldKeepAsFull() {
            String smallContent = "<html><body><h1>Hello</h1></body></html>";
            List<CodeDocument> chunks = splitter.chunk("small.html", smallContent, APP_ID, PROJECT_TYPE);

            assertEquals(1, chunks.size(), "小文件应整体保留");
            assertEquals("full", chunks.get(0).getSection());
            assertEquals(smallContent.trim(), chunks.get(0).getContent());
            assertEquals(APP_ID, chunks.get(0).getAppId());
            assertEquals(PROJECT_TYPE, chunks.get(0).getProjectType());
        }

        @Test
        @DisplayName("按语义区域拆分 header/footer/section")
        void semanticRegions_shouldBeSplit() {
            String content = "<html>\n" +
                    "<header>导航栏</header>\n" +
                    "<div class=\"hero\">主标题区域</div>\n" +
                    "<section>内容区域</section>\n" +
                    "<footer>版权信息</footer>\n" +
                    "</html>";

            String largeContent = content.repeat(10);

            List<CodeDocument> chunks = splitter.chunk("page.html", largeContent, APP_ID, PROJECT_TYPE);

            assertFalse(chunks.isEmpty(), "应拆分出至少一个 chunk");

            boolean hasHeader = chunks.stream().anyMatch(c -> "header".equals(c.getSection()));
            boolean hasHero = chunks.stream().anyMatch(c -> "hero".equals(c.getSection()));
            boolean hasSection = chunks.stream().anyMatch(c -> "section".equals(c.getSection()));
            boolean hasFooter = chunks.stream().anyMatch(c -> "footer".equals(c.getSection()));

            assertTrue(hasHeader, "应包含 header 区域");
            assertTrue(hasHero, "应包含 hero 区域");
            assertTrue(hasSection, "应包含 section 区域");
            assertTrue(hasFooter, "应包含 footer 区域");
        }

        @Test
        @DisplayName("大文件无匹配区域时整体保留")
        void largeFileWithoutSections_shouldKeepAsFull() {
            String content = "<div>".repeat(50) + "无语义标签的内容" + "</div>".repeat(50);

            List<CodeDocument> chunks = splitter.chunk("plain.html", content, APP_ID, PROJECT_TYPE);

            assertEquals(1, chunks.size(), "无匹配区域应整体保留");
            assertEquals("full", chunks.get(0).getSection());
        }

        @Test
        @DisplayName("所有 chunk 的 metadata 正确")
        void allChunks_shouldCarryCorrectMetadata() {
            String content = "<header>标题</header>\n".repeat(50);
            List<CodeDocument> chunks = splitter.chunk("test.html", content, APP_ID, PROJECT_TYPE);

            chunks.forEach(c -> {
                assertEquals(APP_ID, c.getAppId(), "appId 应一致");
                assertEquals(PROJECT_TYPE, c.getProjectType(), "projectType 应一致");
                assertNotNull(c.getSection(), "section 不应为 null");
                assertNotNull(c.getContent(), "content 不应为 null");
                assertFalse(c.getContent().isBlank(), "content 不应为空");
            });
        }
    }

    // ====================================================================
    //  VueSplitter: Vue SFC 拆分
    // ====================================================================

    @Nested
    @DisplayName("VueSplitter — Vue SFC 拆分")
    class VueSplitterTest {

        private final VueSplitter splitter = new VueSplitter();
        private static final String APP_ID = "app456";
        private static final String PROJECT_TYPE = "vue";

        @Test
        @DisplayName("supports 仅匹配 .vue 文件")
        void supports_shouldMatchVueOnly() {
            assertTrue(splitter.supports("App.vue"));
            assertTrue(splitter.supports("components/Button.VUE"));
            assertFalse(splitter.supports("index.html"));
            assertFalse(splitter.supports("script.js"));
            assertFalse(splitter.supports("style.css"));
            assertFalse(splitter.supports(null));
        }

        @Test
        @DisplayName("null 或空内容返回空列表")
        void nullOrEmptyContent_shouldReturnEmpty() {
            assertTrue(splitter.chunk("test.vue", null, APP_ID, PROJECT_TYPE).isEmpty());
            assertTrue(splitter.chunk("test.vue", "", APP_ID, PROJECT_TYPE).isEmpty());
        }

        @Test
        @DisplayName("小文件（≤200 字符）整体保留")
        void smallFile_shouldKeepAsFull() {
            String smallVue = "<template><div>Hello</div></template>";
            List<CodeDocument> chunks = splitter.chunk("Small.vue", smallVue, APP_ID, PROJECT_TYPE);

            assertEquals(1, chunks.size());
            assertEquals("full", chunks.get(0).getSection());
        }

        @Test
        @DisplayName("大文件按 template/script/style 拆分")
        void largeFile_shouldSplitIntoSections() {
            String vueContent = "" +
                    "<template>\n" +
                    "  <div class=\"app\">\n" +
                    "    <h1>{{ title }}</h1>\n" +
                    "  </div>\n" +
                    "</template>\n" +
                    "\n" +
                    "<script setup>\n" +
                    "import { ref } from 'vue'\n" +
                    "const title = ref('Hello')\n" +
                    "</script>\n" +
                    "\n" +
                    "<style scoped>\n" +
                    ".app { color: red; }\n" +
                    "</style>\n";

            String largeVue = vueContent.repeat(5);

            List<CodeDocument> chunks = splitter.chunk("App.vue", largeVue, APP_ID, PROJECT_TYPE);

            assertFalse(chunks.isEmpty(), "应拆分出 chunks");

            boolean hasTemplate = chunks.stream().anyMatch(c -> "template".equals(c.getSection()));
            boolean hasScript = chunks.stream().anyMatch(c -> "script".equals(c.getSection()));
            boolean hasStyle = chunks.stream().anyMatch(c -> "style".equals(c.getSection()));

            assertTrue(hasTemplate, "应包含 template 区域");
            assertTrue(hasScript, "应包含 script 区域");
            assertTrue(hasStyle, "应包含 style 区域");

            chunks.stream()
                    .filter(c -> "template".equals(c.getSection()))
                    .forEach(c -> assertTrue(c.getContent().contains("<template"), "template 内容应包含 <template> 标签"));

            chunks.stream()
                    .filter(c -> "script".equals(c.getSection()))
                    .forEach(c -> assertTrue(c.getContent().contains("<script"), "script 内容应包含 <script> 标签"));

            chunks.forEach(c -> {
                assertEquals(APP_ID, c.getAppId());
                assertEquals(PROJECT_TYPE, c.getProjectType());
            });
        }

        @Test
        @DisplayName("只有 template 时仅拆出一个 template chunk")
        void onlyTemplate_shouldProduceSingleChunk() {
            String content = ("<template><div>只有模板</div></template>").repeat(50);
            List<CodeDocument> chunks = splitter.chunk("Simple.vue", content, APP_ID, PROJECT_TYPE);

            assertEquals(1, chunks.size());
            assertEquals("template", chunks.get(0).getSection());
        }

        @Test
        @DisplayName("没有 SFC 标签时整体保留")
        void noSfcTags_shouldKeepAsFull() {
            String content = "console.log('not a vue sfc');\n".repeat(50);
            List<CodeDocument> chunks = splitter.chunk("plain.vue", content, APP_ID, PROJECT_TYPE);

            assertEquals(1, chunks.size(), "无 SFC 标签应整体保留");
            assertEquals("full", chunks.get(0).getSection());
        }
    }

    // ====================================================================
    //  MultiSplitter: 多文件兜底拆分
    // ====================================================================

    @Nested
    @DisplayName("MultiSplitter — 多文件兜底拆分")
    class MultiSplitterTest {

        private final MultiSplitter splitter = new MultiSplitter();

        @Test
        @DisplayName("supports 始终返回 true（兜底拆分器）")
        void supports_shouldAlwaysReturnTrue() {
            assertTrue(splitter.supports("anything.java"));
            assertTrue(splitter.supports("style.css"));
            assertTrue(splitter.supports("data.json"));
            assertTrue(splitter.supports("config.yaml"));
            assertTrue(splitter.supports("noExtension"));
        }

        @Test
        @DisplayName("每个文件保留为一个 chunk")
        void eachFile_shouldBeOneChunk() {
            String content = "public class Hello {}";
            List<CodeDocument> chunks = splitter.chunk("Hello.java", content, "app1", "multi");

            assertEquals(1, chunks.size());
            assertEquals("full", chunks.get(0).getSection());
            assertEquals(content, chunks.get(0).getContent());
            assertEquals("app1", chunks.get(0).getAppId());
            assertEquals("multi", chunks.get(0).getProjectType());
            assertTrue(chunks.get(0).getFileName().endsWith("Hello.java"));
        }

        @Test
        @DisplayName("null 或空内容返回空列表")
        void nullOrEmptyContent_shouldReturnEmpty() {
            assertTrue(splitter.chunk("test.js", null, "app1", "multi").isEmpty());
            assertTrue(splitter.chunk("test.js", "", "app1", "multi").isEmpty());
        }
    }
}
