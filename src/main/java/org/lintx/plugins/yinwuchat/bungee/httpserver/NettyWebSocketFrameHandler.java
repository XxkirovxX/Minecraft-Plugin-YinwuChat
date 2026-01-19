package org.lintx.plugins.yinwuchat.bungee.httpserver;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.lintx.plugins.yinwuchat.Const;
import org.lintx.plugins.yinwuchat.bungee.config.Config;
import org.lintx.plugins.yinwuchat.bungee.MessageManage;
import org.lintx.plugins.yinwuchat.bungee.YinwuChat;
import org.lintx.plugins.yinwuchat.bungee.json.*;
import org.lintx.plugins.yinwuchat.bungee.config.PlayerConfig;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class NettyWebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    private final YinwuChat plugin;

    NettyWebSocketFrameHandler(YinwuChat plugin){
        this.plugin = plugin;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof TextWebSocketFrame) {
            String request = ((TextWebSocketFrame) frame).text();

            Channel channel = ctx.channel();
            plugin.getProxy().getScheduler().runAsync(plugin, () -> {
                InputBase object = InputBase.getObject(request);
                if (object instanceof InputCheckToken) {
                    InputCheckToken o = (InputCheckToken)object;

                    WsClientUtil clientUtil = new WsClientUtil(o.getToken());
                    WsClientHelper.add(channel, clientUtil);

                    NettyChannelMessageHelper.send(channel,o.getJSON());
                    if (!o.getIsvaild()) {
                        NettyChannelMessageHelper.send(channel,o.getTokenJSON());
                    }
                    else{
                        if (o.getIsbind()) {
                            clientUtil.setUUID(o.getUuid());
                            WsClientHelper.kickOtherWS(channel, o.getUuid());

                            sendSelfInfo(channel, o.getUuid());
                            OutputPlayerList.sendGamePlayerList(channel);
                            OutputPlayerList.sendWebPlayerList();
//                            String player_name = PlayerConfig.getConfig(o.getUuid()).name;
//                        YinwuChat.getWSServer().broadcast((new PlayerStatusJSON(player_name,PlayerStatusJSON.PlayerStatus.WEB_JOIN)).getWebStatusJSON());
//                        plugin.getProxy().broadcast(ChatUtil.formatJoinMessage(o.getUuid()));
                        }
                    }
                }
                else if (object instanceof InputMessage) {
                    handleInputMessage(channel, (InputMessage) object);
                }
                else if (object instanceof InputCommand) {
                    InputCommand o = (InputCommand)object;
                    handleCommandAction(channel, o);
                }
                else if (object instanceof InputCoolQ){
                    InputCoolQ coolMessage = (InputCoolQ)object;
                    if (coolMessage.getPost_type().equalsIgnoreCase("message")
                            && coolMessage.getMessage_type().equalsIgnoreCase("group")
                            && coolMessage.getSub_type().equalsIgnoreCase("normal")
                            && coolMessage.getGroup_id() == Config.getInstance().coolQConfig.coolQGroup){
                        if (!"".equals(Config.getInstance().coolQConfig.coolqToGameStart)){
                            if (!coolMessage.getMessage().startsWith(Config.getInstance().coolQConfig.coolqToGameStart)){
                                return;
                            }
                        }
                        MessageManage.getInstance().handleQQMessage(coolMessage);
                    }
                }
            });
        } else {
            String message = "unsupported frame type: " + frame.getClass().getName();
            throw new UnsupportedOperationException(message);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        //断开连接
        if (WsClientHelper.getCoolQ() == ctx.channel()){
            WsClientHelper.updateCoolQ(null);
        }
        plugin.getProxy().getScheduler().runAsync(plugin, () -> {
            WsClientUtil util = WsClientHelper.get(ctx.channel());
            if (util != null) {
                WsClientHelper.remove(ctx.channel());
                OutputPlayerList.sendWebPlayerList();
            }
        });
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete){
            WebSocketServerProtocolHandler.HandshakeComplete complete = (WebSocketServerProtocolHandler.HandshakeComplete)evt;
            HttpHeaders headers = complete.requestHeaders();

            String qq = headers.get("X-Self-ID");
            String role = headers.get("X-Client-Role");
            String authorization = headers.get("Authorization");
            if (!"".equals(qq) && "Universal".equals(role)){
                String token = Config.getInstance().coolQConfig.coolQAccessToken;
                if (!token.equals("")){
                    token = "Token " + token;
                    if (!token.equals(authorization)){
                        ctx.close();
                        return;
                    }
                }
                WsClientHelper.updateCoolQ(ctx.channel());
            }
            else {
                OutputPlayerList.sendPlayerStatusList(ctx.channel());
            }
        }
    }

    private void sendSelfInfo(Channel channel, java.util.UUID uuid) {
        // ... (existing code)
    }

    private void handleCommandAction(Channel channel, InputCommand inputCommand) {
        WsClientUtil util = WsClientHelper.get(channel);
        if (util == null || util.getUuid() == null) {
            NettyChannelMessageHelper.send(channel, OutputServerMessage.errorJSON("请先绑定账号").getJSON());
            return;
        }

        String fullCommand = inputCommand.getCommand();
        if (fullCommand.isEmpty()) return;

        handleCommand(channel, util, "/" + fullCommand);
    }

    private void handleCommand(Channel channel, WsClientUtil util, String message) {
        String[] commandArgs = message.replaceFirst("^/", "").split("\\s+");
        String label = commandArgs[0].toLowerCase();

        if (label.equals("msg") || label.equals("tell") || label.equals("whisper") || label.equals("w") || label.equals("t")) {
            if (commandArgs.length >= 3) {
                String toPlayerName = commandArgs[1];
                List<String> tmpList = new ArrayList<>(Arrays.asList(commandArgs).subList(2, commandArgs.length));
                String msg = String.join(" ", tmpList);
                MessageManage.getInstance().handleWebPrivateMessage(channel, util, toPlayerName, msg);
            }
        } else if (label.equals("yinwuchat") || label.equals("ignore") || label.equals("noat") || label.equals("vanish")) {
            // 对于插件自身指令，无论玩家是否在线都允许执行
            String[] args;
            if (label.equals("yinwuchat")) {
                args = commandArgs.length > 1 ? Arrays.copyOfRange(commandArgs, 1, commandArgs.length) : new String[]{"ws"};
            } else {
                args = commandArgs;
            }
            handleWebPlayerCommand(channel, util, args);
        } else {
            // 其他指令必须在游戏内在线才能执行
            ProxiedPlayer player = plugin.getProxy().getPlayer(util.getUuid());
            if (player != null) {
                plugin.getProxy().getPluginManager().dispatchCommand(player, message.substring(1));
            } else {
                NettyChannelMessageHelper.send(channel, OutputServerMessage.errorJSON("✗ 你必须在游戏内在线才能执行此指令").getJSON());
            }
        }
    }

    /**
     * 处理 Web 玩家的特殊命令（无需在游戏内在线）
     */
    private void handleWebPlayerCommand(Channel channel, WsClientUtil util, String[] args) {
        UUID uuid = util.getUuid();
        PlayerConfig.Player playerConfig = PlayerConfig.getConfig(uuid);
        String playerName = playerConfig.name;
        if (playerName == null || playerName.isEmpty()) {
            NettyChannelMessageHelper.send(channel, OutputServerMessage.errorJSON("无法获取玩家名称").getJSON());
            return;
        }

        String cmd = args[0].toLowerCase();
        ProxiedPlayer player = plugin.getProxy().getPlayer(uuid);
        boolean isAdmin = (player != null) ? Config.getInstance().isAdmin(player) : Config.getInstance().isAdmin(playerName);

        if (cmd.equals("ws")) {
            if (!isAdmin && (player == null || !player.hasPermission(Const.PERMISSION_WS))) {
                NettyChannelMessageHelper.send(channel, OutputServerMessage.errorJSON("✗ 权限不足").getJSON());
                return;
            }
            String host = "服务器IP或域名";
            try {
                java.net.InetSocketAddress address = (java.net.InetSocketAddress) plugin.getProxy().getConfig().getListeners().iterator().next().getHost();
                if (address != null && address.getHostString() != null) {
                    host = address.getHostString();
                }
            } catch (Exception ignored) {
            }
            if ("0.0.0.0".equals(host) || "::".equals(host)) {
                host = "服务器IP或域名";
            }
            int port = Config.getInstance().wsport;
            String wsUrl = "ws://" + host + ":" + port + "/ws";
            NettyChannelMessageHelper.send(channel, OutputServerMessage.infoJSON("WebSocket 地址: " + wsUrl + "\n如果网页使用 HTTPS，请将 ws:// 改为 wss://").getJSON());
        } else if (cmd.equals("ignore")) {
            if (!isAdmin && (player == null || !player.hasPermission(Const.PERMISSION_IGNORE))) {
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
            ProxiedPlayer target = plugin.getProxy().getPlayer(targetName);
            if (target != null) {
                if (playerConfig.ignore(target.getUniqueId())) {
                    playerConfig.save();
                    NettyChannelMessageHelper.send(channel, OutputServerMessage.infoJSON("✓ 已忽略玩家 " + targetName).getJSON());
                } else {
                    playerConfig.save();
                    NettyChannelMessageHelper.send(channel, OutputServerMessage.infoJSON("✓ 已取消忽略玩家 " + targetName).getJSON());
                }
            } else {
                NettyChannelMessageHelper.send(channel, OutputServerMessage.errorJSON("✗ 该玩家目前不在线，Bungee 版暂不支持离线忽略").getJSON());
            }
        } else if (cmd.equals("noat") || cmd.equals("muteat")) {
            String perm = cmd.equals("noat") ? Const.PERMISSION_NOAT : Const.PERMISSION_MUTEAT;
            if (!isAdmin && (player == null || !player.hasPermission(perm))) {
                NettyChannelMessageHelper.send(channel, OutputServerMessage.errorJSON("✗ 权限不足").getJSON());
                return;
            }
            playerConfig.muteAt = !playerConfig.muteAt;
            playerConfig.save();
            if (playerConfig.muteAt) {
                NettyChannelMessageHelper.send(channel, OutputServerMessage.infoJSON("✓ 已开启免打扰模式").getJSON());
            } else {
                NettyChannelMessageHelper.send(channel, OutputServerMessage.infoJSON("✓ 已关闭免打扰模式").getJSON());
            }
        } else if (cmd.equals("vanish")) {
            if (!isAdmin) {
                NettyChannelMessageHelper.send(channel, OutputServerMessage.errorJSON("✗ 权限不足（你必须在游戏中且拥有权限，或者使用 Bungee 权限系统）").getJSON());
                return;
            }
            playerConfig.vanish = !playerConfig.vanish;
            playerConfig.save();
            if (playerConfig.vanish) {
                NettyChannelMessageHelper.send(channel, OutputServerMessage.infoJSON("✓ 已开启隐身模式").getJSON());
            } else {
                NettyChannelMessageHelper.send(channel, OutputServerMessage.infoJSON("✓ 已关闭隐身模式").getJSON());
            }
            // 广播更新后的玩家列表
            OutputPlayerList.sendPlayerStatusList();
        } else if (cmd.equals("mute") || cmd.equals("unmute") || cmd.equals("muteinfo")) {
            if (!isAdmin) {
                NettyChannelMessageHelper.send(channel, OutputServerMessage.errorJSON("✗ 权限不足").getJSON());
                return;
            }
            
            org.lintx.plugins.yinwuchat.bungee.manage.MuteManage manager = org.lintx.plugins.yinwuchat.bungee.manage.MuteManage.getInstance();
            if (cmd.equals("mute")) {
                if (args.length < 2) {
                    NettyChannelMessageHelper.send(channel, OutputServerMessage.errorJSON("用法: /mute <玩家名> [时间(分钟)] [原因]").getJSON());
                    return;
                }
                String target = args[1];
                long duration = 0;
                if (args.length >= 3) {
                    try {
                        duration = Long.parseLong(args[2]) * 60;
                    } catch (NumberFormatException ignored) {}
                }
                String reason = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "";
                if (manager.mutePlayer(target, duration, playerName, reason)) {
                    NettyChannelMessageHelper.send(channel, OutputServerMessage.infoJSON("✓ 已禁言玩家 " + target + (duration > 0 ? " " + (duration/60) + " 分钟" : " (永久)")).getJSON());
                } else {
                    NettyChannelMessageHelper.send(channel, OutputServerMessage.errorJSON("✗ 禁言失败，玩家可能不在线").getJSON());
                }
            } else if (cmd.equals("unmute")) {
                if (args.length < 2) {
                    NettyChannelMessageHelper.send(channel, OutputServerMessage.errorJSON("用法: /unmute <玩家名>").getJSON());
                    return;
                }
                String target = args[1];
                if (manager.unmutePlayer(target, playerName)) {
                    NettyChannelMessageHelper.send(channel, OutputServerMessage.infoJSON("✓ 已解除玩家 " + target + " 的禁言").getJSON());
                } else {
                    NettyChannelMessageHelper.send(channel, OutputServerMessage.errorJSON("✗ 解除禁言失败，玩家可能不在线").getJSON());
                }
            } else if (cmd.equals("muteinfo")) {
                if (args.length < 2) {
                    NettyChannelMessageHelper.send(channel, OutputServerMessage.errorJSON("用法: /muteinfo <玩家名>").getJSON());
                    return;
                }
                String target = args[1];
                ProxiedPlayer targetPlayer = plugin.getProxy().getPlayer(target);
                if (targetPlayer != null) {
                    PlayerConfig.Player targetConfig = PlayerConfig.getConfig(targetPlayer);
                    if (!targetConfig.isMuted()) {
                        NettyChannelMessageHelper.send(channel, OutputServerMessage.infoJSON("玩家 " + target + " 当前未被禁言").getJSON());
                    } else {
                        long rem = targetConfig.getRemainingMuteTime();
                        String timeStr = rem == -1 ? "永久" : (rem / 60) + " 分钟";
                        NettyChannelMessageHelper.send(channel, OutputServerMessage.infoJSON("玩家 " + target + " 处于禁言状态\n剩余时间: " + timeStr + "\n原因: " + targetConfig.muteReason).getJSON());
                    }
                } else {
                    NettyChannelMessageHelper.send(channel, OutputServerMessage.errorJSON("✗ 无法获取该玩家信息").getJSON());
                }
            }
        } else {
            NettyChannelMessageHelper.send(channel, OutputServerMessage.infoJSON("可用指令: /yinwuchat [ws|ignore|muteat|vanish|mute|unmute|muteinfo]").getJSON());
        }
    }

    private void handleInputMessage(Channel channel, InputMessage o) {
        if (o.getMessage().equalsIgnoreCase("")) {
            return;
        }

        WsClientUtil util = WsClientHelper.get(channel);
        if (util != null && util.getUuid() != null) {
            if (!util.getLastDate().isEqual(LocalDateTime.MIN)) {
                Duration duration = Duration.between(util.getLastDate(), LocalDateTime.now());
                if (duration.toMillis() < Config.getInstance().wsCooldown) {
                    NettyChannelMessageHelper.send(channel, OutputServerMessage.errorJSON("发送消息过快（当前设置为每条消息之间最少间隔" + (Config.getInstance().wsCooldown) + "毫秒）").getJSON());
                    return;
                }
            }
            util.updateLastDate();

            if (o.getMessage().startsWith("/")) {
                handleCommand(channel, util, o.getMessage());
                return;
            }
            MessageManage.getInstance().handleWebPublicMessage(util.getUuid(), o.getMessage(), channel);
        }
    }
}
