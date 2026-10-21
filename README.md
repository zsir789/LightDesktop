# 轻桌面 LightDesk v1.2.0

> 一款模块化、纯本地的移动端桌面小工具（Android · Kotlin · Jetpack Compose）。
> 首页 · 备忘（备忘录 + 待办）· 课表 · 天气 · 设置与统计，全部数据保存在本机。
>
> 仓库：<https://github.com/zsir789/LightDesktop> · 历史版本与签名 APK：`versions/`

---

## 一、项目简介

轻桌面把日常高频使用的小功能收进一个 App：打开就是首页（今日课表 + 待办倒计时），
随手记备忘或待办、导入 WakeUp 课表按周/月查看并自由缩放、查看天气、在「设置与统计」里换一套喜欢的配色
并回看自己的使用情况。所有数据本地持久化，不联网也能查看备忘、课表、待办与天气缓存。

工程按「核心层 + 功能层 + 组装层」拆分，功能模块之间零耦合，新增功能只需加一个 `:feature:xxx` 模块并在
`:app` 注册，已为后续迭代预留扩展点（见文末）。

### v1.2.0 主要变化

| 变化 | 说明 |
| --- | --- |
| 6 套浅色主题 | 海盐蓝 / 薄荷绿 / 蜜桃橙 / 薰衣草 / 暖沙金 / 樱花粉，可在「设置与统计」即时切换并持久化 |
| 课程彩色色块 | 每门课一个柔和 pastel 色块（同一门课颜色固定），周视图、月视图、当天列表统一呈现五颜六色又清爽的课表 |
| 课表可缩放 | 顶栏「－ / 档位 / ＋ / 全览」+ 双指捏合，0.6x–2.0x 自由缩放；「全览」一键看完整周安排 |
| 更多色块与动效 | 首页欢迎卡与课程/待办色块、快捷入口按压缩放与错峰淡入、备忘卡片色块与完成动画、底部导航选中色块胶囊 |
| 版本归档 | 每个版本保留安装包、源码包与更新报告（`versions/vX.Y.Z/`），v1.0.0 / v1.1.0 原样保留 |

> v1.1.0 的首页聚合、备忘与待办合并、设置与统计合页、课表月视图、移除悬浮球等变化均保留。

---

## 二、功能清单

| 模块 | 功能 | 说明 |
| --- | --- | --- |
| 首页 | 问候与日期、第 N 教学周、今日课程（色块卡）、今日待办倒计时、快捷入口 | 只读聚合页，不写数据 |
| 备忘 | 备忘录与待办合并：增删改查、完成标记、置顶、搜索、按类型/状态筛选、待办实时倒计时 | 数据保存在 `notes.json`；带截止时间即为待办 |
| 课表 | 导入 WakeUp 导出的 `.ics`，周视图 + 月视图，课程彩色色块，缩放与全览，课程详情，清空重导 | 自动推导学期起始周；支持 TZID/UTC/浮动时间、RRULE、EXDATE |
| 天气 | Open-Meteo 联网获取实时与 7 天预报 | 断网/超时/解析失败自动降级为本地缓存并标注更新时间 |
| 设置与统计 | 主题模式、**主题配色**、天气城市、倒计时提醒阈值、统计开关、关于、清空统计；使用次数、备忘统计、天气类型、近 12 周热力图 | 数据本地保存，可一键清空 |

---

## 三、安装包

| 产物 | 路径 | 大小 |
| --- | --- | --- |
| Release APK（推荐安装） | `release/LightDesk-v1.2.0.apk` | 11.2 MB（11,776,250 字节） |
| 源码包 | `release/LightDesk-source-v1.2.0.zip` | 约 263 KB（269,658 字节，源码压缩包，含全部模块与文档） |
| 版本归档目录 | `versions/v1.2.0/` | — |
| 历史版本 | `versions/v1.0.0/`、`versions/v1.1.0/` | — |
| 校验值 | `versions/SHA256SUMS.txt` | — |

APK SHA-256：

```text
7a2222746c1de020b92224ebd68c9e2a6e028b666b2d20d3a9c25b4c77c9cab0  LightDesk-v1.2.0.apk
```

安装步骤：

1. 将 `release/LightDesk-v1.2.0.apk` 传到手机（数据线 / 网盘均可）；
2. 点击安装，若系统提示“未知来源应用”，在弹窗中允许当前文件管理器安装；
3. 从 v1.0.0 / v1.1.0 升级可直接覆盖安装（同一签名），原有备忘、待办、课表、历史统计数据全部保留；
   旧版没有配色字段，升级后默认使用「海盐蓝」，可在「设置与统计 → 主题配色」中更换。

