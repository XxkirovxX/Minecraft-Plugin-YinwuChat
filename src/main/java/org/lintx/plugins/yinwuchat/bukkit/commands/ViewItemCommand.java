package org.lintx.plugins.yinwuchat.bukkit.commands;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.lintx.plugins.yinwuchat.Const;
import org.lintx.plugins.yinwuchat.bukkit.ItemDisplayCache;
import org.lintx.plugins.yinwuchat.bukkit.YinwuChat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /viewitem 命令处理器
 * 用于在 GUI 中展示聊天中分享的物品
 * 支持跨服务器物品展示
 */
public class ViewItemCommand implements CommandExecutor {
    
    private final YinwuChat plugin;
    
    // 展示界面的标题前缀，用于识别展示界面
    public static final String DISPLAY_TITLE_PREFIX = "§6物品展示";
    
    // 等待 Velocity 响应的请求
    private static final Map<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
    
    public ViewItemCommand(YinwuChat plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c此命令只能由玩家执行");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length < 1) {
            player.sendMessage("§c用法: /viewitem <物品ID>");
            return true;
        }
        
        String itemId = args[0];
        
        // 首先检查本地缓存
        ItemDisplayCache.CachedItem cached = ItemDisplayCache.getInstance().getItem(itemId);
        
        if (cached != null) {
            // 本地有缓存，直接打开
            openDisplayInventory(player, cached.item, cached.playerName);
            return true;
        }
        
        // 本地没有，向 Velocity 请求
        requestItemFromVelocity(player, itemId);
        
