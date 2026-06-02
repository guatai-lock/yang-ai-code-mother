# AI 网站前端代码生成平台

> 作者：[呱太](https://github.com/guatai-lock?tab=repositories)

## 一、项目介绍

  基于 **Spring Boot 3 + LangChain4j + WebFlux + Vue 3** 构建的企业级 AI 代码生成平台。用户通过自然语言描述需求，AI自动分析并选择最优策略（HTML 单文件 / 多文件工程 / Vue
  项目），流式生成完整可运行的网站代码，支持实时预览、对话式修改、一键部署上线。
![](https://github.com/user-attachments/assets/c80ef8a5-9889-4d84-b2c5-969b0f636dcb/)

<img width="3555" height="2000" alt="image_967088347285491" src="https://github.com/user-attachments/assets/f84bfec8-1310-46f5-b42c-1122ef41cb39" />

### 4 大核心能力

#### 1）智能代码生成

用户输入需求描述，AI 自动分析并选择合适的生成策略，通过工具调用生成代码文件，采用流式输出让用户实时看到 AI 的执行过程。

- **HTML 单文件生成**：快速生成完整的 HTML 页面
- **多文件工程生成**：生成包含 HTML、CSS、JS 的多文件项目
- **Vue 项目生成**：生成完整的 Vue 3 前端项目

![](https://github.com/user-attachments/assets/89f32af6-65ea-4548-981c-9a74440c4020/)

#### 2）可视化编辑

生成的应用将实时展示，可以进入编辑模式，自由选择网页元素并且和 AI 对话来快速修改页面，直到满意为止。

- **实时预览**：生成结果即时可见
- **对话式修改**：通过自然语言指令调整页面
- **智能理解**：AI 理解用户意图并精准修改
- **图片上传**：支持用户自主上传图片，AI 在生成网页时优先使用上传的图片资源

![](https://github.com/user-attachments/assets/a3023685-12c1-447a-ae68-328d64555f7d/)

#### 3）一键部署分享

可以将生成的应用一键部署到云端并自动截取封面图，获得可访问的地址进行分享，同时支持完整项目源码下载。

- **一键部署**：自动生成部署标识和访问链接
- **自动截图**：AI 生成应用后自动截取封面图（Selenium ChromeDriver 连接池，线程池优化）
- **源码下载**：支持完整项目打包下载
- **部署状态管理**：支持部署之后取消部署与恢复部署（ONLINE ↔ OFFLINE 状态切换）

![](https://github.com/user-attachments/assets/87b4d333-3557-4b4f-95c3-81c90c37732f/)
<img width="1895" height="910" alt="0a460d58e98f48fced4ae580ebb3cfb0" src="https://github.com/user-attachments/assets/3db7153b-cd7d-45b3-bce1-9b80ffba71d2" />
<img width="1868" height="929" alt="40233c341107535729c07e7c5bb3c3a5" src="https://github.com/user-attachments/assets/5b47252e-9c18-402a-994c-a305371a9ebd" />
<img width="1886" height="814" alt="184af0609b515eb7cfa1e0f8954eb007" src="https://github.com/user-attachments/assets/bc55d31f-1dc8-4403-ae81-7b1c06d606c4" />

查看精选案例：

![](https://github.com/user-attachments/assets/13679a96-83b0-4119-8ce0-b5d27c5f63f5/)

#### 4）企业级管理

提供用户管理、应用管理、系统监控、业务指标监控等后台功能，管理员可以设置精选应用、监控 AI 调用情况和系统性能。

- **用户管理**：用户注册、登录、权限控制
- **应用管理**：应用创建、编辑、删除、精选设置
- **对话历史**：按应用查看完整对话记录，支持导出为 Markdown 文件
- **系统监控**：AI 模型调用监控（Micrometer + Prometheus + Grafana）、性能指标监控
- **精选应用**：优质应用可申请被平台推荐展示（申请–审核–推荐工作流）

<img width="1909" height="885" alt="15921cbfbbdb2b28f68cec13715162bb" src="https://github.com/user-attachments/assets/46e0c3ad-89e4-4b25-8fe8-1f99cb4f966d" />

## 二、项目优势

### 技术栈

Spring Boot 3 + LangChain4j + MyBatisFlex + Redission + Selenium + Nacos + Prometheus + Grafana

#### 核心技术

- **后端框架**：Spring Boot 3.x、MyBatis-Flex 1.11
- **AI 框架**：LangChain4j 1.1（Java 版 LangChain）
- **AI 模型**：OpenAI API 兼容模型（DeepSeek / 通义千问）
- **数据库**：MySQL 8.x
- **缓存**：Redis、Caffeine（本地缓存）
- **响应式编程**：Spring WebFlux + Reactor
- **对象存储**：腾讯云 COS
- **限流**：Redisson 分布式限流
- **配置中心**：Nacos（动态管理安全规则，支持优雅降级）
- **安全守卫**：双轨提示词防护（外轨主动重写 + 内轨安全兜底）
- **监控**：Micrometer + Prometheus + Grafana

#### 架构特性

- AI 智能体（Agent）开发
- AI 工具调用（Tool Calling）
- 流式输出（SSE）
- 对话记忆管理（基于 Token 数限制，按 appId 隔离）
- 多级缓存架构（Redis + Caffeine）
- 分布式限流（Redisson）
- 权限控制（AOP）
- 企业级监控体系（Micrometer 自定义指标）
- Nacos 配置中心动态热更新
- 双轨提示词安全防护

## 三、项目结构

### 功能模块

- **用户模块**：注册、登录、权限管理
- **应用模块**：创建、编辑、删除、部署、精选应用申请与审核
- **AI 生成模块**：智能代码生成、流式输出、提示词重写与安全守卫
- **对话模块**：对话历史管理、上下文记忆、Markdown 导出、按对话轮次记录
- **部署模块**：一键部署/下线/恢复、自动截图（Selenium 连接池 + 线程池）
- **图片管理**：用户自主上传图片，AI 生成时自动注入图片上下文
- **监控模块**：AI 调用监控（请求数、Token 消耗、响应时间、错误率）
- **配置中心**：Nacos 动态管理敏感词规则、注入检测、幻觉防护
- **管理模块**：用户管理、应用管理、精选设置

### 核心业务流程

1. 用户输入需求描述
2. 提示词经双轨安全守卫（外轨重写 → 内轨拦截检查）
3. AI 智能分析并选择代码生成策略
4. 根据策略调用相应的 AI 工具和模板
5. 流式输出生成结果
6. 保存代码到文件系统
7. 可选：部署应用并生成访问链接

### 架构设计

采用分层架构设计：

- **表现层**：RESTful API + SSE 流式接口
- **业务层**：AI 服务工厂、代码生成器、对话管理、安全守卫
- **数据层**：MySQL + Redis + 文件系统 + 腾讯云 COS
- **基础设施**：AI 模型（4 个独立模型实例）、对象存储、缓存服务
- **配置与安全**：Nacos 配置中心（动态热更新安全规则，优雅降级）

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

## 五、技术深度解析

### AI 智能体架构

- **工具调用机制**：基于 LangChain4j 实现 AI 工具智能调用（FileWrite/Read/Modify/Delete/DirRead/Exit）
- **对话记忆管理**：支持按应用隔离的多租户对话记忆，基于 Token 数限制自动淘汰旧记忆
- **双轨输入防护**：
  - **外轨（PromptRewriteService）**：检测风险内容时主动重写提示词（替换敏感词、移除注入片段、规范化易引发幻觉的表述），让大模型正常生成而不是直接中断
  - **内轨（CompositeInputGuardrail）**：安全兜底，对重写后的提示词进行禁止词、注入模式、幻觉触发词三重检查
- **输出防护（CompositeOutputGuardrail）**：对 AI 生成的内容做独立安全检查

### 配置中心（Nacos）

- **动态热更新**：敏感词规则、注入模式、幻觉触发词全部通过 Nacos 配置中心下发，无需重启
- **优雅降级**：Nacos 不可达时自动使用 `application.yml` 中的 fallback 默认值；运行时断连保留最后已知规则
- **无锁并发**：`AtomicReference<ImmutableRuleSet>` 实现规则集的原子替换和线程安全读取

### 性能优化

- **多级缓存**：Redis + Caffeine 二级缓存架构
- **流式输出**：SSE 实时推送，提升用户体验
- **响应式编程**：WebFlux 提升并发处理能力
- **Selenium 连接池**：预创建的 ChromeDriver 连接池（3 实例），借出/归还模式，带健康检查

### 企业级特性

- **权限控制**：基于 AOP 的细粒度权限验证（`@AuthCheck`）
- **分布式限流**：Redisson 实现接口限流（`@RateLimit`，支持 USER/IP/API 级别）
- **系统监控**：Micrometer 自定义指标（AI 请求数、Token 消耗、响应时间、错误率）+ Prometheus + Grafana
- **分布式锁**：Redisson 保障精选应用申请、部署状态变更的并发安全
- **缓存一致性**：精选应用状态变更时自动驱逐缓存，保证 DB 与缓存一致性

## 六、联系方式

- 作者：呱太
- 邮箱：2928009776@qq.com

---

⭐️ 如果这个项目对你有帮助，请给个 Star 支持一下！

🚀 关注我，获取更多 AI 开发实战项目！

[前端参考](https://github.com/liyupi/yu-ai-code-mother/tree/master/yu-ai-code-mother-frontend)
[后端仓库](https://github.com/guatai-lock/yang-ai-code-mother.git)