**系统要求**：Android 8.0（API 26）及以上。
**签名**：仓库内附自签名证书 `keystore/lightdesk-release.jks`（仅供本项目使用，口令见 `keystore.properties`）。
**架构**：通用 APK，支持 arm64-v8a / armeabi-v7a / x86 / x86_64。

---

## 四、权限说明

| 权限 | 用途 |
| --- | --- |
| `INTERNET` / `ACCESS_NETWORK_STATE` | 获取天气数据、判断网络是否可用 |

> 应用不申请悬浮窗、前台服务、通知、通讯录、位置、存储等权限；
> 天气定位使用设置中的城市（内置常用城市坐标，无需定位权限）。

---

## 五、使用说明

### 首页
- 顶部问候与日期，导入课表后显示当前教学周；
- 「今日课程」以彩色色块卡按时间顺序排列，正在进行或即将开始的课程高亮，已结束的课程淡化；
- 「今日待办」显示未完成且截止时间在今天及之前的待办，右侧为实时倒计时（逾期红色、1 小时内警告色）；
- 底部「全部未完成待办 N 项」与快捷入口可跳到备忘 / 课表页。

### 备忘（备忘录 + 待办）
- 右下角 `+` 新建；标题必填，正文可选；打开「设为待办」后再选截止时间，即成为带倒计时的待办；
- 每张卡片按 id 取一个柔和底色与左侧强调条，便于快速区分；
- 卡片勾选可标记完成（完成动画为填充 + 对勾 spring 回弹，卡片沉底并淡化）；
- 顶部支持关键字搜索与「全部 / 备忘 / 待办 / 已完成」筛选；卡片菜单可置顶、编辑、删除（删除二次确认）；
- 待办卡片实时显示「剩余 2小时13分 / 已逾期 1小时」。

### 课表
1. 打开 WakeUp，进入「课表 → 导出/分享 → 导出为 ics 日历文件」；
2. 回到轻桌面「课表」页，点击「导入课表」，选择刚导出的 `.ics` 文件；
3. 导入后自动推导学期第 1 周，顶部可切换「周视图 / 月视图」：
   - 周视图：时间轴 + 周一至周日，今日列高亮，每门课为独立彩色色块，点击查看教师/地点/周次；
   - 月视图：周一起始网格，今天高亮，每个日期最多展示 2 条彩色课程条，点击日期在下方查看当天全部课程；
4. **缩放**：顶栏 `－` / `＋` 按 1.25 倍档位缩放，中间 TagPill 显示当前档位（全览 / 标准 / 150% 等）；
   也可以双指捏合直接缩放；点「全览」一键缩到 0.6x 并回到顶部，一屏看完整周安排；
5. 可随时重新导入或清空课表（清空二次确认）。

> 没有 WakeUp 导出的文件时，可先用仓库内示例 `samples/wakeup-course-sample.ics` 验证导入流程
> （示例包含 TZID、UTC、浮动时间、RRULE、EXDATE 等常见写法）。

### 天气
- 自动读取设置中的城市并拉取实时天气与未来 7 天预报；
- 无网络时展示最近一次缓存，并提示缓存更新时间；点击「重试」可重新拉取；
- 城市在「设置与统计 → 天气城市」中切换（内置常用城市，含经纬度）。

### 设置与统计
- **主题配色**：6 套浅色系配色（海盐蓝 / 薄荷绿 / 蜜桃橙 / 薰衣草 / 暖沙金 / 樱花粉），点击色卡立即生效；
- 设置区：主题模式（跟随系统 / 浅色 / 深色）、主题配色、天气城市、倒计时提醒阈值、使用统计开关、关于、清空统计数据；
- 统计区：各功能使用次数与占比、备忘记录数与完成次数/完成率、各天气类型出现次数、近 12 周日历热力图、今日数据小结；
- 没有任何统计数据时统计区显示空状态，设置区始终可用。

---

## 六、技术栈

| 类别 | 选型 |
| --- | --- |
| 语言 | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| 架构 | 单 Activity + Navigation Compose，MVVM，仓储模式 |
| 主题 | 6 套 `ThemePalette` → `LightDeskPalette` → Material3 ColorScheme（`CompositionLocal` 下发） |
| 异步 | Kotlin Coroutines + Flow/StateFlow |
| 持久化 | 自研 `JsonFileStore`（原子写入、损坏备份回退），kotlinx.serialization |
| 网络 | OkHttp + kotlinx.serialization（Open-Meteo，无需 API Key） |
| 构建 | Gradle 8.11.1 + AGP 8.7.2，多模块 |
| 最低版本 | Android 8.0（API 26），targetSdk 35 |

