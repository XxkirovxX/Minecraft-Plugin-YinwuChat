package org.lintx.plugins.yinwuchat.velocity.httpserver;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.lintx.plugins.yinwuchat.velocity.YinwuChat;
import org.lintx.plugins.yinwuchat.velocity.config.Config;
import org.lintx.plugins.yinwuchat.velocity.json.*;
import org.lintx.plugins.yinwuchat.velocity.message.MessageManage;
import org.lintx.plugins.yinwuchat.velocity.config.PlayerConfig;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * WebSocket frame handler for Velocity
 * Processes incoming WebSocket frames from web clients and AQQBot (OneBot标准)
 * 支持 AQQBot、Lagrange、LLoneBot 等基于 OneBot 标准的 QQ 机器人框架
 */
public class VelocityWebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    private final YinwuChat plugin;
    
    public VelocityWebSocketFrameHandler(YinwuChat plugin) {
        this.plugin = plugin;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof TextWebSocketFrame) {
            String request = ((TextWebSocketFrame) frame).text();
            Channel channel = ctx.channel();
            
            // Handle WebSocket message
            plugin.getLogger().debug("WebSocket message received: " + request);
            
            // 异步处理消息
            plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                InputBase object = InputBase.getObject(request);
                
                if (object instanceof InputCheckToken) {
                    // Token 验证
                    handleTokenCheck(channel, (InputCheckToken) object);
                } else if (object instanceof InputMessage) {
                    // 消息处理
                    handleMessage(channel, (InputMessage) object);
                } else if (object instanceof InputCommand) {
                    // 命令处理
                    handleCommandAction(channel, (InputCommand) object);
                } else if (object instanceof InputPrivateMessage) {
                    // 私聊消息处理
                    handlePrivateMessageAction(channel, (InputPrivateMessage) object);
                } else if (object instanceof InputAQQBot) {
                    // AQQBot (OneBot标准) 消息
                    handleAQQBotMessage(request);
                }
            }).schedule();
        }
    }
    
    /**
     * 处理 Token 验证
     */
    private void handleTokenCheck(Channel channel, InputCheckToken tokenCheck) {
        VelocityWsClientUtil clientUtil = new VelocityWsClientUtil(tokenCheck.getToken());
        VelocityWsClientHelper.add(channel, clientUtil);
        
        NettyChannelMessageHelper.send(channel, tokenCheck.getJSON());
        
        if (!tokenCheck.getIsvaild()) {
            NettyChannelMessageHelper.send(channel, tokenCheck.getTokenJSON());
        } else {
            if (tokenCheck.getIsbind()) {
                clientUtil.setUUID(tokenCheck.getUuid());
                VelocityWsClientHelper.kickOtherWS(channel, tokenCheck.getUuid());
                sendSelfInfo(channel, tokenCheck.getUuid());
                // 发送离线消息
                String playerName = PlayerConfig.getInstance().getTokenManager().getName(tokenCheck.getUuid());
                if (playerName != null && !playerName.isEmpty()) {
                    org.lintx.plugins.yinwuchat.velocity.message.MessageManage.getInstance()
                        .deliverOfflineMessagesToWeb(channel, playerName);
                }
                
                // 发送玩家列表
                sendPlayerList(channel);
            }
        }
    }
    
    /**
     * 处理消息发送
     */
    private void handleMessage(Channel channel, InputMessage inputMessage) {
        if (inputMessage.getMessage().isEmpty()) {
            return;
        }
        
        VelocityWsClientUtil util = VelocityWsClientHelper.get(channel);
        if (util == null || util.getUuid() == null) {
            NettyChannelMessageHelper.send(channel, OutputServerMessage.errorJSON("请先绑定账号").getJSON());
            return;
        }
        
        // 检查冷却时间
        Config config = plugin.getConfig();
        if (!util.getLastDate().isEqual(LocalDateTime.MIN)) {
            Duration duration = Duration.between(util.getLastDate(), LocalDateTime.now());
            if (duration.toMillis() < config.wsCooldown) {
                NettyChannelMessageHelper.send(channel, 
                    OutputServerMessage.errorJSON("发送消息过快（当前设置为每条消息之间最少间隔" + config.wsCooldown + "毫秒）").getJSON());
                return;
            }
        }
        util.updateLastDate();
        
        String message = inputMessage.getMessage();
        
        // 处理命令
        if (message.startsWith("/")) {
            handleCommand(channel, util, message);
            return;
        }
        
        // 发送公屏消息
        MessageManage.getInstance().handleWebPublicMessage(util.getUuid(), message, channel);
    }

    /**
     * 处理命令动作
     */
    private void handleCommandAction(Channel channel, InputCommand inputCommand) {
        VelocityWsClientUtil util = VelocityWsClientHelper.get(channel);
        if (util == null || util.getUuid() == null) {
            NettyChannelMessageHelper.send(channel, OutputServerMessage.errorJSON("请先绑定账号").getJSON());
            return;
        }

        String fullCommand = inputCommand.getCommand();
        if (fullCommand.isEmpty()) return;

        // 统一调用 handleCommand 逻辑
        handleCommand(channel, util, "/" + fullCommand);
    }

    /**
     * 处理私聊消息动作
     */
    private void handlePrivateMessageAction(Channel channel, InputPrivateMessage inputPrivateMessage) {
        VelocityWsClientUtil util = VelocityWsClientHelper.get(channel);
        if (util == null || util.getUuid() == null) {
            NettyChannelMessageHelper.send(channel, OutputServerMessage.errorJSON("请先绑定账号").getJSON());
            return;
        }

        if (inputPrivateMessage.getMessage().isEmpty() || inputPrivateMessage.getTo().isEmpty()) {
            return;
        }

        MessageManage.getInstance().handleWebPrivateMessage(channel, util, inputPrivateMessage.getTo(), inputPrivateMessage.getMessage());
    }
    
    /**
     * 处理命令
     */
    private void handleCommand(Channel channel, VelocityWsClientUtil util, String message) {
        String[] command = message.replaceFirst("^/", "").split("\\s");
        String label = command[0].toLowerCase();
        
        if (command.length >= 3 && (label.equals("msg") || label.equals("tell") || label.equals("whisper") || label.equals("w") || label.equals("t"))) {
            String toPlayerName = command[1];
            List<String> tmpList = new ArrayList<>(Arrays.asList(command).subList(2, command.length));
            String msg = String.join(" ", tmpList);
            
            MessageManage.getInstance().handleWebPrivateMessage(channel, util, toPlayerName, msg);
        } else if (label.equals("yinwuchat") || label.equals("ignore") || label.equals("noat") || label.equals("vanish") || label.equals("mute") || label.equals("unmute") || label.equals("muteinfo")) {
            // 对于插件自身指令，无论玩家是否在线都允许执行
            String[] args;
            if (label.equals("yinwuchat")) {
                args = command.length > 1 ? Arrays.copyOfRange(command, 1, command.length) : new String[]{"ws"};
            } else {
                args = command;
            }
            handleWebPlayerCommand(channel, util, args);
        } else {
            // 其他指令必须在游戏内在线才能执行
            plugin.getProxy().getPlayer(util.getUuid()).ifPresentOrElse(player -> {
                plugin.getProxy().getCommandManager().executeAsync(player, message.substring(1));
            }, () -> {
                NettyChannelMessageHelper.send(channel, OutputServerMessage.errorJSON("✗ 你必须在游戏内在线才能执行此指令").getJSON());
            });
        }
    }

    private static boolean isAdmin(YinwuChat plugin, String playerName) {
        if (playerName == null) return false;
        java.util.Optional<com.velocitypowered.api.proxy.Player> player = plugin.getProxy().getPlayer(playerName);
        if (player.isPresent()) {
            return plugin.getConfig().isAdmin(player.get());
        }
        return plugin.getConfig().isAdmin(playerName);
    }

    /**
     * 处理 Web 玩家的特殊命令（无需在游戏内在线）
     */
    private void handleWebPlayerCommand(Channel channel, VelocityWsClientUtil util, String[] args) {
        UUID uuid = util.getUuid();
        String playerName = PlayerConfig.getInstance().getTokenManager().getName(uuid);
        if (playerName == null || playerName.isEmpty()) {
            NettyChannelMessageHelper.send(channel, OutputServerMessage.errorJSON("无法获取玩家名称").getJSON());
            return;
        }

        String cmd = args[0].toLowerCase();
        PlayerConfig playerConfig = PlayerConfig.getInstance();
        PlayerConfig.PlayerSettings settings = playerConfig.getSettings(playerName);
        boolean isAdmin = isAdmin(plugin, playerName);

        if (cmd.equals("ws")) {
            if (!isAdmin && !plugin.getProxy().getPlayer(uuid).map(p -> p.hasPermission(org.lintx.plugins.yinwuchat.Const.PERMISSION_WS)).orElse(false)) {
                NettyChannelMessageHelper.send(channel, OutputServerMessage.errorJSON("✗ 权限不足").getJSON());
                return;
            }
            String host = "服务器IP或域名";
            try {
                java.net.InetSocketAddress address = plugin.getProxy().getBoundAddress();
                if (address != null && address.getHostString() != null) {
                    host = address.getHostString();
                }
            } catch (Exception ignored) {
            }
            if ("0.0.0.0".equals(host) || "::".equals(host)) {
                host = "服务器IP或域名";
            }
            int port = plugin.getConfig().wsport;
            String wsUrl = "ws://" + host + ":" + port + "/ws";
            NettyChannelMessageHelper.send(channel, OutputServerMessage.infoJSON("WebSocket 地址: " + wsUrl + "\n如果网页使用 HTTPS，请将 ws:// 改为 wss://").getJSON());
        } else if (cmd.equals("ignore")) {
            if (!isAdmin && !plugin.getProxy().getPlayer(uuid).map(p -> p.hasPermission(org.lintx.plugins.yinwuchat.Const.PERMISSION_IGNORE)).orElse(false)) {
                NettyChannelMessageHelper.send(channel, OutputServerMessage.errorJSON("✗ 权限不足").getJSON());
                return;
            }
            if (args.length < 2) {
                NettyChannelMessageHelper.send(channel, OutputServerMessage.errorJSON("用法: /ignore <玩家名>").getJSON());
                return;
            }
            String targetName = args[1];
            if (targetName.equalsIgnoreCase(playerName)) {
                NettyChannelMessageHelper.send(channel, OutputServerMessage.errorJSON("你不能忽略你自己").getJSON());
                return;
            }
            if (settings.isIgnored(targetName)) {
                settings.unignorePlayer(targetName);
                playerConfig.saveSettings(settings);
                NettyChannelMessageHelper.send(channel, OutputServerMessage.infoJSON("✓ 已取消忽略 " + targetName).getJSON());
            } else {
                settings.ignorePlayer(targetName);
                playerConfig.saveSettings(settings);
                NettyChannelMessageHelper.send(channel, OutputServerMessage.infoJSON("✓ 已忽略 " + targetName).getJSON());
            }
        } else if (cmd.equals("noat")) {
            if (!isAdmin && !plugin.getProxy().getPlayer(uuid).map(p -> p.hasPermission(org.lintx.plugins.yinwuchat.Const.PERMISSION_NOAT)).orElse(false)) {
                NettyChannelMessageHelper.send(channel, OutputServerMessage.errorJSON("✗ 权限不足").getJSON());
                return;
            }
            settings.disableAtMention = !settings.disableAtMention;
            playerConfig.saveSettings(settings);
            if (settings.disableAtMention) {
                NettyChannelMessageHelper.send(channel, OutputServerMessage.infoJSON("✓ 已禁止其他玩家@你").getJSON());
            } else {
                NettyChannelMessageHelper.send(channel, OutputServerMessage.infoJSON("✓ 已允许其他玩家@你").getJSON());
            }
        } else if (cmd.equals("vanish")) {
            if (!isAdmin) {
                NettyChannelMessageHelper.send(channel, OutputServerMessage.errorJSON("✗ 权限不足").getJSON());
                return;
            }
            settings.vanished = !settings.vanished;
            playerConfig.saveSettings(settings);
            if (settings.vanished) {
                NettyChannelMessageHelper.send(channel, OutputServerMessage.infoJSON("✓ 已开启隐身模式").getJSON());
            } else {
                NettyChannelMessageHelper.send(channel, OutputServerMessage.infoJSON("✓ 已关闭隐身模式").getJSON());
            }
            // 更新客户端的 self_info，包含新的隐身状态
            sendSelfInfo(channel, uuid);
            // 广播更新后的玩家列表给所有人
            broadcastPlayerList(plugin);
        } else if (cmd.equals("mute") || cmd.equals("unmute") || cmd.equals("muteinfo")) {
            if (!isAdmin) {
                NettyChannelMessageHelper.send(channel, OutputServerMessage.errorJSON("✗ 权限不足").getJSON());
                return;
            }
            // 转发给 MessageManage 处理禁言逻辑
            String fullCmd = String.join(" ", args);
            MessageManage.getInstance().handleWebPlayerMuteCommand(channel, playerName, fullCmd);
        } else {
            NettyChannelMessageHelper.send(channel, OutputServerMessage.infoJSON("可用指令: /yinwuchat [ws|ignore|noat|vanish|mute|unmute|muteinfo]").getJSON());
        }
    }
    
    /**
     * 向所有已连接的客户端广播最新的玩家列表
     */
    public static void broadcastPlayerList(YinwuChat plugin) {
        for (io.netty.channel.Channel channel : VelocityWsClientHelper.getChannels()) {
            VelocityWsClientUtil util = VelocityWsClientHelper.get(channel);
            if (util != null && util.getUuid() != null) {
                // 只有已登录（绑定）的 Web 客户端才发送列表
                sendPlayerList(channel, plugin);
            }
        }
    }

    /**
     * 发送玩家列表给特定通道
     */
    private void sendPlayerList(Channel channel) {
        sendPlayerList(channel, plugin);
    }

    private static void sendPlayerList(Channel channel, YinwuChat plugin) {
        VelocityWsClientUtil util = VelocityWsClientHelper.get(channel);
        if (util == null || util.getUuid() == null) {
            return;
        }
        String webPlayerName = PlayerConfig.getInstance().getTokenManager().getName(util.getUuid());
        if (webPlayerName == null || webPlayerName.isEmpty()) {
            return;
        }
        PlayerConfig.PlayerSettings webSettings = PlayerConfig.getInstance().getSettings(webPlayerName);
        if (webSettings.vanished) {
            return;
        }
        sendPlayerStatusList(channel, plugin, webSettings);
    }

    private static void sendPlayerStatusList(Channel channel, YinwuChat plugin, PlayerConfig.PlayerSettings webSettings) {
        com.google.gson.JsonArray players = new com.google.gson.JsonArray();
        java.util.Map<String, String> gameServers = new java.util.HashMap<>();
        java.util.Set<String> gameNames = new java.util.HashSet<>();
        java.util.Set<String> webNames = new java.util.HashSet<>();
        java.util.Map<String, Integer> offlineCounts = new java.util.HashMap<>();
        boolean isAdmin = isAdmin(plugin, webSettings.playerName);

        for (com.velocitypowered.api.proxy.Player player : plugin.getProxy().getAllPlayers()) {
            PlayerConfig.PlayerSettings ps = PlayerConfig.getInstance().getSettings(player.getUsername());
            // 如果玩家隐身且查看者不是管理员，则该玩家不计入游戏在线
            if (ps.vanished && !isAdmin) continue;
            if (webSettings.isIgnored(player.getUsername())) continue;
            String name = player.getUsername();
            gameNames.add(name);
            player.getCurrentServer().ifPresent(server -> gameServers.put(name, server.getServerInfo().getName()));
        }

        for (io.netty.channel.Channel wsChannel : VelocityWsClientHelper.getChannels()) {
            VelocityWsClientUtil wsUtil = VelocityWsClientHelper.get(wsChannel);
            if (wsUtil == null || wsUtil.getUuid() == null) continue;
            String name = PlayerConfig.getInstance().getTokenManager().getName(wsUtil.getUuid());
            if (name == null || name.isEmpty()) continue;
            if (webSettings.isIgnored(name)) continue;
            
            PlayerConfig.PlayerSettings ps = PlayerConfig.getInstance().getSettings(name);
            // 如果玩家隐身且查看者不是管理员，则该玩家不计入 Web 在线
            if (ps.vanished && !isAdmin) continue;
            
            webNames.add(name);
        }

        java.util.Set<String> allNames = new java.util.HashSet<>();
        allNames.addAll(PlayerConfig.getInstance().getTokenManager().getAllNames());
        allNames.addAll(gameNames);
        allNames.addAll(webNames);

        java.util.List<String> sorted = new java.util.ArrayList<>(allNames);
        java.util.Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);

        String viewerName = webSettings.playerName;
        org.lintx.plugins.yinwuchat.common.message.OfflineMessageStore store =
            new org.lintx.plugins.yinwuchat.common.message.OfflineMessageStore(plugin.getDataFolder().toFile());
        if (viewerName != null && !viewerName.isEmpty()) {
            offlineCounts = store.getCounts(viewerName);
        }

        for (String name : sorted) {
            if (name == null || name.isEmpty()) continue;
            PlayerConfig.PlayerSettings ps = PlayerConfig.getInstance().getSettings(name);
            // 忽略列表中被忽略的玩家（除非是管理员查看）
            if (webSettings.isIgnored(name) && !isAdmin) continue;
            
            // 状态逻辑：如果玩家隐身且查看者不是管理员，强制显示为 offline
            String status;
            if (ps.vanished && !isAdmin) {
                status = "offline";
            } else {
                status = gameNames.contains(name) ? "game" : (webNames.contains(name) ? "web" : "offline");
            }
            
            com.google.gson.JsonObject playerJson = new com.google.gson.JsonObject();
            playerJson.addProperty("name", name);
            playerJson.addProperty("status", status);
            if ("game".equals(status)) {
                playerJson.addProperty("server", gameServers.getOrDefault(name, ""));
            }
            Integer count = offlineCounts.get(name.toLowerCase());
            if (count != null && count > 0) {
                playerJson.addProperty("offlineCount", count);
            }
            players.add(playerJson);
        }

        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("action", "player_status_list");
        json.add("players", players);
        NettyChannelMessageHelper.send(channel, json.toString());
    }

    /**
     * 处理 AQQBot (OneBot标准) 消息
     * @param jsonMessage JSON格式的消息
     */
    private void handleAQQBotMessage(String jsonMessage) {
        try {
            Config config = plugin.getConfig();
            InputAQQBot inputAQQBot = org.lintx.plugins.yinwuchat.Util.Gson.gson()
                .fromJson(jsonMessage, InputAQQBot.class);
            
            if (inputAQQBot == null) {
                return;
            }

            // 检查是否是群消息
            if ("message".equalsIgnoreCase(inputAQQBot.getPost_type()) 
                && "group".equalsIgnoreCase(inputAQQBot.getMessage_type())
                && "normal".equalsIgnoreCase(inputAQQBot.getSub_type())) {
                
                // 获取配置的群号（优先使用 AQQBot 配置）
                long targetGroupId;
                if (config.aqqBotConfig != null && config.aqqBotConfig.qqGroup > 0) {
                    targetGroupId = config.aqqBotConfig.qqGroup;
                } else if (config.coolQConfig != null && config.coolQConfig.coolQGroup > 0) {
                    targetGroupId = config.coolQConfig.coolQGroup;
                } else {
                    return; // 未配置群号
                }

                // 检查群号是否匹配
                if (inputAQQBot.getGroup_id() != targetGroupId) {
                    return;
                }

                // 检查是否需要特定的消息前缀
                String qqToGameStart = config.aqqBotConfig != null 
                    ? config.aqqBotConfig.qqToGameStart 
                    : (config.coolQConfig != null ? config.coolQConfig.coolqToGameStart : "");
                
                if (qqToGameStart != null && !qqToGameStart.isEmpty()) {
                    String message = inputAQQBot.getRaw_message();
                    if (message == null || !message.startsWith(qqToGameStart)) {
                        return;
                    }
                }

                // TODO: 处理QQ消息转发到游戏
                // MessageManage.getInstance().handleQQMessage(inputAQQBot);
            }
        } catch (Exception e) {
            plugin.getLogger().warn("[WebSocket] QQ消息解析失败: " + e.getMessage());
        }
    }

    private void sendSelfInfo(Channel channel, java.util.UUID uuid) {
        if (uuid == null) {
            return;
        }
        String name = plugin.getProxy().getPlayer(uuid)
            .map(com.velocitypowered.api.proxy.Player::getUsername)
            .orElse("");
        boolean isAdmin = false;
        
        if (!name.isEmpty()) {
            isAdmin = isAdmin(plugin, name);
        } else {
            name = org.lintx.plugins.yinwuchat.velocity.config.PlayerConfig
                .getInstance()
                .getTokenManager()
                .getName(uuid);
            if (name == null) {
                name = "";
            } else {
                isAdmin = isAdmin(plugin, name);
            }
        }
        
        if (name.isEmpty()) {
            return;
        }
        
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("action", "self_info");
        json.addProperty("player", name);
        json.addProperty("isAdmin", isAdmin);
        json.addProperty("vanished", PlayerConfig.getInstance().getSettings(name).vanished);
        NettyChannelMessageHelper.send(channel, json.toString());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            WebSocketServerProtocolHandler.HandshakeComplete complete = 
                (WebSocketServerProtocolHandler.HandshakeComplete) evt;
            HttpHeaders headers = complete.requestHeaders();

            String qq = headers.get("X-Self-ID");
            String role = headers.get("X-Client-Role");
            String authorization = headers.get("Authorization");

            // 识别 AQQBot (OneBot标准) 连接
            // AQQBot 通常使用 X-Self-ID 和 X-Client-Role 头来标识
            // 支持 "Universal" (通用客户端) 和 "Event" (事件上报) 等角色
            if (qq != null && !qq.isEmpty() && ("Universal".equals(role) || "Event".equals(role))) {
                Config config = plugin.getConfig();
                
                // 优先使用 AQQBot 配置，向后兼容 CoolQ 配置
                String token = null;
                if (config.aqqBotConfig != null) {
                    token = config.aqqBotConfig.accessToken;
                } else if (config.coolQConfig != null) {
                    token = config.coolQConfig.coolQAccessToken;
                }
                
                // 验证访问令牌
                if (token != null && !token.isEmpty()) {
                    String expectedAuth = "Token " + token;
                    if (!expectedAuth.equals(authorization)) {
                        plugin.getLogger().warn("[WebSocket] QQ机器人连接被拒绝: Token无效");
                        ctx.close();
                        return;
                    }
                }
                
                // 注册 AQQBot 连接
                VelocityWsClientHelper.updateAQQBot(ctx.channel());
                plugin.getLogger().info("[WebSocket] QQ机器人已连接 (QQ: " + qq + ")");
            } else {
                // 普通Web客户端连接
                VelocityWsClientHelper.add(ctx.channel());
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        
        // 检查是否是 AQQBot 连接断开
        Channel aqqBotChannel = VelocityWsClientHelper.getAQQBot();
        if (aqqBotChannel != null && aqqBotChannel == ctx.channel()) {
            VelocityWsClientHelper.updateAQQBot(null);
            plugin.getLogger().info("[WebSocket] QQ机器人连接已断开");
        } else {
            VelocityWsClientHelper.remove(ctx.channel());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        plugin.getLogger().warn("[WebSocket] 错误: " + cause.getMessage());
        ctx.close();
    }
}
