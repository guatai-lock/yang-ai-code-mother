# AI 网站前端代码生成平台

> 作者：[呱太](https://github.com/guatai-lock?tab=repositories)

## 一、项目介绍

基于 **Spring Boot 3 + LangChain4j + Vue 3** 构建的企业级 AI 代码生成平台。用户通过自然语言描述需求，AI 自动分析并选择最优策略（HTML 单文件 / 多文件工程 / Vue 项目），流式生成完整可运行的网站代码，支持实时预览、对话式修改、一键部署上线。

> **后端**：Spring Boot 3.5 + LangChain4j 1.1 + Servlet（Tomcat 异步 Servlet）+ Reactor（langchain4j-reactor）+ MyBatis-Flex 1.11 + MySQL + Redis + Redisson + Selenium ChromeDriver + 腾讯云 COS + Nacos + Prometheus + Grafana
>
> **前端**（独立仓库）：Vue 3 + Vite + TypeScript + Ant Design Vue

### 展示

<img width="3555" height="2000" alt="主页预览" src="https://github.com/user-attachments/assets/f84bfec8-1310-46f5-b42c-1122ef41cb39" />

### 4 大核心能力

#### 1️⃣ 智能代码生成

用户输入需求描述，AI 自动分析并选择合适的生成策略，通过工具调用生成代码文件，采用 SSE 流式输出让用户实时看到 AI 的执行过程。

- **HTML 单文件生成**：快速生成完整的 HTML 页面
- **多文件工程生成**：生成包含 HTML、CSS、JS 的多文件项目
- **Vue 项目生成**：生成完整的 Vue 3 前端项目（基于 AI 工具调用自主管理文件）

#### 2️⃣ 可视化编辑

生成的应用将实时展示，可以进入编辑模式，自由选择网页元素并且和 AI 对话来快速修改页面，直到满意为止。

- **实时预览**：生成结果即时可见
- **对话式修改**：通过自然语言指令调整页面
- **智能理解**：AI 理解用户意图并精准修改
- **图片上传**：支持用户自主上传图片，AI 在生成网页时优先使用上传的图片资源

#### 3️⃣ 一键部署分享

可以将生成的应用一键部署到云端并自动截取封面图，获得可访问的地址进行分享，同时支持完整项目源码下载。

- **一键部署**：自动生成 6 位部署标识和访问链接
- **自动截图**：Selenium ChromeDriver 连接池（3 实例，带健康检查）
- **源码下载**：支持完整项目打包下载
- **部署状态管理**：ONLINE ↔ OFFLINE 状态切换，自动文件归档

#### 4️⃣ 企业级管理

提供用户管理、应用管理、系统监控、业务指标监控等后台功能，管理员可以设置精选应用、监控 AI 调用情况和系统性能。

- **用户管理**：用户注册、登录、权限控制
- **应用管理**：应用创建、编辑、删除、精选设置
- **对话历史**：按应用查看完整对话记录，支持游标分页
- **系统监控**：AI 模型调用监控（Micrometer + Prometheus + Grafana）
- **精选应用**：优质应用可申请被平台推荐展示（申请–审核–推荐工作流）

---

## 二、项目优势

### 技术栈

| 类别 | 技术 |
|------|------|
| **后端框架** | Spring Boot 3.5 + Servlet（Tomcat） + MyBatis-Flex 1.11 |
| **AI 框架** | LangChain4j 1.1（Java 版 LangChain）+ Reactor 响应式流 |
| **AI 模型** | 5 个独立模型实例（代码生成 ×2、流式生成、路由命名、应用命名），兼容 OpenAI API（DeepSeek / 通义千问） |
| **数据库** | MySQL 8.x |
| **缓存** | Redis + Caffeine（本地二级缓存）|
| **对象存储** | 腾讯云 COS |
| **限流** | Redisson 分布式限流（USER / IP / API 三级）|
| **配置中心** | Nacos（动态安全规则，优雅降级）|
| **安全守卫** | 双轨提示词防护（外轨主动重写 + 内轨禁止词/注入/幻觉三层检查）|
| **监控** | Micrometer 自定义指标 + Prometheus + Grafana（4 面板：概览 / Token / 错误 / 模型调用）|
| **RAG** | 本地 ONNX 嵌入模型（BgeSmallZhV15，零费用），语义检索注入 AI 上下文 |
| **截图** | Selenium ChromeDriver 连接池（3 实例）|

### 架构特性

- AI 智能体（Agent）开发 + 工具调用（Tool Calling）
- SSE 流式输出（Spring 异步 Servlet + langchain4j-reactor）
- Pipeline 管线架构（Setup → Flux 生成 → Cleanup 三阶段，可编排 Stage）
- 策略模式封装 3 种代码生成策略（注册表管理，扩展零修改）
- 装饰器链动态富化用户消息（图片上下文、Skills 提示词）
- 上下文生命周期统一管理（ThreadLocal 在异步线程中正确传播与清理）
- 对话记忆管理（Redis ChatMemoryStore，按 appId 隔离，20 条上限）
- 多级缓存架构（Redis + Caffeine 二级缓存 + AI 服务实例缓存）
- 事件驱动架构（应用删除/部署/精选变更事件，松耦合通知）
- 分布式限流 + 分布式锁（Redisson）
- AOP 权限控制（@AuthCheck）
- Nacos 配置中心动态热更新（敏感词规则、注入/幻觉检测）

---

## 三、项目结构

### 功能模块

- **用户模块**：注册、登录、权限管理
- **应用模块**：创建、编辑、删除、部署、精选应用申请与审核
- **AI 生成模块**：Pipeline 管线编排（Validation → Prompt Rewrite → RAG → Monitor → ChatHistory → ContentReview），策略路由，流式输出，工具调用，Vue 项目构建
- **安全守卫**：双轨输入防护（外轨 PromptRewrite + 内轨 CompositeInputGuardrail）+ 输出防护（CompositeOutputGuardrail）
- **RAG 知识库**：精选应用代码语义检索，增量更新（事件驱动），本地 ONNX 嵌入模型
- **对话模块**：对话历史管理（游标分页）、Redis 上下文记忆、按对话轮次记录
- **部署模块**：状态机管理（null → ONLINE → OFFLINE）、自动截图、文件归档、分布式锁
- **图片管理**：用户自主上传图片，AI 生成时自动注入图片上下文（COS URL + 描述）
- **监控模块**：AI 调用监控（请求数、Token 消耗、响应时间、错误率）+ ThreadLocal 上下文传递
- **配置中心**：Nacos 动态管理敏感词规则、注入检测、幻觉防护
- **管理模块**：用户管理、应用管理、精选设置

### 核心业务流程

```
用户输入 → [外轨] PromptRewriteService（主动重写）
                → [内轨] CompositeInputGuardrail（安全检查）
                    → [RAG] 精选代码语义检索（可选）
                        → [装饰器链] 注入图片/Skills 上下文
                            → [策略路由] AI 选择生成类型
                                → [代码生成] HTML / MULTI_FILE / VUE_PROJECT
                                    → [流式输出] SSE 实时推送
                                        → [代码解析] CodeParser
                                            → [代码保存] CodeFileSaver
                                                → [可选] 一键部署 + 自动截图
```

### AI 代码生成管线（Pipeline）

`chatToGenCode()` 委托 `GenPipeline` 分三阶段执行：

```
Phase 1: Setup（同步，按 @Order 排序）
  └─ ValidationStage → PromptRewriteStage → RagSwitchStage → MonitorStage → ChatHistoryStage → ContentReviewStage
  └─ ContextLifecycle 统一设置 ThreadLocal（RagSwitchHolder / MonitorContextHolder）

Phase 2: Flux 生成（响应式）
  └─ MessageDecoratorChain（@Order 排序）
  │   ├─ ImageContextDecorator — 注入已上传图片
  │   └─ SkillContextDecorator — 注入 Skills 提示词
  └─ CodeGenStrategy（策略路由）
      ├─ HtmlCodeGenStrategy → HTML 单文件（Flux<String>）
      ├─ MultiFileCodeGenStrategy → 多文件工程（Flux<String>）
      └─ VueCodeGenStrategy → Vue 项目（TokenStream + 工具调用）

Phase 3: 响应式生命周期
  └─ doOnSubscribe → PipelineContextManager.restore()（异步线程恢复上下文）
  └─ doFinally → PipelineContextManager.clear() + Stage.cleanup()
```

---

## 四、核心功能演示

### 1. HTML 单文件生成

输入简单的页面需求，AI 自动生成完整的 HTML 代码。

![](https://github.com/user-attachments/assets/c80ef8a5-9889-4d84-b2c5-969b0f636dcb/)

### 2. 多文件工程生成

生成包含 HTML、CSS、JS 分离的多文件项目。

![](https://github.com/user-attachments/assets/e7e33964-4f94-4f74-9f54-27306ac9a24b/)

### 3. Vue 项目生成

生成完整的 Vue 3 项目，包含组件化开发。

![](https://github.com/user-attachments/assets/7cd40ec2-57d9-4c23-baef-1ee286ff0763)

### 4. 对话式修改

通过对话方式让 AI 修改已生成的页面。

![](https://github.com/user-attachments/assets/3cb9907a-5eb8-4dc7-b87c-845dc8a691ce)

### 5. 一键部署

将生成的应用部署到云端并获取访问链接。

![](https://github.com/user-attachments/assets/0fc4391a-4e5a-4959-b080-a20006579f88)

---

## 五、技术深度解析

### AI 智能体架构

- **5 个独立 AI 模型**：代码生成（非流式/流式）×2、Vue 推理生成、路由命名、应用命名，各司其职
- **工具调用机制**：基于 LangChain4j 的 Tool Calling（FileWriteTool / FileReadTool / FileModifyTool / FileDeleteTool / FileDirReadTool / ExitTool），Vue 项目 AI 自主管理文件
- **对话记忆管理**：Redis ChatMemoryStore，按 appId 隔离，20 条消息上限
- **AI 服务实例缓存**：Caffeine 缓存（1000 上限，30 分钟过期），每个 appId 独立 MessageWindowChatMemory

### 关键设计模式

| 模式 | 实现 | 用途 |
|------|------|------|
| **Pipeline** | `GenStage` + `GenPipeline` | 将 `chatToGenCode()` 拆分为可编排阶段，按 `@Order` 执行 |
| **策略模式** | `CodeGenStrategy` + `CodeGenStrategyRegistry` | 按 `CodeGenTypeEnum` 自动注册并查找生成策略 |
| **装饰器链** | `MessageDecorator` + `MessageDecoratorChain` | 链式增强用户消息（@Order 排序）|
| **模板模式** | `CodeParserExecutor` + `CodeFileSaverTemplate` | 统一解析/保存流程，子类处理类型差异 |
| **工厂模式** | `AiCodeGeneratorServiceFactory` 等 | 工厂 + Caffeine 缓存的 AI 服务实例管理 |
| **命令模式** | `ContextLifecycle` + `PipelineContextManager` | ThreadLocal 生命周期统一管理 |
| **事件驱动** | `ApplicationEvent` + `@EventListener` | 应用删除/部署/精选变更的松耦合通知 |

### 提示词安全守卫（Nacos 驱动的双轨架构）

**外轨（Rail 1）- `PromptRewriteService`**：检测风险内容时主动重写（替换敏感词、移除注入片段、规范化幻觉触发词），让 AI 正常生成而不是直接中断。

**内轨（Rail 2）- `CompositeInputGuardrail`**：安全兜底，对重写后的提示词进行三层检查：
1. 禁止词列表检查
2. 注入模式正则匹配
3. 幻觉触发词检测

**输出守卫（`CompositeOutputGuardrail`）**：对 AI 生成的内容做独立安全检查。

全部规则由 Nacos 动态下发，`AtomicReference<ImmutableRuleSet>` 无锁并发，Nacos 断连时保留最后已知规则。

### RAG 知识库（本地嵌入模型，零费用）

精选应用代码 → CodeCorpusLoader → CodeSplitter（按类型拆分：HtmlSplitter / VueSplitter / MultiSplitter）→ BgeSmallZhV15 ONNX 嵌入 → InMemoryEmbeddingStore（持久化 JSON）

- **请求级开关**：RagSwitchHolder（ThreadLocal），请求粒度控制是否启用 RAG
- **条件检索**：ConditionalContentRetriever 装饰 EmbeddingStoreContentRetriever
- **增量更新**：事件驱动（AppFeaturedEvent → 加载 / AppUnfeaturedEvent → 移除）
- **自动持久化**：每次变更后保存到 `tmp/rag_store.json`

### 配置中心（Nacos）

- **动态热更新**：敏感词规则、注入模式、幻觉触发词全部通过 Nacos 下发，无需重启
- **优雅降级**：Nacos 不可达时自动使用 `application.yml` 的 fallback 默认值
- **管理员 API**：`/sensitive/admin/rules` 运行时查看/更新规则

### 部署生命周期状态机

```
null ──[deployApp]──────→ ONLINE ──[deployOffline]──→ OFFLINE ──[deployOnline]──→ ONLINE
                                   [removeById]                    [removeById]
                                       ↓                                ↓
                                    删除 (APP)                       删除 (APP)
```

- **先更新 DB + 后操作文件**：防止中间状态数据不一致
- **Redisson 分布式锁**：key `app:deploy:lock:{appId}`
- **短 key 预览**：6 位随机字符，支持 OLFINE 状态拦截（返回 503）
- **回退机制**：部署目录不存在时自动回退到生成目录

### 监控体系（Micrometer + Prometheus + Grafana）

自定义指标：
| 指标名 | 类型 | 说明 |
|--------|------|------|
| `ai_model_requests_total` | Counter | 模型请求计数（标签：user_id, app_id, model_name, status）|
| `ai_model_errors_total` | Counter | 模型错误计数（标签：user_id, app_id, model_name, error_message）|
| `ai_model_tokens_total` | Counter | Token 消耗计数（标签：user_id, app_id, model_name, token_type）|
| `ai_model_response_duration_seconds` | Timer | 模型响应持续时间 |

> **注意**：Spring 异步 Servlet 环境下 ThreadLocal 可能丢失上下文。项目通过 `WebAsyncConfig` 配置异步上下文传播和 `PipelineContextManager.restore()` 在异步线程中恢复上下文。

### 性能优化

- **多级缓存**：Redis（精选应用分页）+ Caffeine（AI 服务实例）+ Caffeine（本地二级）
- **流式输出**：SSE 实时推送，5 分钟异步超时
- **响应式编程**：langchain4j-reactor 实现响应式流处理
- **Selenium 连接池**：3 个预创建 ChromeDriver，借出/归还模式，带健康检查
- **虚拟线程**：I/O 密集型异步任务（截图、文件构建）

### 精选应用工作流

1. 用户申请精选 → `PENDING` 状态
2. 管理员审核（支持批量）→ `APPROVED` / `REJECTED`
3. 审核通过后：priority=99，缓存驱逐，触发 `AppFeaturedEvent`（RAG 增量加载）
4. 管理员批量取消精选：priority=0，触发 `AppUnfeaturedEvent`
5. 精选应用重新修改代码 → 调用 `requestContentReview()` 创建新 PENDING 申请（旧版本继续在线）
6. 兜底任务 `FeaturedAppReconciliationTask`（每 5 分钟）确保审核通过后 priority 正确更新

---

## 六、联系方式

- 作者：呱太
- GitHub：https://github.com/guatai-lock
- 邮箱：2928009776@qq.com

---

⭐️ 如果这个项目对你有帮助，请给个 Star 支持一下！

🚀 关注我，获取更多 AI 开发实战项目！

[前端仓库](https://github.com/liyupi/yu-ai-code-mother/tree/master/yu-ai-code-mother-frontend)
[后端仓库](https://github.com/guatai-lock/yang-ai-code-mother.git)
