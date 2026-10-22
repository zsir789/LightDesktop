# 轻桌面 LightDesk v1.2.1 更新报告

- 版本：`1.2.1`（versionCode 4）
- 日期：2026-10-22
- 类型：小版本（缺陷修复）
- 基线：v1.2.0（versionCode 3）
- 仓库：<https://github.com/zsir789/LightDesktop>

---

## 一、需求与完成情况

| # | 用户反馈 | 处理结果 |
| --- | --- | --- |
| 1 | 课表页面缩放时横向不会缩放，只有竖直方向缩放 | 已修复：列宽、时间轴宽度、字号、内边距与行高使用同一个缩放比例；捏合手势与「＋ / － / 全览」同时作用于横纵两个方向 |
| 2 | 热力图中色彩区分不明显 | 已修复：热力格改为 5 档顺序色阶，高低档之间明度差明显；新增描边、档位图例文字与说明文案 |

## 二、改动明细

### 1. 课表横向缩放（`:feature:timetable`）

- `TimetableZoom.kt` 新增横向尺寸纯函数与常量：
  - `BASE_DAY_COLUMN_WIDTH_DP = 96f`：7 列日列基准宽度；
  - `BASE_TIME_AXIS_WIDTH_DP = 48f`、`MIN_TIME_AXIS_WIDTH_DP = 36f`：时间轴随缩放变化但保留可读下限；
  - `MIN_TEXT_SCALE = 0.82f`、`MAX_TEXT_SCALE = 1.5f`：字号缩放上下限；
  - `dayColumnWidthDp(scale)`、`timeAxisWidthDp(scale)`、`textScale(scale)`。
- `TimetableScreen.kt`：`WeeklyGrid` 根据 `scale` 计算日列宽度、时间轴宽度与字号比例，并传给
  `DayHeader` / `TimeAxis` / `DayColumn` / `CourseCard`；新增私有 `TextStyle.scaled(factor)` 仅复制
  `fontSize` / `lineHeight`（字重、字族仍由主题控制）；课程块内边距与最小高度同步缩放。
- 缩放后的布局仍位于原有的 `verticalScroll + horizontalScroll` 容器内，双向滚动与捏合缩放行为不变。

### 2. 热力图色阶（`core:designsystem` + `:feature:stats`）

- 新增 `core:designsystem/theme/HeatmapColors.kt`：
  - 0 级 = 主题的 `surfaceVariant`（浅淡底色，与卡片表面区分）；
  - 1–4 级 = 以「表面色 → 主色」顺序插值，浅色模式混合比例 `0.24 / 0.46 / 0.70 / 1.0`；
  - 深色模式单独使用 `0.32 / 0.54 / 0.76 / 1.0`，避免深色表面上层级被吞掉；
  - 纯函数，传入 `LightDeskPalette` 与 `dark`，便于单测与后续换肤。
- 主题新增 `LocalLightDeskDarkTheme`，让非 Composable 取色逻辑也能拿到深浅色状态。
- `StatsScreen`：热力格使用新色阶并加 0.6dp 描边；图例改为「少 / 5 档色块+档位文字 / 多」，档位文字
  `0 / 1-2 / 3-5 / 6-9 / 10+`，并补充「颜色越深，当天活动越多」说明。
- `HeatmapConfig` 移除已废弃的 `levelAlphas`，改为 `levelLegendLabels`。

### 3. 版本与文档

- `versionCode 4`、`versionName "1.2.1"`。
- 更新 `docs/CHANGELOG.md`、`README.md`、`docs/CONTRACTS.md`（`TimetableZoom` 新增 API、`HeatmapColors`、
  `LocalLightDeskDarkTheme`）。
- 新增本报告，并归档到 `versions/v1.2.1/`。

## 三、测试

- 新增/更新单元测试：
  - `TimetableZoomTest`：日列宽随缩放放大、时间轴宽度夹紧到下限、字号夹紧、横向缩放单调性（新增 4 项，合计 19 项）；
  - `HeatmapColorsTest`：6 套配色 × 深浅色下的档位明度单调递增且最小差 ≥ 0.02、越界夹紧（3 项）；
  - `StatsPresentationTest`：热力图图例档位与新配置一致。
- 全量 JVM 单元测试：**223 项通过，0 失败 0 错误**。
- `:app:assembleDebug`、`:app:assembleRelease` 构建成功。

## 四、发布产物

| 产物 | 路径 |
| --- | --- |
| 安装包（推荐） | `release/LightDesk-v1.2.1.apk`（同时归档 `versions/v1.2.1/`） |
| 源码包 | `release/LightDesk-source-v1.2.1.zip` |
| 校验值 | `versions/v1.2.1/SHA256SUMS.txt`、`versions/SHA256SUMS.txt` |

具体大小与 SHA-256 见 `versions/v1.2.1/SHA256SUMS.txt`。

## 五、兼容性与升级

- 数据格式无变化，可直接覆盖安装 v1.0.0 / v1.1.0 / v1.2.0，备忘、课表、统计与设置全部保留；
- 签名与历史版本一致（`keystore/lightdesk-release.jks`），无需卸载重装；
- 课表缩放档位仍为页面内即时状态，退出重进回到 1.0x（与 v1.2.0 行为一致）。

## 六、已知限制

- 缩放后若放大较多，需要横向滚动查看周内全部日期；「全览」会缩到 0.6x 并回到顶部；
- 热力图色阶基于主题主色生成，不同配色下最高档颜色不同，但 5 档相对深浅关系一致；
- 本次仍以编译 + JVM 单测验证为主，未在真机/模拟器做完整 UI 走查。
