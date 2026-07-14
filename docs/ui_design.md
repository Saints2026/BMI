# BMI 体质评估与预测系统 · UI 设计文档（SDD-UI）

> 本文件是 `docs/plan.md` 的视图层下游细化设计，必须严格遵循 `CODEBUDDY.md`（项目宪章）。
> 设计范围完整覆盖：登录注册（FR-01）、身高体重录入（FR-02）、BMI 计算（FR-03）、体脂预测（FR-04）、历史记录（FR-05）、动态折线图（FR-06）、AI 健康建议（FR-07），并扩展双语界面、体型照片、报告导出、系统设置等页面。
> 技术栈边界：**Java 8+ · JavaFX（`javafx.scene.chart.LineChart`）· JDBC + SQLite/MySQL · 原生 HttpURLConnection**；不引入 Web 前端 / Spring / 第三方图表库。

---

## 1. 文档信息

| 项 | 内容 |
|----|------|
| 文档标题 | BMI 体质评估与预测系统 UI 设计文档 |
| 版本 | v1.0 |
| 日期 | 2026-07-14 |
| 上游文档 | `CODEBUDDY.md`、`docs/plan.md`、`docs/db_design.md`、`docs/ai_design.md`、`docs/spec.md` |
| 编写角色 | UI/架构协同（依据宪章视图层规范） |
| 适用范围 | 全部 `com.bmi.view` 包下的界面与交互；与 `com.bmi.controller` 的对接契约 |

---

# 一、全局 UI 通用规范

## 1. 全局中英双语切换实现规则、文字绑定规范

### 1.1 实现规则

- **语言枚举**：定义 `enum Lang { ZH, EN }`，当前语言由全局配置持有（单例 `AppConfig.getInstance().setLang(Lang)`）。
- **文案来源**：每个界面字符串通过 key 绑定，运行时按当前语言取值，**禁止在代码中硬编码中文/英文文案**。
  - 资源文件：`src/com/bmi/view/i18n/ui_zh.properties`、`ui_en.properties`（随 jar 走 classpath，不入库）。
  - 读取工具类：`I18n.t(String key)` —— 返回当前语言下的值；缺失 key 时回退中文并打 WARN 日志。
- **切换即时生效**：语言下拉框（`ComboBox<Lang>`）变更后：
  1. `AppConfig.setLang(...)` 持久化到 `app-config.properties`（`ui.lang.default=zh|en`，**新增配置文件，需补入 gitignore**，呼应宪章第 4/7 节配置外置原则）；
  2. 通知所有已打开的 `XxxView` 调用 `applyLang()` 重新 `setText(I18n.t(key))` 刷新文案（观察者模式或简单遍历 `Stage.getScene().getRoot()` 子节点）。
- **默认语言**：首启读 `app-config.properties` 的 `ui.lang.default`；文件缺失默认 `zh`。

### 1.2 文字绑定规范（命名与示例）

| 规则 | 正例 | 反例 |
|------|------|------|
| key 用「页面.控件.语义」小写下划线 | `login.username.placeholder` | `用户名` / `userNameLabel` |
| 同一语义两语言 key 必须一致，仅值不同 | `ui_zh: login.title=登录` / `ui_en: login.title=Sign In` | 两文件 key 不一致 |
| 动态值用 `{0}{1}` 占位 | `record.saved=已保存 {0} 条记录` | 字符串拼接 `已保存 + n` |

```properties
# ui_zh.properties（节选）
app.title=BMI 体质评估与预测系统
login.title=登录
login.username=用户名
login.password=密码
login.verifycode=验证码
login.submit=登录
login.register=注册
login.lang=语言
common.save=保存
common.delete=删除
common.cancel=取消
common.refresh=刷新
common.confirm=确认
common.emptyHint=暂无数据
chart.metric.bmi=BMI 趋势
chart.metric.weight=体重趋势
chart.metric.bodyfat=体脂率趋势
chart.noData=至少需 2 条记录才能显示趋势
chart.empty=暂无记录，无法绘制图表
ai.genAdvice=获取 AI 建议
ai.adviceText=健康建议
```

```properties
# ui_en.properties（节选）
app.title=BMI Health Assessment & Prediction System
login.title=Sign In
login.username=Username
login.password=Password
login.verifycode=Verification Code
login.submit=Sign In
login.register=Register
login.lang=Language
common.save=Save
common.delete=Delete
common.cancel=Cancel
common.refresh=Refresh
common.confirm=Confirm
common.emptyHint=No data
chart.metric.bmi=BMI Trend
chart.metric.weight=Weight Trend
chart.metric.bodyfat=Body Fat Trend
chart.noData=At least 2 records are required to show a trend
chart.empty=No records yet, cannot draw chart
ai.genAdvice=Get AI Advice
ai.adviceText=Health Advice
```

---

## 2. 页面通用控件标准（统一样式）

所有控件样式集中在 `styles.css`（JavaFX CSS），由 `AppConfig` 加载主题后注入 `Scene.getStylesheets()`。以下为统一样式契约：