---

## 七、工程结构

```
dsh_desktop_master/
├─ app/                         # 壳工程：MainActivity、导航、依赖组装
├─ core/
│  ├─ common/                   # AppResult/错误码/日志/时间格式化/日历工具/调度器/ViewModel 工厂
│  ├─ model/                    # 数据模型（NoteItem/Course/Weather/UsageStats/AppSettings/ThemePalette）+ 课表日历计算
│  ├─ data/                     # JSON 持久化、仓储接口与实现、数据迁移、StatsTracker
│  └─ designsystem/             # 主题（Palette 配色注册表 / CourseColors 课程色板 / 字体 / 圆角）+ 通用组件
├─ feature/
│  ├─ home/                     # 首页（今日课表 + 待办倒计时）
│  ├─ notes/                    # 备忘（备忘录 + 待办合并）
│  ├─ timetable/                # 课表（ICS 解析 + 周视图 + 月视图 + 缩放）
│  ├─ weather/                  # 天气（Open-Meteo + 离线降级）
│  └─ stats/                    # 设置与统计（配色选择 + 热力图）
├─ docs/                        # 架构说明、开发契约、更新报告
├─ scripts/                     # 构建/归档脚本
├─ versions/                    # 每个版本的安装包 + 更新报告归档
├─ keystore/                    # 发布签名（仅本项目使用）
├─ release/                     # 最新交付产物
├─ gradle/libs.versions.toml    # 版本目录（统一依赖版本）
└─ README.md
```

### 架构与数据流

```
Compose UI (feature)
      │ 只依赖接口
      ▼
ViewModel / UiState
      ▼
Repository 接口（core:data）
      ▼
JsonFileStore（原子写 + 异常兜底）
      ▼
filesDir/lightdesk/*.json
```

主题数据流（v1.2.0）：

```
AppSettings.themePalette ──> MainActivity ──> LightDeskTheme(palette = ...)
                                                   │
                                 LightDeskPalettes.of(palette, dark)
                                                   │
                              Material3 ColorScheme + LocalLightDeskPalette
                                                   │
                    各页面用 MaterialTheme 颜色 / accentSoft / CourseColors 色块
```

- 功能模块之间互不依赖；共享能力下沉到 `core`，由 `:app` 的 `AppContainer` 统一组装注入；
- 写入失败返回 `AppResult.Failure`，UI 提示，不会崩溃；读取损坏文件会自动备份为 `.corrupt` 并回退默认值；
- 统计埋点通过 `StatsTracker` 异步写入，失败不会影响主流程；页面访问由 `:app` 的 `NavHost` 统一记录，避免各页面重复埋点。

详见 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)。

---

## 八、本地数据

所有数据位于应用私有目录下，卸载应用即删除：

```
/data/data/com.lightdesk.app/files/lightdesk/
├─ settings.json       # 主题模式、主题配色、天气城市、提醒阈值、统计开关
├─ notes.json          # 备忘 + 待办（统一事项）
├─ courses.json        # 课表（含学期起始周）
├─ usage_stats.json    # 使用统计与每日活跃
└─ weather_cache.json  # 天气缓存（离线降级）
```

从 v1.0.0 升级时，首次启动会自动把旧的 `memos.json` / `todos.json` 合并迁移为 `notes.json`，
旧统计维度也会迁移到新结构；若没有旧数据则不执行任何迁移。
从 v1.1.0 升级时，`settings.json` 自动补齐 `themePalette`（默认海盐蓝），其余文件结构不变。

每个文件带 `schemaVersion` 字段，为后续版本数据迁移预留。

---

## 九、构建与测试

### 环境

- JDK 17+（本项目使用 JDK 21）
- Android SDK Platform 35 + Build-Tools 35.0.0
- Gradle Wrapper（已随仓库提供，无需单独安装）

`local.properties` 中配置 SDK 路径（已被 `.gitignore` 忽略，按本机实际路径修改）：

```properties
sdk.dir=D:/path/to/android-sdk
```

### 构建

```powershell
$env:JAVA_HOME='D:\path\to\jdk21'
$env:ANDROID_HOME='D:\path\to\android-sdk'

# 调试包
.\gradlew.bat :app:assembleDebug

# 发布包（使用 keystore.properties 中的签名）
.\gradlew.bat :app:assembleRelease

# 一键构建 + 归档（单测 → 签名 APK → 源码包 → versions/v1.2.0）
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build-release.ps1 -Version 1.2.0
```

