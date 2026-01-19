package org.lintx.plugins.yinwuchat.bungee;

import io.netty.channel.Channel;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.config.ListenerInfo;
import net.md_5.bungee.api.plugin.Command;
import org.lintx.plugins.yinwuchat.Const;
import org.lintx.plugins.yinwuchat.Util.MessageUtil;
import org.lintx.plugins.yinwuchat.bungee.config.Config;
import org.lintx.plugins.yinwuchat.bungee.config.PlayerConfig;
import org.lintx.plugins.yinwuchat.bungee.json.*;
import org.lintx.plugins.yinwuchat.bungee.httpserver.NettyChannelMessageHelper;
import org.lintx.plugins.yinwuchat.bungee.httpserver.WsClientHelper;
import org.lintx.plugins.yinwuchat.bungee.httpserver.WsClientUtil;

import java.util.*;

public class Commands extends Command {
    private YinwuChat plugin;

    Commands(YinwuChat plugin, String name) {
        super(name,null,"yw");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length>=1 && args[0].equalsIgnoreCase("reload")) {
            if (sender instanceof ProxiedPlayer) {
                ProxiedPlayer player = (ProxiedPlayer) sender;
                if (sender.hasPermission(Const.PERMISSION_RELOAD) || Config.getInstance().isAdmin(player)) {
                    reload(sender,args);
                }
                else{
                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "权限不足"));
                    return;
                }
            }
            else{
                reload(sender,args);
            }
            return;
        }
        if (!(sender instanceof ProxiedPlayer)) {
            sender.sendMessage(MessageUtil.newTextComponent("Must use command in-game"));
            return;
        }
        final ProxiedPlayer player = (ProxiedPlayer)sender;
        boolean isAdmin = Config.getInstance().isAdmin(player);
        boolean isDefault = Config.getInstance().isDefault(player);
        final UUID playerUUID = player.getUniqueId();
        if (playerUUID==null) {
            plugin.getLogger().info("Player " + sender.getName() + "has a null UUID");
            sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "You can't use that command right now. (No UUID)"));
            return;
        }
        if (args.length>=1) {
            String first = args[0];
            PlayerConfig.Player playerConfig = PlayerConfig.getConfig(player);
            if (first.equalsIgnoreCase("badword")){
                if (player.hasPermission(Const.PERMISSION_BAD_WORD) || Config.getInstance().isAdmin(player)){
                    if (args.length>=3){
                        if (args[1].equalsIgnoreCase("add")){
                            String word = args[2].toLowerCase(Locale.ROOT);
                            Config.getInstance().shieldeds.add(word);
                            Config.getInstance().save(plugin);
                            sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "成功将关键词 " + word + " 添加到屏蔽库"));
                            return;
                        }
                        else if (args[1].equalsIgnoreCase("remove")){
                            String word = args[2].toLowerCase(Locale.ROOT);
                            if (Config.getInstance().shieldeds.contains(word)){
                                Config.getInstance().shieldeds.remove(word);
                                Config.getInstance().save(plugin);
                                sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "成功将关键词 " + word + " 从屏蔽库删除"));
                                return;
                            }
                        }
                    }
                    else if (args.length==2 && args[1].equalsIgnoreCase("list")){
                        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "屏蔽关键词："));
                        for (String str:Config.getInstance().shieldeds){
                            sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + str));
                        }
                        return;
                    }
                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "命令格式：/yinwuchat badword <add|remove|list> [word]"));
                    return;
                }
            }
            else if (first.equalsIgnoreCase("bind")) {
                if (player.hasPermission(Const.PERMISSION_BIND) || isDefault) {
                    if (args.length>=2) {
                        String token = args[1];
                        PlayerConfig.Tokens tokens = PlayerConfig.getTokens();
                        if (tokens.bindToken(token,player)){
                            sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "绑定成功"));
                            tokens.save();
                            Channel channel = WsClientHelper.getWebSocket(token);
                            if (channel != null) {
                                WsClientHelper.get(channel).setUUID(playerUUID);
                                NettyChannelMessageHelper.send(channel,(new org.lintx.plugins.yinwuchat.bungee.json.InputCheckToken(token,false)).getJSON());
                                WsClientHelper.kickOtherWS(channel, playerUUID);
                            }
                            org.lintx.plugins.yinwuchat.bungee.json.OutputPlayerList.sendWebPlayerList();
                        }
                        else {
                            sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "绑定失败，你可以重试几次，如果持续失败，请联系OP，错误代码：002"));
                        }
                    }
                    else {
                        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "命令格式：/yinwuchat bind <token>"));
                        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "缺少token（token从网页客户端获取）"));
                    }
                } else {
                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "权限不足"));
                }
                return;
            }
            else if (first.equalsIgnoreCase("list")) {
                if (player.hasPermission(Const.PERMISSION_LIST) || isDefault) {
                    PlayerConfig.Tokens tokens = PlayerConfig.getTokens();
                    List<String> list = tokens.tokenWithPlayer(player);
                    if (list.isEmpty()) {
                        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "你没有绑定任何token"));
                        return;
                    }
                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "你一共绑定了"+list.size()+"个token，详情如下："));
                    for (String token : list) {
                        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + token));
                    }
                } else {
                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "权限不足"));
                }
                return;
            }
            else if (first.equalsIgnoreCase("ws")) {
                if (player.hasPermission(Const.PERMISSION_WS) || isDefault) {
                    String host = "服务器IP或域名";
                    try {
                        for (ListenerInfo info : plugin.getProxy().getConfig().getListeners()) {
                            if (info != null && info.getHost() != null) {
                                host = info.getHost().getHostString();
                                break;
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    if ("0.0.0.0".equals(host) || "::".equals(host)) {
                        host = "服务器IP或域名";
                    }
                    int port = Config.getInstance().wsport;
                    String wsUrl = "ws://" + host + ":" + port + "/ws";
                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GOLD + "WebSocket 地址: " + ChatColor.AQUA + wsUrl));
                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GRAY + "如果网页使用 HTTPS，请将 ws:// 改为 wss://"));
                } else {
                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "权限不足"));
                }
                return;
            }
            else if (first.equalsIgnoreCase("unbind")) {
                if (player.hasPermission(Const.PERMISSION_UNBIND) || isDefault) {
                    if (args.length>=2) {
                        String token = args[1];

                        PlayerConfig.Tokens tokens = PlayerConfig.getTokens();
                        List<String> list = tokens.tokenWithPlayer(player);
                        if (list.isEmpty()) {
                            sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "你没有绑定任何token"));
                            return;
                        }
                        for (String t : list) {
                            if (t.startsWith(token)){
                                tokens.unbindToken(t);
                                sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "解绑成功"));
                                tokens.save();
                                return;
                            }
                        }
                        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "在你已绑定的token中没有找到对应的数据，解绑失败"));
                    }
                    else{
                        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "命令格式：/yinwuchat unbind token"));
                        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "token可以只输入前面的部分"));
                    }
                } else {
                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "权限不足"));
                }
                return;
            }
            else if (first.equalsIgnoreCase("format") && Config.getInstance().allowPlayerFormatPrefixSuffix){
                if (player.hasPermission(Const.PERMISSION_FORMAT) || isDefault) {
                    if (args.length>=4){
                    String namespace = args[1].toLowerCase(Locale.ROOT);
                    String position = args[2].toLowerCase(Locale.ROOT);
                    String action = args[3].toLowerCase(Locale.ROOT);
                    String str = "";
                    if (args.length>=5){
                        str = MessageUtil.filter(args[4],Config.getInstance().playerFormatPrefixSuffixDenyStyle);
                    }
                    if (namespace.equals("public")){
                        if (position.equals("prefix")){
                            if ("view".equals(action)){
                                if ("".equals(playerConfig.publicPrefix)){
                                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "你没有设置公共消息前缀"));
                                }else {
                                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "你的公共消息前缀是:"+playerConfig.publicPrefix.replaceAll("([&§])([0-9a-fklmnor])","$1&a$2")));
                                }
                                return;
                            }
                            else if ("set".equals(action)) {
                                playerConfig.publicPrefix = str;
                                playerConfig.save();
                                sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "你的公共消息前缀已设置为:"+playerConfig.publicPrefix.replaceAll("([&§])([0-9a-fklmnor])","$1&a$2")));
                                return;
                            }
                        }
                        else if (position.equals("suffix")){
                            if ("view".equals(action)){
                                if ("".equals(playerConfig.publicSuffix)){
                                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "你没有设置公共消息后缀"));
                                }else {
                                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "你的公共消息后缀是:"+playerConfig.publicSuffix.replaceAll("([&§])([0-9a-fklmnor])","$1&a$2")));
                                }
                                return;
                            }
                            else if ("set".equals(action))  {
                                playerConfig.publicSuffix = str;
                                playerConfig.save();
                                sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "你的公共消息后缀已设置为:"+playerConfig.publicSuffix.replaceAll("([&§])([0-9a-fklmnor])","$1&a$2")));
                                return;
                            }
                        }
                    }
                    else if (namespace.equals("private")){
                        if (position.equals("prefix")){
                            if ("view".equals(action)){
                                if ("".equals(playerConfig.privatePrefix)){
                                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "你没有设置私聊消息前缀"));
                                }else {
                                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "你的私聊消息前缀是:"+playerConfig.privatePrefix.replaceAll("([&§])([0-9a-fklmnor])","$1&a$2")));
                                }
                                return;
                            }
                            else if ("set".equals(action))  {
                                playerConfig.privatePrefix = str;
                                playerConfig.save();
                                sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "你的私聊消息前缀已设置为:"+playerConfig.privatePrefix.replaceAll("([&§])([0-9a-fklmnor])","$1&a$2")));
                                return;
                            }
                        }
                        else if (position.equals("suffix")){
                            if ("view".equals(action)){
                                if ("".equals(playerConfig.privateSuffix)){
                                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "你没有设置私聊消息后缀"));
                                }else {
                                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "你的私聊消息后缀是:"+playerConfig.privateSuffix.replaceAll("([&§])([0-9a-fklmnor])","$1&a$2")));
                                }
                                return;
                            }
                            else if ("set".equals(action))  {
                                playerConfig.privateSuffix = str;
                                playerConfig.save();
                                sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "你的私聊消息后缀已设置为:"+playerConfig.privateSuffix.replaceAll("([&§])([0-9a-fklmnor])","$1&a$2")));
                                return;
                            }
                        }
                    }
                }
                }
                else {
                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "权限不足"));
                }
            }
            else if (first.equalsIgnoreCase("vanish")){
                if (player.hasPermission(Const.PERMISSION_VANISH) || Config.getInstance().isAdmin(player)){
                    playerConfig.vanish = !playerConfig.vanish;
                    playerConfig.save();
                    if (playerConfig.vanish){
                        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "你现在在聊天系统中处于隐身状态"));
                    }
                    else {
                        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "你现在在聊天系统中不再处于隐身状态"));
                    }
                    return;
                }
            }
            else if (first.equalsIgnoreCase("muteat")){
                if (player.hasPermission(Const.PERMISSION_MUTEAT) || isDefault) {
                    playerConfig.muteAt = !playerConfig.muteAt;
                    playerConfig.save();
                    if (playerConfig.muteAt){
                        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "你现在被@时不再会听到声音了"));
                    }
                    else {
                        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "你现在被@时可以听到声音了"));
                    }
                } else {
                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "权限不足"));
                }
                return;
            }
            else if (first.equalsIgnoreCase("noat")){
                if (player.hasPermission(Const.PERMISSION_NOAT) || isDefault) {
                    playerConfig.banAt = !playerConfig.banAt;
                    playerConfig.save();
                    if (playerConfig.banAt){
                        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "你现在不能被@了（管理员@全体除外）"));
                    }
                    else {
                        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "你现在可以被@了"));
                    }
                } else {
                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "权限不足"));
                }
                return;
            }
            else if (first.equalsIgnoreCase("ignore")){
                if (player.hasPermission(Const.PERMISSION_IGNORE) || isDefault) {
                    if (args.length>=2) {
                        String name = args[1].toLowerCase(Locale.ROOT);
                        for (ProxiedPlayer p:plugin.getProxy().getPlayers()){
                            if (p.getName().toLowerCase(Locale.ROOT).equals(name)){
                                if (playerConfig.ignore(p.getUniqueId())){
                                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "你现在忽略了"+p.getName()+"的信息"));
                                }
                                else {
                                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "你不再忽略"+p.getName()+"的信息"));
                                }
                                return;
                            }
                        }

                        for (WsClientUtil util:WsClientHelper.utils()){
                            PlayerConfig.Player p = PlayerConfig.getConfig(util.getUuid());
                            if (name.equals(p.name.toLowerCase(Locale.ROOT))){
                                if (playerConfig.ignore(util.getUuid())){
                                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "你现在忽略了"+p.name+"的信息"));
                                }
                                else {
                                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "你不再忽略"+p.name+"的信息"));
                                }
                                return;
                            }
                        }
                    }
                    else{
                        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GOLD + "忽略某人的消息：/yinwuchat ignore <player_name>（再输入一次不再忽略）"));
                    }
                } else {
                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "权限不足"));
                }
                return;
            }
            else if (first.equalsIgnoreCase("monitor")){
                if (player.hasPermission(Const.PERMISSION_MONITOR_PRIVATE_MESSAGE) || Config.getInstance().isAdmin(player)){
                    playerConfig.monitor = !playerConfig.monitor;
                    playerConfig.save();
                    if (playerConfig.monitor){
                        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "你现在会监听其他玩家的私聊信息"));
                    }
                    else {
                        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "你现在不会监听其他玩家的私聊信息"));
                    }
                    return;
                }
            }
            else if (first.equalsIgnoreCase("permsync")) {
                if (!isAdmin && !sender.hasPermission(Const.PERMISSION_ADMIN)) {
                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "权限不足"));
                    return;
                }
                handlePermissionSync(sender);
                return;
            }
            else if (first.equalsIgnoreCase("atalladmin")) {
                if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) {
                    if (!isAdmin && !sender.hasPermission(Const.PERMISSION_AT_ALL_ADMIN_RESET)) {
                        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "权限不足"));
                        return;
                    }
                    if (args.length < 3) {
                        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "用法: /yinwuchat atalladmin confirm <玩家名>"));
                        return;
                    }
                    String targetName = args[2];
                    PlayerConfig.Player targetConfig = PlayerConfig.getPlayerConfigByName(targetName);
                    if (targetConfig != null) {
                        targetConfig.lastAtAllAdmin = 0;
                        targetConfig.save();
                        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "✓ 已重置玩家 " + targetName + " 的突发事件提醒冷却时间"));
                    } else {
                        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "✗ 未找到玩家 " + targetName));
                    }
                    return;
                }

                if (!sender.hasPermission(Const.PERMISSION_AT_ALL_ADMIN) && !isAdmin) {
                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "权限不足"));
                    return;
                }

                long now = System.currentTimeMillis();
                if (playerConfig.lastAtAllAdmin > 0 && now - playerConfig.lastAtAllAdmin < 24 * 60 * 60 * 1000L) {
                    long remaining = 24 * 60 * 60 * 1000L - (now - playerConfig.lastAtAllAdmin);
                    long hours = remaining / (60 * 60 * 1000L);
                    long minutes = (remaining % (60 * 60 * 1000L)) / (60 * 1000L);
                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "✗ 你今天已经使用过该功能了，请在 " + hours + " 小时 " + minutes + " 分钟后再试"));
                    return;
                }

                sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "" + ChatColor.BOLD + "⚠ 请确认目前情况是否需要提醒所有管理员，若情况不属实，将承担被封禁的风险！！"));
                net.md_5.bungee.api.chat.TextComponent confirmBtn = new net.md_5.bungee.api.chat.TextComponent("  点击确认发送：[ √ ]");
                confirmBtn.setColor(ChatColor.GREEN);
                confirmBtn.setBold(true);
                confirmBtn.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/yinwuchat atalladmin_execute"));
                confirmBtn.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new net.md_5.bungee.api.chat.hover.content.Text("确认发送突发事件提醒")));
                
                net.md_5.bungee.api.chat.TextComponent cancelBtn = new net.md_5.bungee.api.chat.TextComponent("    [ x ]");
                cancelBtn.setColor(ChatColor.RED);
                cancelBtn.setBold(true);
                cancelBtn.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/yinwuchat atalladmin_cancel"));
                cancelBtn.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new net.md_5.bungee.api.chat.hover.content.Text("取消发送")));
                
                confirmBtn.addExtra(cancelBtn);
                sender.sendMessage(confirmBtn);
                return;
            }
            else if (first.equalsIgnoreCase("atalladmin_execute")) {
                if (!sender.hasPermission(Const.PERMISSION_AT_ALL_ADMIN) && !isAdmin) {
                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "权限不足"));
                    return;
                }

                long now = System.currentTimeMillis();
                if (playerConfig.lastAtAllAdmin > 0 && now - playerConfig.lastAtAllAdmin < 24 * 60 * 60 * 1000L) {
                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "✗ 冷却中"));
                    return;
                }

                playerConfig.lastAtAllAdmin = now;
                playerConfig.save();

                String playerName = player.getName();
                net.md_5.bungee.api.chat.TextComponent gameAlert = new net.md_5.bungee.api.chat.TextComponent("警告！" + playerName + " 报告服务器存在突发事件，请立即查看");
                gameAlert.setColor(ChatColor.RED);
                gameAlert.setBold(true);

                com.google.gson.JsonObject webAlert = new com.google.gson.JsonObject();
                webAlert.addProperty("action", "admin_alert");
                webAlert.addProperty("message", "存在需要立即处理的服务器风险");
                webAlert.addProperty("player", playerName);
                String webAlertJson = webAlert.toString();

                // 提醒所有在线管理员
                for (ProxiedPlayer p : plugin.getProxy().getPlayers()) {
                    if (Config.getInstance().isAdmin(p)) {
                        p.sendMessage(gameAlert);
                    }
                }

                // 提醒所有 Web 在线管理员
                for (io.netty.channel.Channel channel : org.lintx.plugins.yinwuchat.bungee.httpserver.WsClientHelper.channels()) {
                    org.lintx.plugins.yinwuchat.bungee.httpserver.WsClientUtil util = org.lintx.plugins.yinwuchat.bungee.httpserver.WsClientHelper.get(channel);
                    if (util != null && util.getUuid() != null) {
                        PlayerConfig.Player pc = PlayerConfig.getConfig(util.getUuid());
                        if (pc.name != null && Config.getInstance().isAdmin(pc.name)) {
                            org.lintx.plugins.yinwuchat.bungee.httpserver.NettyChannelMessageHelper.send(channel, webAlertJson);
                        }
                    }
                }

                sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "✓ 突发事件报告已发送给所有管理员"));
                return;
            }
            else if (first.equalsIgnoreCase("atalladmin_cancel")) {
                sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GRAY + "已取消突发事件报告。"));
                return;
            }
            else if (first.equalsIgnoreCase("reset")) {
                if (args.length >= 3) {
                    String accountName = args[1];
                    String code = args[2];
                    org.lintx.plugins.yinwuchat.common.auth.AuthService authService = 
                        org.lintx.plugins.yinwuchat.common.auth.AuthService.getInstance(plugin.getDataFolder());
                    boolean success = authService.verifyInGameReset(accountName, player.getName(), code);
                    if (success) {
                        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "✓ 身份验证成功！请回到 Web 端设置新密码。"));
                    } else {
                        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "✗ 验证失败。请检查账户名和重置码是否正确，或重置申请已过期。"));
                    }
                } else {
                    sender.sendMessage(MessageUtil.newTextComponent(ChatColor.RED + "用法: /yinwuchat reset <账户名> <重置码>"));
                }
                return;
            }
        }
        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GOLD + "YinwuChat Version "+ plugin.getDescription().getVersion() + ",Author:"+plugin.getDescription().getAuthor()));
        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GOLD + "插件帮助："));
        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GOLD + "插件基本命令为&b/yinwuchat&6或&b/yw&6"));
        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GOLD + "绑定：&b/yinwuchat bind <token>&6，token为web端获取"));
        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GOLD + "获取WS地址：&b/yinwuchat ws&6（用于手动填写WebSocket地址）"));
        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GOLD + "查询：&b/yinwuchat list&6，可以查询到你绑定的所有token"));
        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GOLD + "解绑：&b/yinwuchat unbind <token>"));
        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GOLD + "可以解绑对应的token，token为查询结果中的token,可以只输入前面的部分"));
        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GOLD + "发送私聊消息：&b/msg <player_name> <message>&6，例：&b/msg LinTx 一条私聊消息"));
        if (Config.getInstance().allowPlayerFormatPrefixSuffix){
            sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GOLD + "查看/设置聊天前后缀：&b/yinwuchat format public/private prefix/suffix view/set [prefix/suffix]"));
        }
        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GOLD + "被@时静音：&b/yinwuchat muteat"));
        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GOLD + "阻止自己被@：&b/yinwuchat noat&6（无法阻止被管理@全体）"));
        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GOLD + "忽略某人的消息：&b/yinwuchat ignore <player_name>&6（再输入一次不再忽略）"));
        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GOLD + "突发事件报告：&b/yinwuchat atalladmin&6（每日限一次）"));
        if (isAdmin) {
            sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GOLD + "重置报告冷却：&b/yinwuchat atalladmin confirm <player_name>"));
        }
        if (sender.hasPermission(Const.PERMISSION_RELOAD) || isAdmin){
            sender.sendMessage(MessageUtil.newTextComponent("&c重新加载插件：&b/yinwuchat reload [config|ws]&c重新加载插件/插件配置/WebSocket"));
        }
        if (sender.hasPermission(Const.PERMISSION_BAD_WORD) || isAdmin){
            sender.sendMessage(MessageUtil.newTextComponent("&c聊天关键词管理：&b/yinwuchat badword <add|remove|list> [word]"));
        }
        if (sender.hasPermission(Const.PERMISSION_VANISH) || isAdmin){
            sender.sendMessage(MessageUtil.newTextComponent("&c聊天隐身：&b/yinwuchat vanish"));
        }
        if (sender.hasPermission(Const.PERMISSION_MONITOR_PRIVATE_MESSAGE) || isAdmin){
            sender.sendMessage(MessageUtil.newTextComponent("&c切换是否监听其他玩家私聊信息：&b/yinwuchat monitor"));
        }
        if (isAdmin) {
            sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GOLD + "同步权限节点：&b/yinwuchat permsync"));
        }
    }

    private void handlePermissionSync(CommandSender sender) {
        boolean detected = false;
        List<String> pluginIds = Config.getInstance().permissionPluginIds;
        if (pluginIds != null) {
            for (String pluginId : pluginIds) {
                if (pluginId != null && !pluginId.isEmpty()
                    && plugin.getProxy().getPluginManager().getPlugin(pluginId) != null) {
                    detected = true;
                    break;
                }
            }
        }
        if (!detected) {
            sender.sendMessage(MessageUtil.newTextComponent(ChatColor.YELLOW + "未检测到权限插件，将尝试直接执行同步命令"));
        }
        String adminGroup = Config.getInstance().adminGroup != null && !Config.getInstance().adminGroup.isEmpty()
            ? Config.getInstance().adminGroup
            : "admin";
        String defaultGroup = Config.getInstance().defaultGroup != null && !Config.getInstance().defaultGroup.isEmpty()
            ? Config.getInstance().defaultGroup
            : "default";
        
        String commandPrefix = Config.getInstance().permissionCommandPrefix != null && !Config.getInstance().permissionCommandPrefix.isEmpty()
            ? Config.getInstance().permissionCommandPrefix
            : "lp";
        
        List<String> commands = new ArrayList<>();
        for (String node : Const.DEFAULT_PERMISSION_NODES) {
            commands.add(commandPrefix + " group " + defaultGroup + " permission set " + node + " true");
            commands.add(commandPrefix + " group " + adminGroup + " permission set " + node + " true");
        }
        for (String node : Const.ADMIN_PERMISSION_NODES) {
            commands.add(commandPrefix + " group " + adminGroup + " permission set " + node + " true");
        }
        
        dispatchPermissionCommands(commands);
        
        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "✓ 已同步权限节点到组: " + adminGroup + ", " + defaultGroup));
    }

    private void dispatchPermissionCommands(List<String> commands) {
        if (commands == null || commands.isEmpty()) return;
        for (int i = 0; i < commands.size(); i++) {
            String command = commands.get(i);
            long delayMs = i * 50L;
            plugin.getProxy().getScheduler().schedule(
                plugin,
                () -> plugin.getProxy().getPluginManager().dispatchCommand(plugin.getProxy().getConsole(), command),
                delayMs,
                java.util.concurrent.TimeUnit.MILLISECONDS
            );
        }
    }

    private void reload(CommandSender sender, String[] args){
        sender.sendMessage(MessageUtil.newTextComponent(ChatColor.GREEN + "YinwuChat插件重载"));
        if (args.length==1){
            plugin.reload();
        }
        else if (args.length>1){
            String s = args[1];
            if (s.equalsIgnoreCase("config")){
                plugin.reloadConfig();
            }
            else if (s.equalsIgnoreCase("ws")){
                plugin.startWs();
            }
        }
    }
}