| 控件类型 | 样式类 / 规范 | 统一表现 |
|----------|---------------|----------|
| 输入框 `TextField`/`PasswordField` | `.bmi-field` | 圆角 4px、高 32px、聚焦蓝色描边（`-fx-focus-color`），非法态加 `.bmi-field-error`（红边+红字提示）；占位符用 `promptText` 绑 `I18n` |
| 按钮 `Button` | `.bmi-btn` / `.bmi-btn-primary` | 主按钮填充主题色、白字；次按钮描边；高 34px、圆角 4px、hover 加深 |
| 折叠面板 `TitledPane`（`Accordion`） | `.bmi-accordion` | 标题加粗、展开/收起动画；同一 `Accordion` 默认仅展开一个面板 |
| 弹窗 `Alert`/`Dialog` | `.bmi-dialog` | 统一标题栏、确定/取消双按钮、`I18n` 文案；错误弹窗用 `Alert.AlertType.ERROR`、警告用 `WARNING`、信息用 `INFORMATION` |
| 下拉框 `ComboBox` | `.bmi-combo` | 与输入框等高的圆角；选项文案走 `I18n`（如指标切换、语言、历史记录选择） |
| 复选框 `CheckBox` | `.bmi-check` | 左侧方框、标签右置、选中态主题色；多选组用 `VBox`/`FlowPane` 横纵排列 |
| 图表 `LineChart` | `.bmi-chart` | 见「三、图表专项规范」；统一坐标轴字体、网格线淡灰、数据点圆形标记、主题色折线 |

> 主题切换（系统设置页）通过覆盖 `.root` 的 CSS 变量（`-bmi-primary`、`-bmi-bg`、`-bmi-fg`）实现，不重建控件。

---

## 3. 数值输入校验规则

| 规则 | 说明 | 提示文案（I18n key） |
|------|------|----------------------|
| 禁止负数 | 身高/体重/围度/血压/心率/年龄等数值控件，解析为负立即拒绝 | `validate.negative=数值不能为负` |
| 禁止字母/非数字 | `TextField` 用 `TextFormatter` 限制仅数字与小数点；非数字拒绝 | `validate.nan=请输入有效数字` |
| 必填非空 | 标注 `*` 的必填项为空拒绝提交 | `validate.required=该项为必填` |
| 选填允许空白 | 身体围度、健康指标、既往疾病（选「无」时）等选填项允许为空，空值不入库（或存 NULL） | — |
| 区间校验 | 身高 `[50,250]`cm、体重 `[10,300]`kg、年龄 `[1,120]`、血压/心率合理区间，越界拒绝 | `validate.height=身高应在 50–250cm 之间`、`validate.weight=体重应在 10–300kg 之间`、`validate.age=年龄应在 1–120 之间` |

> 校验在 **view 层** 用 `TextFormatter` + 提交前集中 `validate()` 完成；越界/非法在输入框旁就地红字提示（呼应 AC-02/AC-03/AC-04），不弹窗打断。

---

## 4. 全局异常弹窗规范

| 异常场景 | 触发位置 | 弹窗类型 | 展示文案（直接展示，不暴露技术栈） | 对齐文档 |
|----------|----------|----------|----------------------------------|----------|
| 数据库读取失败 | `RecordController`/`ChartController` 调 DAO 抛 `SQLException` | `ERROR` | 「数据读取失败，请检查数据库或稍后重试」 | db_design §6.3（配置外置） |
| AI 接口超时 | `AiService.send()` 捕获 `SocketTimeoutException` | `WARNING` | 「AI 建议请求超时，请稍后重试」 | ai_design §5.1② / AC-07 |
| AI 断网 | 捕获 `ConnectException`/`UnknownHostException` | `WARNING` | 「暂时无法获取 AI 建议，请检查网络或稍后再试」 | ai_design §5.1① / AC-07 |
| AI 空参数 | `AiRequest` 校验失败 | `WARNING` | 「当前数据不完整，暂无法生成 AI 建议，请先完成一次身高体重录入」 | ai_design §5.1③ |
| AI 服务器报错 | HTTP 5xx / content 缺失 | `WARNING` | 「AI 服务暂时不可用，请稍后再试」 | ai_design §5.1④ / AC-07 |
| AI 密钥缺失 | `AiConfigException` | `WARNING` | 「AI 服务未配置，请联系管理员」 | ai_design §5.1（附）/ AC-07 |
| 表单填写错误 | view 层 `validate()` 失败 | 就地红字（非弹窗） | 各 `validate.*` 文案 | AC-01~AC-04 |

> 职责边界（呼应 ai_design §5.3）：AI 四类异常已由 `AiService` 在内部转换为降级文案（`AiHealthResult.message`），`AiController.getAdvice` 直接把字符串交视图；视图**只展示**，不做任何异常判断。数据库异常由 controller 捕获后转为上述中文弹窗。

---

## 5. 本地图片存储规则