        return true;
    }
    
    /**
     * 向 Velocity 请求物品数据
     */
    private void requestItemFromVelocity(Player player, String itemId) {
        try {
            // 存储待处理的请求
            PendingRequest request = new PendingRequest();
            request.playerUuid = player.getUniqueId();
            request.itemId = itemId;
            request.requestTime = System.currentTimeMillis();
            pendingRequests.put(itemId + ":" + player.getUniqueId(), request);
            
            // 发送请求到 Velocity
            ByteArrayDataOutput output = ByteStreams.newDataOutput();
            output.writeUTF(Const.PLUGIN_SUB_CHANNEL_ITEM_DISPLAY_REQUEST);
            output.writeUTF(itemId);
            
            player.sendPluginMessage(plugin, plugin.getActualChannel(), output.toByteArray());
            plugin.getLogger().info("Requested item from Velocity: " + itemId + " for player " + player.getName());
            
            // 设置超时清理
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                PendingRequest pending = pendingRequests.remove(itemId + ":" + player.getUniqueId());
                if (pending != null) {
                    Player p = Bukkit.getPlayer(pending.playerUuid);
                    if (p != null && p.isOnline()) {
                        p.sendMessage("§c物品展示已过期或不存在");
                    }
                }
            }, 100L); // 5秒超时
            
        } catch (Exception e) {
            player.sendMessage("§c请求物品数据失败");
            plugin.getLogger().warning("Failed to request item from Velocity: " + e.getMessage());
        }
    }
    
    /**
     * 处理 Velocity 返回的物品数据
     * 由 Listeners 调用
     */
    public static void handleItemDisplayResponse(YinwuChat plugin, Player player, String itemId, 
            boolean success, String itemJson, String playerName, String serverName) {
        
        String key = itemId + ":" + player.getUniqueId();
        PendingRequest pending = pendingRequests.remove(key);
        
        if (pending == null) {
            // 没有待处理的请求
            return;
        }
        
        if (!success || itemJson == null || itemJson.isEmpty()) {
            player.sendMessage("§c物品展示已过期或不存在");
            return;
        }
        
        try {
            // 从 JSON 重建物品
            ItemStack item = deserializeItem(itemJson);
            
            if (item == null) {
                player.sendMessage("§c无法解析物品数据");
                return;
            }
            
            // 打开展示界面
            openDisplayInventoryStatic(plugin, player, item, playerName);
            
        } catch (Exception e) {
            player.sendMessage("§c处理物品数据时出错");
            plugin.getLogger().warning("Failed to handle item display response: " + e.getMessage());
        }
    }
    
    /**
     * 从 JSON 反序列化物品
     */
    private static ItemStack deserializeItem(String itemJson) {
        try {
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(itemJson).getAsJsonObject();
            
            // 获取物品 ID
            String id = json.has("id") ? json.get("id").getAsString() : null;
            if (id == null) {
                return null;
            }
            
            // 移除 minecraft: 前缀
            if (id.startsWith("minecraft:")) {
                id = id.substring(10);
            }
            
            // 获取物品类型
            org.bukkit.Material material;
            try {
                material = org.bukkit.Material.valueOf(id.toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
            
            // 获取数量
            int count = json.has("count") ? json.get("count").getAsInt() : 1;
            
            // 创建物品
            ItemStack item = new ItemStack(material, count);
            
            // 获取或创建 ItemMeta
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return item;
            }
            
            // 设置显示名称
            if (json.has("displayName")) {
                meta.setDisplayName(json.get("displayName").getAsString());
            }
            
            // 设置 Lore
            if (json.has("lore") && json.get("lore").isJsonArray()) {
                java.util.List<String> lore = new java.util.ArrayList<>();
                for (com.google.gson.JsonElement elem : json.getAsJsonArray("lore")) {
                    lore.add(elem.getAsString());
                }
                meta.setLore(lore);
            }
            
            // 应用 ItemMeta
            item.setItemMeta(meta);
            
            // 设置附魔
            if (json.has("enchantments") && json.get("enchantments").isJsonObject()) {
                com.google.gson.JsonObject enchants = json.getAsJsonObject("enchantments");
                for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : enchants.entrySet()) {
                    String enchKey = entry.getKey();
                    int level = entry.getValue().getAsInt();
                    
                    // 查找附魔
                    org.bukkit.enchantments.Enchantment enchantment = findEnchantment(enchKey);
                    if (enchantment != null) {
                        // 使用 addUnsafeEnchantment 允许超过最大等级的附魔
                        item.addUnsafeEnchantment(enchantment, level);
                    }
                }
            }
            
            return item;
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 根据附魔 key 查找附魔
     */
    private static org.bukkit.enchantments.Enchantment findEnchantment(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        
        // 标准化 key（移除可能的 minecraft: 前缀）
        if (key.startsWith("minecraft:")) {
            key = key.substring(10);
        }
        
        // 尝试通过 NamespacedKey 查找（1.13+）
        try {
            org.bukkit.NamespacedKey namespacedKey = org.bukkit.NamespacedKey.minecraft(key.toLowerCase());
            org.bukkit.enchantments.Enchantment ench = org.bukkit.enchantments.Enchantment.getByKey(namespacedKey);
            if (ench != null) {
                return ench;
            }
        } catch (Exception ignored) {}
        
        // 尝试通过名称查找（旧版本兼容）
        try {
            @SuppressWarnings("deprecation")
            org.bukkit.enchantments.Enchantment ench = org.bukkit.enchantments.Enchantment.getByName(key.toUpperCase());
            if (ench != null) {
                return ench;
            }
        } catch (Exception ignored) {}
        
        // 尝试常见的附魔名称映射
        String upperKey = key.toUpperCase().replace(" ", "_");
        try {
            @SuppressWarnings("deprecation")
            org.bukkit.enchantments.Enchantment ench = org.bukkit.enchantments.Enchantment.getByName(upperKey);
            if (ench != null) {
                return ench;
            }
        } catch (Exception ignored) {}
        
        return null;
    }
    
    /**
     * 打开物品展示界面（静态方法，供外部调用）
     */
    private static void openDisplayInventoryStatic(YinwuChat plugin, Player player, ItemStack item, String ownerName) {
        // 需要在主线程执行
        Bukkit.getScheduler().runTask(plugin, () -> {
            String title = DISPLAY_TITLE_PREFIX + " - " + ownerName;
            Inventory display = Bukkit.createInventory(null, InventoryType.DISPENSER, title);
            
            // 使用玻璃板填充其他格子
            ItemStack glass = createGlassPaneStatic();
            for (int i = 0; i < 9; i++) {
                if (i != 4) {
                    display.setItem(i, glass);
                }
            }
            
            // 在中间位置放置展示的物品
            display.setItem(4, item.clone());
            
            // 打开界面
            player.openInventory(display);
            
            plugin.getLogger().info("Player " + player.getName() + " viewing cross-server item from " + ownerName);
        });
    }
    
    /**
     * 打开物品展示界面
     * 使用类似发射器的 3x3 格界面
     */
    private void openDisplayInventory(Player player, ItemStack item, String ownerName) {
        // 创建一个发射器类型的 3x3 界面
        String title = DISPLAY_TITLE_PREFIX + " - " + ownerName;
        Inventory display = Bukkit.createInventory(null, InventoryType.DISPENSER, title);
        
        // 使用玻璃板填充其他格子
        ItemStack glass = createGlassPane();
        for (int i = 0; i < 9; i++) {
            if (i != 4) { // 除了中间格子以外
                display.setItem(i, glass);
            }
        }
        
        // 在中间位置（索引 4）放置展示的物品
        display.setItem(4, item.clone());
        
        // 打开界面
        player.openInventory(display);
        
        plugin.getLogger().info("Player " + player.getName() + " viewing item from " + ownerName);
    }
    
    /**
     * 创建装饰用的玻璃板
     */
    private ItemStack createGlassPane() {
        return createGlassPaneStatic();
    }
    
    /**
     * 创建装饰用的玻璃板（静态方法）
     */
    private static ItemStack createGlassPaneStatic() {
        try {
            // 尝试使用灰色玻璃板
            org.bukkit.Material glassMaterial = org.bukkit.Material.valueOf("GRAY_STAINED_GLASS_PANE");
            ItemStack glass = new ItemStack(glassMaterial);
            org.bukkit.inventory.meta.ItemMeta meta = glass.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(" "); // 空名称
                glass.setItemMeta(meta);
            }
            return glass;
        } catch (Exception e) {
            // 如果失败，返回空
            return null;
        }
    }
    
    /**
     * 待处理的请求
     */
    private static class PendingRequest {
        UUID playerUuid;
        String itemId;
        long requestTime;
    }
}
