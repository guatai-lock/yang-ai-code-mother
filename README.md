# AI 网站前端代码生成平台

> 作者：[呱太](https://github.com/guatai-lock?tab=repositories)

## 一、项目介绍

本项目基于 Spring Boot 3 + LangChain4j + MyBatisFlex +  Redission + Selenium + 阿里云ARMS + Prometheus + Grafana开发对标大厂的 **企业级 AI 代码生成平台**！
![](<img width="1876" height="849" alt="Image" src="https://github.com/user-attachments/assets/c80ef8a5-9889-4d84-b2c5-969b0f636dcb" />)

<img width="3555" height="2000" alt="image_967088347285491" src="https://github.com/user-attachments/assets/f84bfec8-1310-46f5-b42c-1122ef41cb39" />

![](file://C:\Users\30019713\AppData\Roaming\marktext\images\2026-05-22-21-18-18-QQ_1779455884934.png?msec=1779455898879)

### 4 大核心能力

#### 1）智能代码生成

用户输入需求描述，AI 自动分析并选择合适的生成策略，通过工具调用生成代码文件，采用流式输出让用户实时看到 AI 的执行过程。

- **HTML 单文件生成**：快速生成完整的 HTML 页面
- **多文件工程生成**：生成包含 HTML、CSS、JS 的多文件项目
- **Vue 项目生成**：生成完整的 Vue 3 前端项目

![](file://C:\Users\30019713\AppData\Roaming\marktext\images\2026-05-22-21-19-11-QQ_1779455941897.png?msec=1779455951474)

#### 2）可视化编辑

生成的应用将实时展示，可以进入编辑模式，自由选择网页元素并且和 AI 对话来快速修改页面，直到满意为止。

- **实时预览**：生成结果即时可见
- **对话式修改**：通过自然语言指令调整页面
- **智能理解**：AI 理解用户意图并精准修改

![](file://C:\Users\30019713\AppData\Roaming\marktext\images\2026-05-22-21-19-55-QQ_1779455991037.png?msec=1779455995859)

#### 3）一键部署分享

可以将生成的应用一键部署到云端并自动截取封面图，获得可访问的地址进行分享，同时支持完整项目源码下载。

- **一键部署**：自动生成部署标识和访问链接
- **自动截图**：AI 生成应用后自动截取封面图
- **源码下载**：支持完整项目打包下载

![](file://C:\Users\30019713\AppData\Roaming\marktext\images\2026-05-22-21-21-10-QQ_1779456064140.png?msec=1779456070235)

查看精选案例：

![](file://C:\Users\30019713\AppData\Roaming\marktext\images\2026-05-22-21-23-23-QQ_1779456200516.png?msec=1779456203417)

#### 4）企业级管理

提供用户管理、应用管理、系统监控、业务指标监控等后台功能，管理员可以设置精选应用、监控 AI 调用情况和系统性能。

- **用户管理**：用户注册、登录、权限控制
- **应用管理**：应用创建、编辑、删除、精选设置
- **对话历史**：按应用查看完整对话记录
- **系统监控**：AI 模型调用监控、性能指标监控

![](file://C:\Users\30019713\AppData\Roaming\marktext\images\2026-05-22-21-25-41-QQ_1779456336178.png?msec=1779456341041)

## 二、项目优势

### 技术栈

Spring Boot 3 + LangChain4j + MyBatisFlex +  Redission + Selenium + 阿里云ARMS + Prometheus + Grafana 后续会补充架构图相关内容

#### 核心技术

- **后端框架**：Spring Boot 3.x、MyBatis-Flex
- **AI 框架**：LangChain4j（Java 版 LangChain）
- **AI 模型**：OpenAI API 兼容模型、通义千问等
- **数据库**：MySQL 8.x
- **缓存**：Redis、Caffeine（本地缓存）
- **响应式编程**：Spring WebFlux + Reactor
- **对象存储**：腾讯云 COS
- **限流**：Redisson 分布式限流
- **监控**：Micrometer + Prometheus + Grafana

#### 架构特性

- AI 智能体（Agent）开发
- AI 工具调用（Tool Calling）
- 流式输出（SSE）
- 对话记忆管理
- 多级缓存架构
- 分布式限流
- 权限控制（AOP）
- 企业级监控体系

## 三、项目结构

### 功能模块

后续会补充模块图

- **用户模块**：注册、登录、权限管理
- **应用模块**：创建、编辑、删除、部署应用
- **AI 生成模块**：智能代码生成、流式输出
- **对话模块**：对话历史管理、上下文记忆
- **部署模块**：一键部署、自动截图
- **监控模块**：AI 调用监控、性能指标
- **管理模块**：用户管理、应用管理、精选设置

### 核心业务流程

后续会补充业务流程图

1. 用户输入需求描述
2. AI 智能分析并选择代码生成策略
3. 根据策略调用相应的 AI 工具和模板
4. 流式输出生成结果
5. 保存代码到文件系统
6. 可选：部署应用并生成访问链接

### 架构设计

后续会补充架构设计图

采用分层架构设计：

- **表现层**：RESTful API + SSE 流式接口
- **业务层**：AI 服务工厂、代码生成器、对话管理
- **数据层**：MySQL + Redis + 文件系统
- **基础设施**：AI 模型、对象存储、缓存服务

## 四、核心功能演示

### 1. HTML 单文件生成

输入简单的页面需求，AI 自动生成完整的 HTML 代码。

![](file://C:\Users\30019713\AppData\Roaming\marktext\images\2026-05-22-21-39-28-QQ_1779457164877.png?msec=1779457168137)

### 2. 多文件工程生成

生成包含 HTML、CSS、JS 分离的多文件项目。

![](file://C:\Users\30019713\AppData\Roaming\marktext\images\2026-05-22-21-43-46-QQ_1779457422478.png?msec=1779457426519)

### 3. Vue 项目生成

生成完整的 Vue 3 项目，包含组件化开发。

![](file://C:\Users\30019713\AppData\Roaming\marktext\images\2026-05-22-21-44-31-QQ_1779457468020.png?msec=1779457471983)

### 4. 对话式修改

通过对话方式让 AI 修改已生成的页面。

![](file://C:\Users\30019713\AppData\Roaming\marktext\images\2026-05-22-21-47-20-QQ_1779457636100.png?msec=1779457640097)

### 5. 一键部署

将生成的应用部署到云端并获取访问链接。

![](file://C:\Users\30019713\AppData\Roaming\marktext\images\2026-05-22-21-47-36-QQ_1779457653523.png?msec=1779457656423)

## 五、技术深度解析

### AI 智能体架构

- **工具调用机制**：基于 LangChain4j 实现 AI 工具智能调用
- **对话记忆管理**：支持按应用隔离的多租户对话记忆
- **输入输出护轨**：保障 AI 输出质量和安全性

### 性能优化

- **多级缓存**：Redis + Caffeine 二级缓存架构
- **流式输出**：SSE 实时推送，提升用户体验
- **响应式编程**：WebFlux 提升并发处理能力

### 企业级特性

- **权限控制**：基于 AOP 的细粒度权限验证
- **分布式限流**：Redisson 实现接口限流
- **系统监控**：Micrometer + Grafana 全方位监控
- **日志追踪**：完整的日志记录和异常处理

## 六、联系方式

- 作者：呱太
- 邮箱：2928009776@qq.com

---

⭐️ 如果这个项目对你有帮助，请给个 Star 支持一下！

🚀 关注我，获取更多 AI 开发实战项目！

[前端参考]([yu-ai-code-mother/yu-ai-code-mother-frontend at master · liyupi/yu-ai-code-mother](https://github.com/liyupi/yu-ai-code-mother/tree/master/yu-ai-code-mother-frontend))