- **仅记录文件路径**：体型照片不写入数据库、不做 Base64、不做二进制入库；只在对应记录上保存**本地绝对/相对路径字符串**（建议字段 `photo_path`）。
- **无图片预览**：UI 不渲染缩略图或图像控件；照片管理页仅以「文件名 + 路径文本 + 打开文件夹」按钮呈现，点击用系统默认程序打开（不内嵌 `ImageView` 预览）。
- **不存入数据库**：照片文件本身保存在本地 `user.home/bmi/photos/` 目录（或由用户在设置页指定根目录），DB 仅存路径；该目录与 `db-config.properties`/`ai-key.properties` 同样视为本地数据，不提交仓库。
- **绑定规则**：上传时由用户选择「关联哪条体检记录」，将照片文件复制到 photos 目录并重命名为 `{recordId}_{timestamp}.jpg`，把路径写入该记录的 `photo_path`（需 `body_record` 增加 `photo_path TEXT` 列 —— 见四.2 扩展标记）。
- **安全**：路径存库前做白名单校验（仅允许本地盘符/用户目录，禁止 `http`/`ftp` 等远程协议前缀）。

---

# 二、分页面详细设计

> 公共容器 `MainView`（系统首页/侧边导航）为其余功能页的承载壳，所有功能页均为其内嵌 `BorderPane` 中心区或独立 `Scene`。

## 1. 登录 & 注册页面（`LoginView`）

### 1.1 界面布局

```
┌───────────────────────────────────────────────────────────┐
│  LoginView（左右分栏 HBox）                                │
│ ┌──────────────────┐  ┌────────────────────────────────┐ │
│ │  左：登录区        │  │  右：注册折叠面板（Accordion）     │ │
│ │  [双语标题]       │  │  ▸ 账号                          │ │
│ │  用户名 [____]    │  │  ▸ 基础体质（必填）               │ │
│ │  密码   [____]    │  │  ▸ 身体围度（选填）               │ │
│ │  验证码 [__] [图] │  │  ▸ 健康指标（选填）               │ │
│ │  [登录]  [语言▾] │  │  ▸ 既往疾病（多选）               │ │
│ │                  │  │  [注册并提交]                    │ │
│ └──────────────────┘  └────────────────────────────────┘ │
└───────────────────────────────────────────────────────────┘
```

### 1.2 控件清单

| 区域 | 控件 | key / 绑定 |
|------|------|------------|
| 左登录区 | 标题 `Label` | `login.title` |
| | 用户名 `TextField` | `login.username` |
| | 密码 `PasswordField` | `login.password` |
| | 验证码 `TextField` + 本地验证码图 `Label`（显示 4 位数字） | `login.verifycode` |
| | 登录 `Button` | `login.submit` |
| | 语言 `ComboBox<Lang>` | `login.lang` |
| 右注册面板 | `Accordion`（5 个 `TitledPane`：账号 / 基础体质 / 身体围度 / 健康指标 / 既往疾病） | — |
| | 账号：用户名、密码、确认密码 `TextField`/`PasswordField` | `register.*` |
| | 基础体质：身高、体重、年龄、性别 `ComboBox`(男/女)、测量时间 `DatePicker` | `register.basic.*` |
| | 身体围度（选填）：腰围、臀围、腕围 `TextField` | `register.circum.*` |
| | 健康指标（选填）：收缩压、舒张压、心率 `TextField` | `register.vital.*` |
| | 既往疾病（多选）：`CheckBox` 组（高血压/糖尿病/心脏病/高血脂/脂肪肝/无） | `register.disease.*` |
| | 注册并提交 `Button` | `register.submit` |

### 1.3 交互功能

- **本地 4 位验证码**：进入页面时由 `LoginView` 本地 `Random` 生成 4 位数字串（**无需联网**），以 `Label` 文本/简单干扰线展示；`[刷新]` 按钮重新生成；登录/注册提交时比对输入，不一致提示「验证码错误」（`validate.code`）。
- **注册自动计算初始 BMI**：填写身高/体重后（或提交时），调用 `BmiCalculator.calcBmi(height, weight)` 实时算出初始 BMI 与分级，显示在基础体质面板底部（仅展示，落库按四.2 扩展字段处理）。
- **重复账号拦截**：注册提交先调 `UserController.register(username, password, confirm)` → 内部 `UserDao.existsUsername` 判重；已存在提示「该用户名已被注册」（`AC-01`）。
- **密码规则校验**（AC-01）：长度 8–20、至少含两种字符类型、不与用户名相同、两次输入一致；任一不满足在对应框旁红字提示具体项。
- **注册成功跳转登录**：注册落库成功后清空表单、切回登录区并提示「注册成功，请登录」。
- **语言切换**：左区 `ComboBox<Lang>` 实时切换整页 `I18n` 文案。

---

## 2. 系统首页（`MainView`，全局侧边导航共用）

### 2.1 界面布局

