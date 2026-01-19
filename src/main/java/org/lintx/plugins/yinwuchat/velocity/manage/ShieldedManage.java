package org.lintx.plugins.yinwuchat.velocity.manage;

import com.velocitypowered.api.proxy.Player;
import io.netty.channel.Channel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.lintx.plugins.yinwuchat.velocity.YinwuChat;
import org.lintx.plugins.yinwuchat.velocity.config.Config;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Velocity 屏蔽词管理系统
 * 检查消息中是否包含配置的屏蔽词
 */
public class ShieldedManage {
    private static final ShieldedManage instance = new ShieldedManage();
    
    // 玩家违规记录
    private final Map<String, ViolationRecord> users = new ConcurrentHashMap<>();
    
    private ShieldedManage() {
    }
    
    public static ShieldedManage getInstance() {
        return instance;
    }
    
    /**
     * 违规记录类
     */
    private static class ViolationRecord {
        int count = 0;
        LocalDateTime firstViolation = null;
    }
    
    /**
     * 检查结果类
     */
    public static class Result {
        public boolean shielded = false;  // 是否包含屏蔽词
        public boolean end = false;       // 是否阻止发送
        public boolean kick = false;      // 是否踢出玩家
        public String msg = "";           // 替换后的消息（如果使用替换模式）
    }
    
    /**
     * 检查玩家消息是否包含屏蔽词
     * @param player 玩家
     * @param message 消息内容
     * @return 检查结果
     */
    public Result checkShielded(Player player, String message) {
        Result result = checkShielded(player.getUniqueId().toString(), message);
        
        Config config = Config.getInstance();
        
        if (result.kick) {
            // 踢出玩家
            Component kickMessage = LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(config.tipsConfig.shieldedKickTip);
            player.disconnect(kickMessage);
            YinwuChat.getInstance().getLogger().info("[屏蔽词] 玩家 " + player.getUsername() + " 因多次发送违禁词被踢出");
        } else if (result.shielded && result.end) {
            // 发送提示消息
            Component tipMessage = LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(config.tipsConfig.shieldedTip);
            player.sendMessage(tipMessage);
        }
        
        return result;
    }
    
    /**
     * 检查 Web 玩家消息是否包含屏蔽词，并通过 WebSocket 发送提示
     * @param channel WebSocket 通道
     * @param uuid 玩家UUID
     * @param message 消息内容
     * @return 检查结果
     */
    public Result checkShielded(Channel channel, String uuid, String message) {
        Result result = checkShielded(uuid, message);
        
        Config config = Config.getInstance();
        
        if (result.kick) {
            // 踢出 Web 玩家
            org.lintx.plugins.yinwuchat.velocity.httpserver.NettyChannelMessageHelper.send(channel, 
                org.lintx.plugins.yinwuchat.velocity.json.OutputServerMessage.errorJSON(config.tipsConfig.shieldedKickTip).getJSON());
            channel.close();
            YinwuChat.getInstance().getLogger().info("[屏蔽词] Web 玩家 " + uuid + " 因多次发送违禁词被断开连接");
        } else if (result.shielded) {
            // 发送提示消息给 Web 玩家（无论是阻止模式还是替换模式都提示）
            org.lintx.plugins.yinwuchat.velocity.httpserver.NettyChannelMessageHelper.send(channel, 
                org.lintx.plugins.yinwuchat.velocity.json.OutputServerMessage.errorJSON(config.tipsConfig.shieldedTip).getJSON());
        }
        
        return result;
    }
    
    /**
     * 检查消息是否包含屏蔽词
     * @param uuid 玩家UUID
     * @param message 消息内容
     * @return 检查结果
     */
    public Result checkShielded(String uuid, String message) {
        // 预处理消息：移除颜色代码和空格，转小写
        String cleanMessage = message
                .replaceAll("&([0-9a-fA-FklmnorKLMNOR])", "")
                .replaceAll("§([0-9a-fA-FklmnorKLMNOR])", "")
                .replaceAll(" ", "")
                .toLowerCase(Locale.ROOT);
        
        Result result = new Result();
        Config config = Config.getInstance();
        
        // 检查是否包含屏蔽词
        if (config.shieldeds == null || config.shieldeds.isEmpty()) {
            return result;  // 没有配置屏蔽词
        }
        
        // 检查消息是否包含任何屏蔽词
        boolean containsShielded = config.shieldeds.parallelStream()
                .anyMatch(keyword -> {
                    String cleanKeyword = keyword.toLowerCase(Locale.ROOT).replaceAll(" ", "");
                    return cleanMessage.contains(cleanKeyword);
                });
        
        if (containsShielded) {
            result.shielded = true;
            
            // 记录违规
            ViolationRecord record = users.computeIfAbsent(uuid, k -> {
                ViolationRecord r = new ViolationRecord();
                r.firstViolation = LocalDateTime.now();
                return r;
            });
            
            // 检查是否超过时间窗口，重置计数
            Duration duration = Duration.between(record.firstViolation, LocalDateTime.now());
            if (duration.toMillis() > config.shieldedKickTime * 1000L) {
                record.firstViolation = LocalDateTime.now();
                record.count = 0;
            }
            
            // 增加违规计数
            record.count += 1;
            
            // 检查是否达到踢出阈值
            if (record.count >= config.shieldedKickCount) {
                result.kick = true;
                users.remove(uuid);  // 清除记录
                return result;
            }
            
            // 根据模式处理
            if (config.shieldedMode == 1) {
                // 替换模式：用配置的文本替换消息
                result.msg = config.tipsConfig.shieldedReplace;
            } else {
                // 阻止模式：不发送消息
                result.end = true;
            }
        }
        
        return result;
    }
    
    /**
     * 清除玩家的违规记录
     * @param uuid 玩家UUID
     */
    public void clearViolation(String uuid) {
        users.remove(uuid);
    }
    
    /**
     * 清除所有违规记录
     */
    public void clearAllViolations() {
        users.clear();
    }
}
