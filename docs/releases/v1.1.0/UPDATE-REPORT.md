# 轻桌面 v1.1.0 更新报告

- 版本号：`1.1.0`（versionCode 2）
- 发布日期：2026-10-19
- 基础版本：v1.0.0（versionCode 1）
- 安装包：`LightDesk-v1.1.0.apk`
- 源码包：`LightDesk-source-v1.1.0.zip`
- 校验值：同目录 `SHA256SUMS.txt`（汇总见 `versions/SHA256SUMS.txt`）

---

## 一、本次更新要点

v1.1.0 是一次以「使用体验」为核心的改版，围绕首屏、备忘/待办一体化、设置与统计合并、课表可用性展开，
并按你的要求**移除了悬浮球**、**每个版本单独归档安装包与更新报告**。

| # | 需求 | 完成情况 |
| --- | --- | --- |
| 1 | 删除悬浮球 | ✅ 移除 `feature:floatingball` 模块、前台服务、悬浮窗权限与开机自启接收器 |
| 2 | 备忘录与待办合并为一项，优化完成动画 | ✅ 合并为「备忘」页；完成动画改为勾选填充渐变 + 对勾 spring 回弹 + 卡片沉底淡化 |
| 3 | 统计页改为《设置与统计》，设置在上、统计在下，增加日历热力图 | ✅ 单页上下分区；统计区含近 12 周日历热力图（5 档强度、月份标注、近 7 天小计） |
| 4 | 统计备忘记录数/完成次数与各天气类型次数 | ✅ 新增备忘记录数、完成次数、完成率；天气按中文天气类型统计次数与占比 |
| 5 | 课表适配 WakeUp ICS、增加每月每天课程查看、修复字体覆盖 bug | ✅ 重写 ICS 解析器（学期起始自动推导等）；新增月视图；移除 `Type.kt` 中硬编码 `FontFamily.SansSerif` |
| 6 | 首屏改为首页，展示当日课表与待办 | ✅ 新增 `feature:home`，含问候、教学周、今日课程（进行中/下一节高亮）、今日待办实时倒计时 |
| 7 | 优化动画与交互 | ✅ 页面切换 fade+slide 转场、底部图标缩放与指示条、TagPill 视图切换、卡片状态过渡 |
| 8 | 保存每个版本的安装包及更新报告 | ✅ 新增 `versions/vX.Y.Z/` 归档（APK + 源码包 + 更新报告 + 校验值），已有 v1.0.0、v1.1.0 |

---

## 二、功能变化明细

### 新增
- **首页**：问候语、日期与教学周、今日课程时间轴、今日待办倒计时、快捷入口。
- **课表月视图**：周一起始网格、今天高亮、每格最多 2 条课程与「+N」、点选日期查看当天全部课程。
- **统计维度**：备忘记录数、备忘完成次数、完成率、各天气类型出现次数、每日活跃热力图。
- **数据迁移**：从 v1.0.0 升级时自动合并 `memos.json` + `todos.json` 为 `notes.json`，旧统计维度自动迁移。

### 调整
- 底部导航由 5 项改为「首页 / 备忘 / 课表 / 天气 / 设置与统计」。
- 备忘录与待办合并为统一事项模型 `NoteItem`：带截止时间即视为待办，支持置顶、筛选、搜索。
- 统计页更名为「设置与统计」，设置与统计同屏上下排布。
- 课表导入后自动推导学期第 1 周，周视图直接显示「第 N 周」。

### 移除
- 悬浮球（悬浮窗权限、前台服务、开机自启接收器一并移除）。
- `feature:memo`、`feature:todo`、`feature:floatingball` 三个旧模块。

---

## 三、技术改进

- **模块化**：`core:{common,model,data,designsystem}` + `feature:{home,notes,timetable,weather,stats}`，功能模块零耦合，`app` 统一组装。
- **解析器**：新增可扩展导入接口 `TimetableImporter`，`IcsParser` 支持行折叠、TZID/UTC/浮动时间、全天事件、RRULE（COUNT/UNTIL/BYDAY/INTERVAL/WKST）、EXDATE、多时段拆分、坏数据提示。
- **字体**：`core:designsystem/theme/Type.kt` 移除 9 处硬编码 `FontFamily.SansSerif`，统一交给系统默认中文字体，并为品牌字体预留集中替换点。
- **构建**：新增 `scripts/gw.ps1`（Gradle 串行互斥，避免并发构建损坏 Kotlin 增量缓存）与 `scripts/build-release.ps1`（单测 → 签名 APK → 源码包 → 版本归档 → 校验值）。
- **异常处理**：所有仓储调用返回 `AppResult`，UI 统一 ErrorBanner 提示；JSON 文件损坏自动备份 `.corrupt` 并回退默认值；天气请求超时/断网自动降级缓存。

---

## 四、质量验证

| 项目 | 结果 |
| --- | --- |
| JVM 单元测试 | **168 个用例，0 失败 0 错误**（19 个测试类） |
| Release 构建 | `:app:assembleRelease` 成功（lintVitalRelease 通过） |
| Debug 构建 | `:app:assembleDebug` 成功 |
| 编译告警 | 无 Kotlin 编译错误；仅工具链提示 |

测试分布：

| 模块 | 用例数 |
| --- | --- |
| core:common（时间格式化、日历工具） | 12 |
| core:model（统一事项、课表日历计算） | 10 |
| core:data（JSON 存储、仓储、迁移、统计聚合） | 10 |
| feature:home（首页展示与 ViewModel） | 20 |
| feature:notes（筛选排序、倒计时、编辑、完成动画逻辑） | 32 |
| feature:timetable（ICS 解析 27、周/月查询 11、周次文案 4） | 42 |
| feature:weather（天气码映射、URL 构建、响应映射、降级） | 22 |
| feature:stats（统计展示、热力图分级、设置动作） | 20 |

> 合计 168。所有测试通过 `scripts/build-release.ps1` 在打包前自动执行。

---

## 五、安装与升级

1. 安装 `release/LightDesk-v1.1.0.apk`（Android 8.0+，通用架构）；
2. 覆盖安装可直接升级，首次启动自动迁移 v1.0.0 数据；
3. 若从 v1.0.0 升级后统计为空，属正常情况（旧统计维度已迁移，热力图从新版本开始累计）；
4. 回退到旧版：卸载后安装 `versions/v1.0.0/LightDesk-v1.0.0.apk`（会丢失 v1.1.0 期间新增数据）。

签名信息：`keystore/lightdesk-release.jks`（CN=LightDesk），与 v1.0.0 相同，可直接覆盖安装。

---

## 六、已知限制

- 天气数据来自 Open-Meteo（海外服务），弱网可能较慢，已做超时与缓存降级；
- WakeUp 导出的非标准字段可能解析不全；非 WEEKLY 重复事件只取首次实例并给出提示；
- 月视图窄屏每格最多 2 条课程，其余折叠为「+N」；
- 本次交付经过编译与 JVM 单测验证，未在真机/模拟器上进行完整 UI 走查；
- 统计热力图按“有活动的天”计强度，升级前历史数据不可回溯补齐。

---

## 七、版本归档

| 版本 | 产物 | 说明 |
| --- | --- | --- |
| v1.0.0 | `versions/v1.0.0/` | 初版：备忘录、课表、天气、交互统计、悬浮球、待办倒计时 |
| v1.1.0 | `versions/v1.1.0/` | 本版本：首页、备忘待办合并、设置与统计合页、课表月视图、移除悬浮球 |

后续版本请用 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build-release.ps1 -Version <x.y.z>` 生成同名归档。