```
┌──────────┬───────────────────────────────────────────────┐
│ 左侧固定  │  顶部栏：用户名 | 语言▾ | 配色▾ | 退出登录    │
│ 侧边导航  ├───────────────────────────────────────────────┤
│ [首页]    │  数据卡片区（GridPane）：最新 BMI/体重/体脂率    │
│ [录入]    │  迷你趋势图（LineChart 小图，近 5 次）          │
│ [历史]    │  底部快捷按钮：[录入数据][查看图表][获取建议]    │
│ [图表]    │                                               │
│ [AI分析]  │                                               │
│ [照片]    │                                               │
│ [报告]    │                                               │
│ [设置]    │                                               │
└──────────┴───────────────────────────────────────────────┘
```

### 2.2 控件清单

| 区域 | 控件 | 绑定 |
|------|------|------|
| 侧边导航 | `VBox` + `Button`（首页/录入/历史/图表/AI分析/照片/报告/设置） | `nav.*` |
| 顶部栏 | 用户名 `Label`、语言 `ComboBox<Lang>`、配色 `ComboBox<Theme>`、退出登录 `Button` | `topbar.*` |
| 数据卡片 | 3 张 `TitledPane`/卡片：最新 BMI（含分级色块）、体重、体脂率 | `card.*` |
| 迷你趋势 | 小号 `LineChart`（指标可切 BMI/体重/体脂，复用 `ChartView`） | `chart.*` |
| 底部快捷 | 3 个 `Button` | `home.quick.*` |

### 2.3 交互功能

- **侧边导航**：点击切换中心区 `Scene`/`BorderPane` 内容（调用对应 `XxxView` 构建方法，如 `MainView.switchToInput()`）；当前页按钮高亮（`.bmi-nav-active`）。
- **顶部栏**：用户名显示当前会话 `User.getUsername()`；语言/配色切换即时生效（见一.1、一.2）；退出登录清会话 → 返回 `LoginView`。
- **数据卡片**：进入首页时由 `RecordController.queryRecords(userId, null, null)` 取最新一条，展示 BMI/体重/体脂率；分级用色块（偏瘦蓝/正常绿/超重橙/肥胖红，呼应 FR-03 中国标准）。
- **迷你趋势图**：调 `ChartController.getSeries(userId, metric)` 取近 5 条绘小图，指标下拉切换。
- **底部快捷按钮**：分别跳录入页 / 图表页 / AI 分析页。

---

## 3. 体检数据录入页面（`InputView`）

### 3.1 界面布局

```
InputView（Accordion 4 组折叠面板 + 底部结果区）
 ▸ 基础必填数据：身高* 体重* 年龄* 性别* 测量时间(DatePicker)
 ▸ 身体围度（选填）：腰围 臀围 腕围
 ▸ 健康指标（选填）：收缩压 舒张压 心率
 ▸ 疾病勾选（选填）：高血压/糖尿病/心脏病/高血脂/脂肪肝/无
 ─────────────────────────────
 [实时 BMI：22.9（正常）  体脂率：18.3%]
 [保存本条记录]  [加载历史旧记录▾]  [修改选中记录]
```

### 3.2 控件清单

| 面板 | 控件 | 必填 | key |
|------|------|------|-----|
| 基础必填 | 身高/体重/年龄 `TextField`（数字格式化）、性别 `ComboBox`(男/女)、测量时间 `DatePicker` | 是 | `input.basic.*` |
| 身体围度 | 腰围/臀围/腕围 `TextField` | 否 | `input.circum.*` |
| 健康指标 | 收缩压/舒张压/心率 `TextField` | 否 | `input.vital.*` |
| 疾病勾选 | `CheckBox` 组 + 「无」互斥 | 否 | `input.disease.*` |
| 结果区 | BMI/分级 `Label`、体脂率 `Label` | — | `input.result.*` |
| 操作区 | 保存 / 加载历史 / 修改 `Button` | — | `input.save` 等 |

### 3.3 交互功能

- **实时 BMI 自动计算**：身高/体重任一变动即调 `BmiCalculator.calcBmi` + `classify` 实时刷新结果区（FR-03，保留 1 位小数，AC-03）；体脂率随年龄/性别/最新 BMI 调 `BodyFatCalculator.predictBodyFat` 刷新（FR-04，AC-04）。
- **保存多条独立历史记录**：每次点「保存本条记录」→ `RecordController.createRecord(userId, h, w, age, gender)` 落库一条**新** `body_record`（即使同日也独立成行，呼应 FR-05「保存每次测量」）。围度/指标/疾病等扩展字段按四.2 标记处理（需 schema 扩展）。
- **支持修改历史旧记录**：「加载历史旧记录」下拉（`ComboBox<BodyRecord>` 显示测量时间）选中后回填表单；改完点「修改选中记录」→ 调 `RecordController.updateRecord(record)`（**需新增 `RecordDao.update` / `RecordController.updateRecord`，扩展标记见四.2**）覆盖原行；修改后 BMI/体脂按新值重算。
- **校验**：必填项按一.3 规则校验，越界就地红字（AC-02/03/04）。
- **保存后联动**：保存/修改成功后回调刷新首页卡片与图表（`ChartController.getSeries` → `ChartView.repaint`）。

