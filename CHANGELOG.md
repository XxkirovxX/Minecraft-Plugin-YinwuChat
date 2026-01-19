# 更新日志 (CHANGELOG)

本文件记录项目各版本的重要更新与修复，便于在 GitHub 分支或发布页查看变更内容。

## [Unreleased]
- 尚未整理的改动请先记录在此

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
