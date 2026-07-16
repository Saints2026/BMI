# BMI 项目长期记忆

## 技术栈
- JDK 25.0.2 + JavaFX SDK 24.0.1（外部路径 D:/javafx-sdk-24.0.1/lib）
- 无 Maven/Gradle，手动 jar 放 lib/
- JDBC + SQLite（首选）/ MySQL，无 ORM
- AI 调用用原生 HttpURLConnection，手工拼接/解析 JSON

## 项目结构关键点
- 启动类：com.bmi.view.BmiApplication
- AI 架构：AiHealthClient（client 层）→ AiController（controller 层），AiService 已删除
- BodyRecord 统一使用 com.bmi.model.BodyRecord（完整实体），com.bmi.model.ai.BodyRecord 为废弃 DTO
- 密钥从 ai-key.properties 读取（api.key / api.url / api.model），禁止硬编码

## 编译运行
- 编译需 --module-path 指向 JavaFX SDK，--add-modules javafx.controls,javafx.fxml
- 运行需 -cp 包含 bin + lib/sqlite-jdbc-3.46.1.3.jar
- I18n.java 已增加 src/ 目录回退路径，不再需要手动复制 .properties 文件
- 测试编译需 -cp 包含 junit-jupiter-api + platform-commons + apiguardian + opentest4y

## 国际化
- I18n.load() 使用 InputStreamReader(UTF-8) 显式编码 + classpath 优先、src/ 回退
- 默认语言中文（AppConfig.lang=ZH，SettingController.getLangDefault()="zh"）
- BmiApplication.start() 会从 SettingController 读取持久化语言偏好初始化 AppConfig
- 语言切换通过 AppConfig.setLang() → LangChangeListener.onLangChange() → refreshTexts()

## AI 模块
- AiHealthClient: HttpURLConnection + 手工 JSON，缓存基于数据内容（bmi|bodyFat|measureTime）
- 4 类异常提示：断网/超时/空参数/服务器报错，各有中文降级文案
- 接口调用日志：请求URL、请求参数、AI返回（System.out.println）
- extractContent 正确处理 JSON 转义序列（\n→换行符）
- TestAiService: 5场景全覆盖测试（正常/缓存/空输入/断网/服务器报错）
- AiAnalysisView 对接 RecordController 获取真实历史数据，空数据时提示用户先录入

## 数据库
- lib/sqlite-jdbc-3.46.1.3.jar 已添加，DbUtil 显式 Class.forName 注册驱动
- DbUtil 首次连接自动建表（user + body_record + 3索引），幂等 CREATE IF NOT EXISTS
- SQLite JDBC getTimestamp() 要求完整 datetime 格式（如 2026-07-16 10:00:00），不接受纯日期

## 已知限制
- JDK 25 + JavaFX 24 有 native access 警告，不影响功能
- 登录验证码已临时跳过（LoginView），正式版需恢复
- BmiApplication 中 API Key 仍为硬编码（应改为从 ai-key.properties 读取）