---

## 4. 历史数据统计页面（`HistoryView`）

### 4.1 界面布局

```
HistoryView（BorderPane）
 顶部：时间筛选 [起 DatePicker] [止 DatePicker] [查询] [重置]
 中心：左侧分页表格（时间/身高/体重/BMI/体脂率/操作[编辑][删除]）
       右侧：三个独立 LineChart
         · BMI 趋势
         · 体脂率 / 内脏脂肪 趋势
         · 血压 / 心率 对比图
 底部：[导出选中] [刷新]
```

### 4.2 控件清单

| 区域 | 控件 | 绑定 |
|------|------|------|
| 筛选 | 起止 `DatePicker` + 查询/重置 `Button` | `history.filter.*` |
| 表格 | `TableView<BodyRecord>`（分页，每页 20 行）+ 行内 编辑/删除 `Button` | `history.table.*` |
| 图表区 | 3 个 `LineChart`（复用 `ChartView` 渲染，传入不同指标/数据集） | `chart.*` |
| 底部 | 导出选中 / 刷新 `Button` | `history.export` `history.refresh` |

### 4.3 交互功能

- **时间筛选 + 分页表格**：`RecordController.queryRecords(userId, start, end)` 取数据，前端按 20 行/页分页；行内「编辑」回填 `InputView`、「删除」调 `RecordController.deleteRecord(userId, recordId)`（限定 `user_id` 防越权，AC-05）。
- **多独立动态折线图**：
  - BMI 趋势：`ChartView.setMetric(0)` + `repaint(series)`（数据来自 `body_record.bmi`）。
  - 体脂率趋势：`setMetric(2)`（来自 `body_record.body_fat`）；**内脏脂肪**需扩展字段（四.2 标记）。
  - 血压/心率对比图：收缩压、舒张压、心率三条线对比（**需扩展字段**，四.2 标记）。
- **数据修改/删除后图表实时刷新**：任意删除/编辑完成后，重新 `ChartController.getSeries` 并 `ChartView.repaint`，曲线立即同步（呼应 FR-06 动态性）。
- **空数据友好提示**：无记录时表格显示「暂无历史记录」（`common.emptyHint`），各图表区显示 `chart.empty` / `chart.noData`（见三.3）。

---

## 5. AI 健康分析页面（`AiAnalysisView`）

### 5.1 界面布局

```
AiAnalysisView（VBox）
 [历史记录选择▾]  [数据预览区：最新指标 + 近5次趋势文本]
 [获取 AI 建议 Button]
 [建议文本展示框 TextArea（可切 全文 / 饮食 / 运动 / 健康 分页）]
 [本地缓存状态：已缓存 / 未缓存]  [清除缓存]
```

### 5.2 控件清单

| 控件 | 绑定 | 说明 |
|------|------|------|
| 历史记录 `ComboBox<BodyRecord>` | `ai.record.select` | 选择作为「最新指标」的一条记录 |
| 数据预览 `Label`/`TextArea` | `ai.preview` | 展示所选记录指标 + `historyTrend` 文本摘要 |
| 获取建议 `Button` | `ai.genAdvice` | 触发 `AiController.getAdvice(userId)` |
| 建议展示 `TextArea` + 分段 `TabPane` | `ai.adviceText` | 全文 / 饮食 / 运动 / 健康 三分段（对应 `AiHealthResult.adviceText/dietAdvice/exerciseAdvice/healthAdvice`） |
| 缓存状态 `Label` + 清除 `Button` | `ai.cache.*` | 本地缓存管理 |

### 5.3 交互功能

- **历史记录下拉选择**：选中一条作为「最新指标」(`latest`)，预览区展示其 `bmi/bmiGrade/bodyFat/weight/height/age/gender/measureTime` 与近 5 次趋势摘要（由 `AiService.buildRequest` 内部构造的 `historyTrend` 文本）。
- **生成建议**：点「获取 AI 建议」→ `AiController.getAdvice(userId)` → `AiService.requestAdvice(buildRequest(user, latest, history))`，返回字符串填入展示框（成功取 `adviceText`，失败取 `message` 降级文案）。
- **本地缓存逻辑**：首次成功获取后，将建议文本按 `recordId` 缓存到本地 `user.home/bmi/ai_cache/{recordId}.txt`（仅存文本路径，不入库）；再次进入同记录先读缓存秒显，可「清除缓存」强制重新生成；缓存文件同属本地数据不提交仓库。
- **四类异常场景页面提示处理规范**（严格对齐 ai_design §5.1，视图只展示，不判断）：

| 场景 | 页面表现（直接展示文案） |
|------|--------------------------|
| 断网 | 展示区显示「暂时无法获取 AI 建议，请检查网络或稍后再试」 |
| 接口超时 | 显示「AI 建议请求超时，请稍后重试」 |
| 参数为空 | 显示「当前数据不完整，暂无法生成 AI 建议，请先完成一次身高体重录入」 |
| 服务器报错 | 显示「AI 服务暂时不可用，请稍后再试」 |
| 密钥缺失 | 显示「AI 服务未配置，请联系管理员」 |

