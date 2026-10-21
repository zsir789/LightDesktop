# 轻桌面 v1.0.0 更新报告（首个版本）

- 发布日期：2026-10-19
- versionCode / versionName：`1` / `1.0.0`
- applicationId：`com.lightdesk.app`
- 安装包：`LightDesk-v1.0.0.apk`（11,695,270 字节，v2 签名）

## 一、本次交付内容

v1.0.0 是轻桌面的首个可安装版本，一次性交付六大功能：

| 功能 | 说明 |
| --- | --- |
| 备忘录 | 增删改查、完成标记、置顶、关键字搜索与状态筛选，数据写入 `memos.json` |
| 课表 | 读取 WakeUp 导出的 `.ics` 文件（TZID / UTC / 浮动时间 / VALUE=DATE、RRULE、EXDATE），周视图网格展示、课程详情、清空重导 |
| 天气 | Open-Meteo 联网获取实时天气与未来 7 天预报；断网/超时/解析失败降级为本地缓存并标注更新时间 |
| 交互统计 | 记录备忘录 / 课表 / 天气 / 悬浮球 / 待办的使用次数与最近使用时间，本地保存、可重置 |
| 悬浮球 | 可拖动的系统悬浮按钮，边缘吸附、点击唤起主程序，支持开关与开机恢复 |
| 待办倒计时 | 添加待办并设置截止时间，列表内实时倒计时，临近截止按设置阈值高亮 |

另含设置页（悬浮球开关、天气城市、倒计时提醒阈值、主题模式）与深浅色主题。

## 二、安装与使用

1. 安装 `LightDesk-v1.0.0.apk`（Android 8.0 / API 26 及以上，通用架构）；
2. 首次使用课表：WakeUp → 课表 → 导出 `.ics` → 轻桌面「课表」页导入；
3. 首次使用悬浮球：设置 → 桌面悬浮球 → 授权「显示在其他应用上层」。

详细说明见同目录 `README.md` 快照。

## 三、技术架构

- 多模块：`:app` + `:core:{common,model,data,designsystem}` + `:feature:{memo,timetable,weather,todo,stats,floatingball}`；
- 单 Activity + Navigation Compose，MVVM + Repository，`AppContainer` 手动依赖注入；
- 本地持久化：自研 `JsonFileStore`（`.tmp` 原子替换、损坏文件备份为 `.corrupt` 并回退默认值、Mutex 串行化）；
- 全链路 `AppResult`/`ErrorCode` 错误建模，IO 与网络异常均转 UI 提示，不崩溃；
- 可测纯逻辑下沉：时间格式化、ICS 解析、WMO 映射、排序/紧急度、悬浮球几何。

## 四、质量情况

- JVM 单元测试 **98 个全部通过**（0 失败 0 错误）；
- `lintDebug` 0 error；
- 源码包解压后可独立执行 `assembleDebug`；
- 受开发环境限制，未在真机 / 模拟器做 UI 手测。

## 五、校验和

```text
69798c11178176c87e008d95f107b5612447356805cee90358da207047abe979  LightDesk-v1.0.0.apk
21150cc8e8cadce9892e68c639101f5a2e569267781aa8e80d3c7d25597de390  LightDesk-source-v1.0.0.zip
```

## 六、已知限制

- 课表只有周视图，没有“按天 / 按月”查看；
- 备忘录与待办是两个独立 Tab，统计维度只到“功能使用次数”；
- 悬浮球需要额外权限，部分国产 ROM 还需允许后台弹出界面 / 自启动；
- 天气来自 Open-Meteo（海外服务），弱网时依赖缓存降级。

以上问题在 v1.1.0 中处理，详见 `../v1.1.0/UPDATE-REPORT.md`。