依赖仓库已在 `settings.gradle.kts` 中配置阿里云镜像（Google / Central / Gradle Plugin），
Gradle 发行版使用腾讯云镜像；如需官方源可自行替换。

### 测试

当前工程共 **216 个 JVM 单元测试，全部通过（0 失败 0 错误）**，覆盖：

- 通用层：时间格式化 / 倒计时文案、日历网格与周计算、JSON 存储的读写与损坏回退；
- 数据层：统一事项仓储、v1.0.0 数据迁移、统计聚合与迁移、设置持久化；
- 课表：ICS 解析（折叠行、TZID/UTC/浮动时间/全天事件、RRULE、EXDATE、坏数据）、教学周与月视图计算、
  缩放档位与行高换算、课程色块取色稳定性；
- 天气：WMO 天气码映射、请求 URL 构建、DTO → 模型映射；
- 功能逻辑：首页聚合与倒计时、备忘筛选/排序/倒计时、统计展示与热力图分级、配色选项与备忘视觉参数。

```powershell
# 全量单测
.\gradlew.bat testDebugUnitTest
```

> 多个构建同时执行会争用 Kotlin 增量缓存，脚本 `scripts/gw.ps1` 提供命名互斥锁串行执行：
> `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\gw.ps1 testDebugUnitTest`

---

## 十、扩展点（预留迭代接口）

| 扩展方向 | 接入方式 |
| --- | --- |
| 新增功能模块 | 新建 `:feature:xxx`，在 `:app` 的 `TopLevelDestination` / `NavHost` 注册；`FeatureKey` 追加枚举值即可自动出现在统计页 |
| 新增主题配色 | 在 `ThemePalette` 加枚举 + `LightDeskPalettes` 注册一套浅/深色值，设置页色卡自动多一项，页面无需改动 |
| 自定义课程色板 | 只改 `core:designsystem/theme/CourseColors.kt` 的色板数组（12 色），课表/备忘/首页色块同步生效 |
| 更换存储引擎 | 实现 `core:data` 中对应 Repository 接口（如 Room 版），在 `AppContainer` 替换实现，功能模块零改动 |
| 更换天气服务 | 实现 `feature:weather` 的数据源接口并替换注入 |
| 更多课表来源 | 新增 `TimetableImporter` 实现（CSV、教务系统等），UI 选择导入方式（当前已有 `IcsParser` 实现） |
| 课表更多视图/手势 | 复用 `TimetableZoom`（缩放/行高纯函数）与 `TimetableCalendar`（周/月课程计算）扩展新视图 |
| 系统通知提醒 | `NoteItem.deadline` 已就绪，接入 WorkManager/AlarmManager 即可做到期提醒 |
| 品牌字体 / 动态取色 | 字体在 `LightDeskTypography` 集中替换；也可用 Material3 dynamicColor 生成 `LightDeskPalette` |
| 云端同步 | 在 Repository 之上叠加同步层，或实现远程数据源 |
| 自定义统计维度 | `StatsRepository` 已按“访问/备忘/天气/每日”拆分记录方法，可继续扩展 `DailyActivity` 字段 |

---

## 十一、已知限制

- 课表缩放档位不跨页面持久化：离开课表页再进入会回到 1.0x（缩放属于即时操作，未写入设置）；
- 课程色板为 12 色滚动取色，课程超过 12 门时颜色会重复（同一门课颜色始终稳定）；
- 天气数据来自 Open-Meteo（海外服务），弱网环境可能较慢，已做超时与缓存降级；
- 课表解析遵循标准 iCalendar 规范，若 WakeUp 导出使用了非标准字段，部分信息（如教师）可能为空；
  非 WEEKLY 重复事件只取首次实例并给出解析提示；
- 月视图在窄屏上每个日期最多展示 2 条课程，其余折叠为“+N”；
- 安装包为自签名发布包，未上架应用商店；升级安装需使用同一签名（本仓库内 `keystore/`）；
- 本次交付通过编译与 JVM 单测验证，未在真机 / 模拟器做完整 UI 走查，配色观感建议真机确认。

---

## 十二、版本记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0.0 | 2026-10-19 | 首个版本：备忘录、课表、天气、交互统计、悬浮球、待办倒计时 |
| v1.1.0 | 2026-10-19 | 首页、备忘与待办合并、设置与统计合页、课表月视图、移除悬浮球 |
| v1.2.0 | 2026-10-21 | 6 套浅色主题与换色设置、课程与卡片彩色色块、课表缩放与全览、交互动效打磨 |

每个版本的安装包、源码包与更新报告见 `versions/`，变更明细见 [`docs/CHANGELOG.md`](docs/CHANGELOG.md)。