> 不阻断主流程：无论成功或降级，录入/图表照常（呼应 ai_design §2 不阻断原则）。

---

## 6. 体型照片管理页面（`PhotoView`）

### 6.1 界面布局

```
PhotoView（VBox）
 [关联体检记录▾]  [上传照片 Button]  [当前记录路径 Label]
 [已绑定照片列表：文件名 + 路径文本 + 打开文件夹 Button]  （无预览）
```

### 6.2 控件清单

| 控件 | 绑定 | 说明 |
|------|------|------|
| 关联记录 `ComboBox<BodyRecord>` | `photo.record.select` | 选择要绑定的体检记录 |
| 上传照片 `Button`（调 `FileChooser`） | `photo.upload` | 仅允许本地图片（jpg/png） |
| 路径 `Label` | `photo.path` | 仅显示路径文本，**无 ImageView 预览** |
| 打开文件夹 `Button` | `photo.open` | `Desktop.open()` 系统程序打开原图 |

### 6.3 交互功能

- **上传 + 绑定**：`FileChooser` 选图 → 复制到 `user.home/bmi/photos/{recordId}_{ts}.jpg` → 将路径写入该记录 `photo_path`（需 `body_record` 增加 `photo_path TEXT`，见四.2 扩展标记）→ 列表刷新。
- **仅存储本地路径**：严格遵循一.5 —— 不预览、不入库二进制、DB 仅存路径字符串；路径白名单校验仅本地协议。
- **解绑/删除**：可清除 `photo_path` 并（可选）删除本地文件。

---

## 7. 报告导出页面（`ReportView`）

### 7.1 界面布局

```
ReportView（VBox）
 [时间范围▾ 或 全部]  [报告内容勾选：基础数据/趋势图/AI建议]
 [导出 HTML Button]  [保存路径 Label + 打开文件 Button]
```

### 7.2 控件清单

| 控件 | 绑定 | 说明 |
|------|------|------|
| 范围 `ComboBox` / `DatePicker` | `report.range` | 全部 / 自定义区间 |
| 内容 `CheckBox` 组 | `report.content.*` | 基础数据、趋势图、AI 建议 |
| 导出 `Button` | `report.export` | 生成 HTML |
| 路径 `Label` + 打开 `Button` | `report.path` | 本地保存路径 |

### 7.3 交互功能

- **导出 HTML 完整报告**：由 `ReportController`（**新增，扩展标记见四.2**）聚合 `RecordController.queryRecords` 数据 + `ChartController` 截图/内联 SVG 趋势 + 已缓存的 `AiHealthResult.adviceText`，渲染为单文件 `bmi_report_{userId}_{date}.html`。
- **本地保存路径规则**：默认 `user.home/bmi/reports/`，文件名含用户 ID 与日期防覆盖；路径在页面展示并可一键打开；报告文件属本地数据不提交仓库。
- **内容可选**：按勾选项决定报告章节，趋势图以内联 SVG/Base64 图片嵌入（仅报告内，不影响 DB）。

---

## 8. 系统设置页面（`SettingsView`）

### 8.1 界面布局

```
SettingsView（VBox / TabPane）
 [全局配色切换▾]  [语言默认设置▾]  [个人健康资料修改入口 Button]
 [数据库/AI 配置说明（只读提示，不展示密钥）]
```

### 8.2 控件清单

| 控件 | 绑定 | 说明 |
|------|------|------|
| 配色 `ComboBox<Theme>` | `setting.theme` | 切换全局 CSS 变量 |
| 语言默认 `ComboBox<Lang>` | `setting.lang.default` | 写 `app-config.properties` 的 `ui.lang.default` |
| 个人资料修改 `Button` | `setting.profile` | 跳转/弹窗修改 `User` 资料（身高基线、目标等） |
| 配置说明 `Label` | `setting.config.note` | 提示密钥在 `ai-key.properties`/`db-config.properties`，不展示明文 |

### 8.3 交互功能

- **全局配色切换**：选主题后覆盖 `.root` 的 `-bmi-primary/-bmi-bg/-bmi-fg`，全应用即时生效（复用一.2 样式机制）。
- **语言默认设置**：写入 `app-config.properties`，下次启动生效；当前会话可同步切换。
- **个人健康资料修改入口**：打开资料编辑（更新 `User` 或扩展档案；若需持久化年龄/性别基线等，按四.2 扩展标记处理）。
- **安全**：本页不读取/展示任何密钥明文，仅给出配置文件位置说明（呼应宪章第 4/7 节密钥安全）。

---

# 三、图表专项规范

## 1. `ChartView` 动态折线图渲染标准

