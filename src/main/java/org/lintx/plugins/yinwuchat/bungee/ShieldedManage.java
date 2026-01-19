package org.lintx.plugins.yinwuchat.bungee;


import io.netty.channel.Channel;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.lintx.plugins.yinwuchat.Util.MessageUtil;
import org.lintx.plugins.yinwuchat.bungee.config.Config;
import org.lintx.plugins.yinwuchat.bungee.json.OutputServerMessage;
import org.lintx.plugins.yinwuchat.bungee.httpserver.NettyChannelMessageHelper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ShieldedManage {
    private static final ShieldedManage instance = new ShieldedManage();
    public static ShieldedManage getInstance(){
        return instance;
    }

    private Map<String,Man> users = new HashMap<>();

    private static class Man {
        int count = 0;
        LocalDateTime first = null;
    }

    private String formatMessage(String string){
        string = string.replaceAll("&([0-9a-fA-FklmnorKLMNOR])","§$1");
        return string;
    }

    Result checkShielded(Channel channel, String uuid, String message){
        Result result = checkShielded(uuid,message);
        if (result.kick){
            NettyChannelMessageHelper.send(channel, OutputServerMessage.errorJSON(formatMessage(Config.getInstance().tipsConfig.shieldedKickTip)).getJSON());
            channel.close();
            YinwuChat.getPlugin().getLogger().info("[屏蔽词] Web 玩家 " + uuid + " 因多次发送违禁词被断开连接");
        }
        else if (result.shielded){
            NettyChannelMessageHelper.send(channel, OutputServerMessage.errorJSON(formatMessage(Config.getInstance().tipsConfig.shieldedTip)).getJSON());
        }
        return result;
    }

    Result checkShielded(ProxiedPlayer player,String message){
        Result result = checkShielded(player.getUniqueId().toString(),message);
        if (result.kick){
            player.disconnect(MessageUtil.newTextComponent(formatMessage(Config.getInstance().tipsConfig.shieldedKickTip)));
            YinwuChat.getPlugin().getLogger().info("[屏蔽词] 玩家 " + player.getName() + " 因多次发送违禁词被踢出");
        }
        else if (result.shielded){
            player.sendMessage(MessageUtil.newTextComponent(formatMessage(Config.getInstance().tipsConfig.shieldedTip)));
        }
        return result;
    }

    private Result checkShielded(String uuid,String message){
        String cleanMessage = message
                .replaceAll("&([0-9a-fA-FklmnorKLMNOR])", "")
                .replaceAll("§([0-9a-fA-FklmnorKLMNOR])", "")
                .replaceAll(" ", "")
                .toLowerCase(Locale.ROOT);
        
        Result result = new Result();
        Config config = Config.getInstance();
        
        // 检查是否包含屏蔽词
        if (config.shieldeds == null || config.shieldeds.isEmpty()) {
            return result;
        }
        
        // 检查消息是否包含任何屏蔽词（清理关键词以匹配 cleaned 消息）
        boolean containsShielded = config.shieldeds.parallelStream()
                .anyMatch(keyword -> {
                    String cleanKeyword = keyword.toLowerCase(Locale.ROOT).replaceAll(" ", "");
                    return !cleanKeyword.isEmpty() && cleanMessage.contains(cleanKeyword);
                });
        
        if (containsShielded){
            YinwuChat.getPlugin().getLogger().info(uuid + " send a shielded word: " + message);
            result.shielded = true;
            if (!users.containsKey(uuid)){
                Man man = new Man();
                man.first = LocalDateTime.now();
                users.put(uuid,man);
            }

            Man man = users.get(uuid);
            Duration duration = Duration.between(man.first,LocalDateTime.now());
            if (duration.toMillis()>config.shieldedKickTime*1000){
                man.first = LocalDateTime.now();
                man.count = 0;
            }
            man.count += 1;
            users.put(uuid,man);
            if (man.count>=config.shieldedKickCount){
                result.kick = true;
                users.remove(uuid);
                return result;
            }

            if (config.shieldedMode==1){
                result.msg = config.tipsConfig.shieldedReplace;
            }
            else {
                result.end = true;
            }
        }
        return result;
    }

    static class Result{
        boolean shielded = false;
        boolean end = false;
        boolean kick = false;
        String msg = "";
    }
}
