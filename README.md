# MonaFlow · 莫奈艺术风个人效率中心

Kotlin + Jetpack Compose + Material 3 的本地优先个人效率 App：笔记、待办、习惯打卡、物品记录、倒计时与可自定义 AI 服务商，整合在一个离线可用的应用中。首版优先适配小米 HyperOS。

## 功能总览

| 模块 | 能力 |
| --- | --- |
| 今日 | 日期问候、今日任务进度、今日待办（逾期红标）、习惯快捷打卡、倒计时、最近笔记/物品、快速新建、AI 入口 |
| 笔记 | 增删改查、置顶/收藏/归档/回收站、标签（多对多）、全文搜索（防抖）、图片附件（复制进私有目录）、自动保存（800ms 防抖）、转待办 |
| 待办 | 收件箱/今天/即将到期/已完成/全部五视图、优先级、子任务进度、重复任务（统一简化 RRULE，完成后单事务生成下一次实例）、提醒 |
| 打卡 | 每日/每周指定日/自定义间隔、连续天数/累计/完成率、月历（应执行/完成/漏卡/跳过）、暂停/归档、显式确认补卡 |
| 物品 | 名称/分类/品牌/型号/数量/价格（最小货币单位）/币种/购买日期/位置/保修期/图片/备注、保修状态徽标 |
| 倒计时 | 纯日期差计算（跨月/跨年/闰年/时区安全）、每年重复（2/29 → 2/28 钳制）、主题色、全天/时刻 |
| AI | Provider/Adapter 架构，OpenAI Chat Completions 兼容协议（DeepSeek/Kimi/GLM/千问/自定义），Keystore 加密密钥、连接测试错误分类、结构化输出校验+预览确认、用量记录（无定价数据时费用标记未知） |
| 主题 | 莫奈 Token 体系（禁止页面硬编码）、4 预设主题、浅/深/跟随系统、图片取色（本地 Palette，预览/应用/取消/恢复默认/色相微调） |
| 提醒 | AlarmManager 精确闹钟（权限缺失自动退化）、习惯提醒触发后自动顺延、WorkManager 每日巡检（保修/今日倒计时）、开机/时间/时区变化重建、通知点击跳转对应页面 |
| 备份 | ZIP（manifest + 结构化 JSON + 附件）、SHA-256 校验、条数核对、外键一致性校验、路径穿越防护、单事务覆盖恢复（失败不破坏现有数据）、不含 API Key |
| 首页 | 模块显示/隐藏、紧凑模式（顺序字段已就绪） |

## 构建环境（本机）

```
JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot
GRADLE_USER_HOME=D:\AI\rj\tools\gradle-home     # 中文用户名必须指 ASCII 路径
TMP / TEMP=D:\AI\rj\tools\tmp
ANDROID_SDK_ROOT=D:\AI\rj\tools\android-sdk
Gradle: D:\AI\rj\tools\gradle-8.9\bin\gradle.bat（wrapper 已指向华为云镜像）
```

```bat
:: Debug APK
gradle.bat assembleDebug
:: 产物 app/build/outputs/apk/debug/app-debug.apk

:: Release APK（未签名；发布需自行配置签名）
gradle.bat assembleRelease

:: 单元测试（88 个：RRULE/日期/金额/取色/Room/备份/AI 错误分类与重试/设置持久化/提醒时间）
gradle.bat testDebugUnitTest
```

技术版本：AGP 8.5.2 · Kotlin 2.0.21 · Compose BOM 2024.10.00 · Room 2.6.1（KSP）· minSdk 26 / targetSdk 35 · 依赖走阿里云镜像。

## 目录结构

```
app/src/main/java/com/monaflow/app/
├── core/            theme（莫奈 Token/预设/取色）· navigation · notifications · security（Keystore）· ui（通用组件）· common（RRULE/日期/金额）
├── data/
│   ├── local/       MonaFlowDatabase（v1 + Migration 通道）· entity ×12 · dao ×10 · AttachmentStore
│   ├── repository/  Note · Task · Habit · Item · Countdown
│   ├── backup/      BackupManager（ZIP 导出/校验/事务恢复）
│   ├── ai/          AiService（重试/用量）· OpenAiCompatAdapter（错误分类）· StructuredOutputParser
│   └── settings/    SettingsRepository（DataStore）
└── feature/         today · notes · tasks · habits · items · countdown · ai · mine · settings
```

## 数据语义约定（二次开发必读）

- **时间戳**（createdAt/updatedAt/dueAt/reminderAt）= epochMillis（UTC 瞬时）
- **当地日期**（打卡/购买日期/保修日期）= epochDay
- **倒计时天数** = 目标时区 LocalDate 差值，禁止 24h 除法
- **金额** = 最小货币单位 Long（JPY/KRW 零小数位），禁止浮点
- **重复规则** = 统一简化 RRULE（`FREQ=DAILY|WEEKLY;INTERVAL=n;BYDAY=MO,WE`），习惯/任务/倒计时共用 `RecurrenceRule`
- 数据库升级只允许 Migration（`ALL_MIGRATIONS`），禁止破坏性重建

## 已知限制 / 后续路线

- 合并恢复未实现（覆盖恢复已明确告知用户）
- AI 协议当前仅 OpenAI 兼容（其他协议按 Adapter 扩展）
- 首页模块**顺序**调整 UI 未做（order 字段与渲染已支持）
- Keystore 加密依赖真机安全硬件，Robolectric 中以 Fake Vault 测试业务逻辑
- Macrobenchmark/真机 HyperOS 实测待实机到位后补充