- **组件**：JavaFX `javafx.scene.chart.LineChart<Number, Number>`（宪章第 2 节指定，零额外依赖）。
- **坐标**：横轴 `NumberAxis`（序号或时间戳转数值），纵轴 `NumberAxis`（指标值）；轴标签、刻度字体统一 `.bmi-chart` 样式。
- **数据系列**：每个指标一条 `XYChart.Series`；数据点 `Circle` 标记；折线用主题色 `-bmi-primary`。
- **指标编码**（对齐 plan.md §4.1 `ChartView.setMetric(int metric)`）：

| metric | 含义 | 数据源字段 |
|--------|------|------------|
| 0 | BMI 趋势 | `body_record.bmi` |
| 1 | 体重趋势 | `body_record.weight` |
| 2 | 体脂率趋势 | `body_record.body_fat` |

- **多指标对比图**（历史页血压/心率）：同一 `LineChart` 内多条 `Series`（收缩压/舒张压/心率），各自颜色区分。

## 2. 多条历史记录自动加载、新增/删除同步更新

- **自动加载**：`ChartController.getSeries(userId, metric)` → `RecordDao.queryByUser(userId, null, null)`（按 `measure_time` 升序）→ `ChartView.repaint(List<BodyRecord>)` 一次性绘全部点。
- **新增同步**：`InputView` 保存成功后回调 `MainView`/`HistoryView` 重新 `getSeries` + `repaint`，曲线立即包含新点。
- **删除同步**：`HistoryView` 删除记录 → `RecordController.deleteRecord` 成功后重新 `getSeries` + `repaint`，曲线立即剔除该点。
- **切换指标**：`setMetric(m)` 后 `repaint` 同数据集，重绘 **< 500ms**（AC-06）。

## 3. 自适应数据条数、无数据友好提示

- **自适应**：数据点任意数量均可绘制；横轴刻度随点数自动稀疏（JavaFX 默认行为）。
- **数据点 < 2**：不绘趋势线，显示 `chart.noData`（「至少需 2 条记录才能显示趋势」，AC-06）。
- **无任何记录**：显示 `chart.empty`（「暂无记录，无法绘制图表」），图区留白不报错（呼应 FR-06 异常处理）。

---

# 四、Controller 对接约束

## 1. 页面控制器调用 `AiService` / `BodyRecord` 参数格式（严格对齐 ai_design.md、db_design.md）

- **AI 调用统一入口**：所有页面（首页/Ai分析页/报告页）获取建议均经 `AiController.getAdvice(long userId)`；其内部：
  1. `RecordController.queryRecords(userId, null, null)` 取 `latest`（最新）与 `history`（列表）；
  2. `AiService.buildRequest(User u, BodyRecord latest, List<BodyRecord> history)` 构造请求；
  3. `AiService.requestAdvice(AiRequest req)` 返回字符串（成功 `adviceText` / 失败 `message` 降级）。
- **`AiRequest` 参数格式（对齐 ai_design §3）**：

| 段 | 字段 | 类型 | 来源 |
|----|------|------|------|
| `userMetrics` | `bmi` / `bmiGrade` / `bodyFat` / `weight` / `height` / `age` / `gender` / `measureTime` | double/String/double/.../int/String | 取自 `BodyRecord`（持久化字段）+ `age`/`gender`（瞬时字段，见下） |
| `historyTrend` | `count` / `direction` / `points[]` | int/String/Array | 由 `history` 截取近 5 条计算 |
| `modelParams` | `model` / `temperature` / `maxTokens` | String/double/int | 读 `ai-key.properties`（`api.model` 等） |
| `systemPrompt` | 角色设定文本 | String | 常量（I18n 无关，服务端文案） |

- **`BodyRecord` 传参格式（对齐 db_design §3）**：

| 字段 | 类型 | 是否持久化 | 说明 |
|------|------|------------|------|
| `id` / `userId` | long | 是 | 记录归属 |
| `measureTime` | Timestamp | 是 | 测量时间 |
| `height` / `weight` | double | 是 | cm / kg，区间 50–250 / 10–300 |
| `bmi` | double | 是 | `BmiCalculator.calcBmi` 结果，1 位小数 |
| `bodyFat` | double | 是 | `BodyFatCalculator.predictBodyFat` 结果，1 位小数 |
| `createdAt` | Timestamp | 是 | 入库时间 |
| `age` / `gender` | int | **否（瞬时）** | 仅用于体脂计算与 AI 请求；按 db_design 约定不落库 |

## 2. UI 与底层模块数据传递字段统一规范

### 2.1 字段统一映射表（UI ↔ 实体 ↔ 数据库）

