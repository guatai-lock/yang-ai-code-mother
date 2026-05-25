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

![](https://github.com/user-attachments/assets/a3023685-12c1-447a-ae68-328d64555f7d/)

#### 3）一键部署分享

可以将生成的应用一键部署到云端并自动截取封面图，获得可访问的地址进行分享，同时支持完整项目源码下载。

- **一键部署**：自动生成部署标识和访问链接
- **自动截图**：AI 生成应用后自动截取封面图
- **源码下载**：支持完整项目打包下载
- **部署状态管理**：支持部署之后取消部署与恢复部署

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
- **对话历史**：按应用查看完整对话记录
- **系统监控**：AI 模型调用监控、性能指标监控

![](https://github.com/user-attachments/assets/75fa6e87-db8b-44d9-be72-7db6aa363e88/)

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
