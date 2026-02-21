# 更新日志 (CHANGELOG)

本文件记录项目各版本的重要更新与修复，便于在 GitHub 分支或发布页查看变更内容。

## [Unreleased]
- 尚未整理的改动请先记录在此

## [2.12.75] - 2026-02-20
### 修复
- 修复 Folia 服务端因 Bukkit Scheduler 不兼容导致插件启动失败的问题（`UnsupportedOperationException`）
- 新增 `SchedulerUtil` 调度工具类，通过反射自动识别 Folia/Bukkit 环境并选择对应的任务调度器
- `ItemDisplayCache` 缓存清理任务改用 `SchedulerUtil`，兼容 Folia 的 `AsyncScheduler`
- `ViewItemCommand` 物品展示界面改用 Folia 的 `EntityScheduler`（区域线程），确保跨区块/跨服玩家可正常打开展示界面
- 超时清理任务改用异步调度，兼容 Folia 异步任务机制

### 优化
- Web 端与 App 端内嵌完整 HarmonyOS Sans SC 字体（6 字重：Thin/Light/Regular/Medium/Bold/Black）

## [2.12.74] - 2026-02-12
### 新功能
- 服务器端消息缓存与重放：Web/App 用户上线后自动补发离线期间的公屏与私聊消息（增量存储 + 游标机制）
- 未读消息分割线：进入聊天界面时自动定位至上次阅读位置，方便快速查看未读消息
- 内嵌 HarmonyOS Sans 字体：Web 端与 App 端统一使用 HarmonyOS Sans 字体，无需设备本地安装

### 优化
- 消息去重：App 端多连接/重连场景下不再出现重复消息（基于 messageId 的客户端去重）
- 广播消息增加 messageId 与 time 字段，支持去重与正确时间戳显示
- 进入聊天界面时自动滚动至最新消息
- 功能图标自动渲染：修复首次打开页面时图标不显示的问题（MutationObserver 方案）
- [i] 物品展示日间模式弹窗可读性优化
- Velocity 控制台日志简化：[i] 物品展示不再输出大量调试信息

### 修复
- 修复 App 端 WebSocket 连接 1006 错误，改进连接策略与重试机制
- 修复消息重放时时间戳显示为连接时间而非实际发送时间的问题
- 修复未读分割线在重连后不能正确定位至上次阅读位置的问题

## [2.12.73] - 2026-02-13
### 新功能
- 增加了 Web 端物品展示功能，现在可以在游戏内向玩家和 Web 端玩家展示物品

### 修复
- 修正服务器反代环境下 App 无法连接服务器的问题，现在可以正常连接

## [2.12.72] - 2026-01-20
### 新功能
- Web/App 指令输入新增快捷选人面板（显示在线状态并自动补全）

### 优化
- 移动端玩家列表宽度与手势交互优化（任意位置滑动触发）
- 物品展示 [i] 的悬停改为原版物品展示格式（Velocity 端）
- /yinwuchat 帮助与 README 指令说明整理

## [2.12.71] - 2026-01-20
### 新功能
- 支持 Web 端与 APP 同时使用同一账号登录，消息实时同步（多端同时在线）
- 头像改用 `/helm/` 端点，显示双层皮肤（底层+头盔层）
- 新增 `/chatunban` 解封指令，支持解除 `/chatban` 的封禁
- 管理员快捷指令新增 `/chatunban`

### 优化
- 绑定成功消息只在游戏内 `bind` 指令成功时发送，Web 端连接时不再重复提示
- Web 端违禁词过多改为封禁1小时（游戏端仍为踢出服务器）

### English Summary
- Multi-device login: Web and APP can now login simultaneously with same account
- Avatar uses `/helm/` endpoint for dual-layer skin display
- Added `/chatunban` command to unban players
- Bind success message only sent on in-game bind command
- Web excessive shielded words now results in 1-hour ban instead of kick

## [2.12.70]
- web端UI 优化：移除私聊窗口顶部头像图标，精简界面
- Web 端侧边栏交互优化：支持手机端左侧侧滑呼出/关闭玩家列表，实现平滑跟随效果
- 修复 Android App 手势兼容性
- 优化物品展示功能[i]：实现点击[i]后展示物品的功能
- 优化权限组，支持lp等插件整理命令权限

## [2.12.69]
- Web 端隐身功能上线，支持独立 `/vanish` 指令
- 优化 Web 端指令权限控制与白名单机制
- 更新 Web 端系统头像
- 完善忽略（Ignore）逻辑
- 支持web端 `/ignore` 和 `/noat` 指令

## [2.12.68]
- 上线 APP debug 版本，支持安卓端

## [2.12.67]
- 更新网页端公共聊天室消息显示兼容处理
- 完善 WebSocket 消息解析与颜色码渲染
- 实现 Web 端指令集成

## [2.12.66]
- 新增velocity端禁言、隐身、重加载插件功能
- 支持定时禁言、永久禁言、禁言原因
- 新增独立命令 `/mute`、`/unmute`、`/muteinfo`
- 优化 `[p]` 位置显示功能
- 修复自定义前后缀与占位符冲突问题
- 服内聊天功能全部上线

## [2.12.65]
- 加入 Folia 支持（测试中），Bukkit/Paper 测试正常

## [2.12.64]
- 前后缀编辑上线，优化编辑功能
- 支持编辑、删除、快捷指令导入
- 加入私聊前后缀

## [2.12.63]
- Redis 依赖转向 Velocity
- `@` 与 `noat` 功能上线

## [2.12.62]
- 修复玩家名称 hover 占位符与 `/msg` 无法调用玩家名称的问题
- 基本完成 YinwuChat 跨服通讯功能搭建

## [2.12.61]
- 消除上版本存在的消息重复发送问题

## [2.12.60]
- 更新版本号配置
- 解决私聊格式问题