| UI 录入项 | `BodyRecord` 字段 | `body_record` 列 | 必填 | 状态 |
|------------|-------------------|----------------|------|------|
| 身高 | `height` | `height` | 是 | ✅ 已对齐 |
| 体重 | `weight` | `weight` | 是 | ✅ 已对齐 |
| BMI | `bmi` | `bmi` | 自动 | ✅ 已对齐 |
| 体脂率 | `bodyFat` | `body_fat` | 自动 | ✅ 已对齐 |
| 测量时间 | `measureTime` | `measure_time` | 是 | ✅ 已对齐 |
| 年龄 | `age` | （瞬时，不落库） | 是 | ✅ 已对齐（不入库） |
| 性别 | `gender` | （瞬时，不落库） | 是 | ✅ 已对齐（不入库） |
| 腰围/臀围/腕围 | — | ⚠️ 未定义 | 选填 | ❗ **需扩展**：`body_record` 增 `waist_circum`/`hip_circum`/`wrist_circum` NUMERIC |
| 收缩压/舒张压/心率 | — | ⚠️ 未定义 | 选填 | ❗ **需扩展**：增 `systolic_bp`/`diastolic_bp`/`heart_rate` NUMERIC |
| 内脏脂肪 | — | ⚠️ 未定义 | 选填 | ❗ **需扩展**：增 `visceral_fat` NUMERIC（或来自设备） |
| 既往疾病 | — | ⚠️ 未定义 | 选填 | ❗ **需扩展**：增 `diseases` TEXT（逗号分隔）或独立 `body_disease` 表 |
| 体型照片路径 | — | ⚠️ 未定义 | 选填 | ❗ **需扩展**：增 `photo_path` TEXT |

> ⚠️ **扩展标记（需修订 db_design.md）**：本 UI 设计引入的身体围度、健康指标、内脏脂肪、既往疾病、照片路径等字段，**超出当前 `body_record` 表结构**。落地前须经 DBA 修订 `docs/db_design.md`（新增列或独立扩展表），并同步更新 `BodyRecord` 实体与 `RecordDao` 的 `insert`/`update`/`queryByUser`（含 `RecordDao.update` 新增）。扩展字段同样遵循宪章「小写下划线」命名与「db 层不含业务计算」铁律。

### 2.2 新增/扩展控制器（在 plan.md 四控制器基础上）

| 控制器 | 状态 | 说明 |
|--------|------|------|
| `UserController` / `RecordController` / `ChartController` / `AiController` | ✅ 已定义（plan.md §4.2） | 全部页面主调用入口 |
| `RecordController.updateRecord(BodyRecord)` + `RecordDao.update` | ❗ 扩展 | 支撑「修改历史旧记录」（InputView） |
| `PhotoController` | ❗ 扩展 | 照片上传/绑定/解绑（PhotoView），命名遵循 `XxxController` |
| `ReportController` | ❗ 扩展 | 报告聚合导出（ReportView），命名遵循 `XxxController` |
| `SettingController` | ❗ 扩展 | 配色/语言/资料（SettingsView），命名遵循 `XxxController` |

> 所有新增控制器必须遵循宪章第 4 节命名（后缀 `XxxController`）、第 5 节分层铁律（仅调度 model，不直接访问 DB/AI 细节），且不得引入白名单外技术。

### 2.3 统一数据传递契约

- **入参**：视图只向 controller 传**基础标量**（`double height, double weight, int age, int gender, Timestamp measureTime` 等），公式与组装由 controller/model 完成（呼应 plan.md §2 职责隔离）。
- **出参**：controller 向视图返回**实体或实体列表**（`BodyRecord` / `List<BodyRecord>` / `String` 建议文本），视图自行渲染，不反向依赖 model。
- **字段命名**：UI 控件 id、实体字段、DB 列名三者统一小驼峰/小写下划线映射（见 2.1），禁止出现 `UserEntity`/`TUser` 等背离宪章命名。

---

## 5. FR / AC 追溯

| 需求 | UI 落点 | 对齐文档 |
|------|---------|----------|
| FR-01 登录注册 | `LoginView`（登录区+注册折叠面板、本地验证码、重复拦截） | spec/plan/db_design |
| FR-02 身高体重录入 | `InputView` 基础必填面板 + 一.3 校验 | spec AC-02 |
| FR-03 BMI 计算分级 | `InputView` 实时计算 + 首页卡片分级色块 | spec AC-03 |
| FR-04 体脂预测 | `InputView`/`AiService` 用 `BodyFatCalculator` | spec AC-04 |
| FR-05 历史记录 | `HistoryView` 表格（编辑/删除）+ `RecordController` | spec AC-05 |
| FR-06 动态折线图 | `ChartView` + 历史页三图 + 三.2 同步 | spec AC-06 |
| FR-07 AI 健康建议 | `AiAnalysisView` + 四.1 调 `AiController`/`AiService` + 四类异常 | ai_design §5 / AC-07 |
| 扩展：双语 | 一.1/一.2 `I18n` + `Lang` 切换 | 用户明确需求（叠加于 spec） |
| 扩展：照片/报告/设置 | 六/七/八章 + 四.2 扩展控制器 | 需 db_design 修订 |

> 7 个核心 FR 全部有对应 UI 页面与控制器调用；扩展页面（照片/报告/设置/双语）为本次 UI 设计新增，已标记其对 `db_design.md` / `plan.md` 的扩展依赖。
