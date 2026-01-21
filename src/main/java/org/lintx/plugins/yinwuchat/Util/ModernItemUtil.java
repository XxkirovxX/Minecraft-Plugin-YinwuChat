package org.lintx.plugins.yinwuchat.Util;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * 现代化物品工具类
 * 支持 Minecraft 1.13 - 1.21.1+ 的物品序列化和展示
 * 
 * 版本兼容性:
 * - 1.20.5+: 使用新的 Data Components 系统和 HoverEvent.showItem(Item) API
 * - 1.13-1.20.4: 使用传统的 NBT 序列化方法
 */
public class ModernItemUtil {
    
    // 版本检测缓存
    private static Boolean supportsDataComponents = null;
    private static Boolean supportsNewHoverEvent = null;
    private static int serverVersion = -1;
    
    // Paper UnsafeValues 缓存
    private static Object cachedUnsafeValues = null;
    private static Method cachedSerializeItemMethod = null;
    
    // 1.21+ 使用的新方法缓存 (预留扩展)
    @SuppressWarnings("unused")
    private static Method cachedGetItemDataMethod = null;
    @SuppressWarnings("unused") 
    private static Method cachedAsHoverEventMethod = null;
    
    /**
     * 获取服务器主版本号 (例如: 1.20 返回 20, 1.21 返回 21)
     */
    private static int getServerVersion() {
        if (serverVersion == -1) {
            try {
                String version = Bukkit.getBukkitVersion();
                // 格式如: 1.21.1-R0.1-SNAPSHOT
                String[] parts = version.split("-")[0].split("\\.");
                if (parts.length >= 2) {
                    serverVersion = Integer.parseInt(parts[1]);
                } else {
                    serverVersion = 0;
                }
            } catch (Exception e) {
                serverVersion = 0;
            }
        }
        return serverVersion;
    }
    
    /**
     * 检查是否支持 Data Components (1.20.5+)
     */
    private static boolean supportsDataComponents() {
        if (supportsDataComponents != null) {
            return supportsDataComponents;
        }
        
        int version = getServerVersion();
        if (version < 20) {
            supportsDataComponents = false;
            return false;
        }
        
        // 1.20.5+ 才支持 Data Components
        if (version == 20) {
            // 检查是否是 1.20.5+
            try {
                String fullVersion = Bukkit.getBukkitVersion();
                String[] parts = fullVersion.split("-")[0].split("\\.");
                if (parts.length >= 3) {
                    int patch = Integer.parseInt(parts[2]);
                    supportsDataComponents = patch >= 5;
                } else {
                    supportsDataComponents = false;
                }
            } catch (Exception e) {
                supportsDataComponents = false;
            }
        } else {
            // 1.21+ 肯定支持
            supportsDataComponents = true;
        }
        
        return supportsDataComponents;
    }
    
    /**
     * 检查是否支持新的 HoverEvent API (BungeeCord-Chat 1.20+)
     */
    private static boolean supportsNewHoverEvent() {
        if (supportsNewHoverEvent != null) {
            return supportsNewHoverEvent;
        }
        
        try {
            // 检查是否存在 Item 类和新的 HoverEvent.showItem 方法
            Class.forName("net.md_5.bungee.api.chat.hover.content.Item");
            supportsNewHoverEvent = true;
        } catch (ClassNotFoundException e) {
            supportsNewHoverEvent = false;
        }
        
        return supportsNewHoverEvent;
    }
    
    /**
     * 检查是否支持 Paper 的现代序列化方法
     */
    private static boolean supportsModernSerialization() {
        if (!supportsDataComponents()) {
            return false;
        }
        
        if (cachedUnsafeValues != null && cachedSerializeItemMethod != null) {
            return true;
        }

        try {
            Object server = Bukkit.getServer();
            if (server != null) {
                Method getUnsafeValuesMethod = server.getClass().getMethod("getUnsafe");
                Object unsafeValues = getUnsafeValuesMethod.invoke(server);

                // 尝试获取 serializeItem 或 serializeItemAsBytes 方法
                Method serializeItemMethod = null;
                try {
                    serializeItemMethod = unsafeValues.getClass().getMethod("serializeItem", ItemStack.class);
                } catch (NoSuchMethodException e) {
                    // 1.21+ 可能使用不同的方法名
                    try {
                        serializeItemMethod = unsafeValues.getClass().getMethod("serializeItemAsBytes", ItemStack.class);
                    } catch (NoSuchMethodException ignored) {}
                }

                if (serializeItemMethod != null) {
                    cachedUnsafeValues = unsafeValues;
                    cachedSerializeItemMethod = serializeItemMethod;
                    return true;
                }
            }
        } catch (Exception ignored) {}

        return false;
    }
    
    /**
     * 将 ItemStack 转换为 JSON 字符串（兼容新旧版本）
     */
    private static String convertItemToJson(ItemStack itemStack) {
        // 优先使用现代方法
        if (supportsModernSerialization()) {
            String result = convertItemToJsonModern(itemStack);
            if (result != null) {
                return result;
            }
        }
        
        // 回退到传统方法
            return convertItemToJsonLegacy(itemStack);
    }
    
    /**
     * 现代版本的物品序列化方法 (1.20.5+)
     */
    private static String convertItemToJsonModern(ItemStack itemStack) {
        try {
            if (cachedSerializeItemMethod != null && cachedUnsafeValues != null) {
                Object serializedItem = cachedSerializeItemMethod.invoke(cachedUnsafeValues, itemStack);
                if (serializedItem instanceof byte[]) {
                    // 如果返回的是字节数组，需要转换
                    return new String((byte[]) serializedItem, java.nio.charset.StandardCharsets.UTF_8);
                }
                return serializedItem.toString();
            }
            return null;
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING, "Failed to serialize itemstack using modern method", e);
            return null;
        }
    }
    
    /**
     * 传统版本的物品序列化方法 (1.13-1.20.4)
     */
    private static String convertItemToJsonLegacy(ItemStack itemStack) {
        Class<?> craftItemStackClazz = ReflectionUtil.getOBCClass("inventory.CraftItemStack");
        if (craftItemStackClazz == null) {
            return createFallbackItemJson(itemStack);
        }
        
        Method asNMSCopyMethod = ReflectionUtil.getMethod(craftItemStackClazz, "asNMSCopy", ItemStack.class);
        if (asNMSCopyMethod == null) {
            return createFallbackItemJson(itemStack);
        }

        Class<?> nmsItemStackClazz = ReflectionUtil.getNMSClass("ItemStack");
        if (nmsItemStackClazz == null) {
            return createFallbackItemJson(itemStack);
        }
        
        Class<?> nbtTagCompoundClazz = ReflectionUtil.getNMSClass("NBTTagCompound");
        if (nbtTagCompoundClazz == null) {
            return createFallbackItemJson(itemStack);
        }
        
        // 尝试多种方法名
        Method saveNmsItemStackMethod = ReflectionUtil.getMethod(nmsItemStackClazz, "save", nbtTagCompoundClazz);
        if (saveNmsItemStackMethod == null) {
            saveNmsItemStackMethod = ReflectionUtil.getMethod(nmsItemStackClazz, "b", nbtTagCompoundClazz);
        }
        if (saveNmsItemStackMethod == null) {
            return createFallbackItemJson(itemStack);
        }

        try {
            Object nmsNbtTagCompoundObj = nbtTagCompoundClazz.getDeclaredConstructor().newInstance();
            Object nmsItemStackObj = asNMSCopyMethod.invoke(null, itemStack);
            Object itemAsJsonObject = saveNmsItemStackMethod.invoke(nmsItemStackObj, nmsNbtTagCompoundObj);
            return itemAsJsonObject.toString();
        } catch (Throwable t) {
            Bukkit.getLogger().log(Level.WARNING, "Failed to serialize itemstack using legacy method", t);
            return createFallbackItemJson(itemStack);
        }
    }
    
    /**
     * 创建备用的物品 JSON（当其他方法都失败时）
     */
    private static String createFallbackItemJson(ItemStack itemStack) {
        // 返回最基本的物品信息
        String itemId = getItemId(itemStack);
        int count = itemStack.getAmount();
        return String.format("{\"id\":\"%s\",\"count\":%d}", itemId, count);
    }

    /**
     * 获取物品的显示名称
     */
    private static BaseComponent getItemComponent(ItemStack itemStack) {
        TextComponent component = new TextComponent();
        if (itemStack.hasItemMeta()) {
            ItemMeta itemMeta = itemStack.getItemMeta();
            if (itemMeta.hasDisplayName()) {
                TextComponent textComponent = new TextComponent(itemMeta.getDisplayName());
                component.addExtra(textComponent);
                return component;
            }
        }
        
        // 使用翻译键作为显示名称（更好的本地化支持）
        String translationKey = getTranslationKey(itemStack);
        if (translationKey != null) {
            net.md_5.bungee.api.chat.TranslatableComponent transComponent = 
                new net.md_5.bungee.api.chat.TranslatableComponent(translationKey);
            component.addExtra(transComponent);
            return component;
        }
        
        // 使用 Material 名称作为最终备选
        String materialName = formatMaterialName(itemStack.getType().name());
        TextComponent textComponent = new TextComponent(materialName);
        component.addExtra(textComponent);
        return component;
    }

    /**
     * 获取物品的翻译键
     */
    private static String getTranslationKey(ItemStack itemStack) {
        try {
            // 尝试使用 Paper API 获取翻译键 (1.16+)
            Method getTranslationKeyMethod = itemStack.getClass().getMethod("translationKey");
            Object key = getTranslationKeyMethod.invoke(itemStack);
            return key.toString();
        } catch (Exception e1) {
            try {
                // 尝试从 ItemMeta 获取 (某些版本)
                if (itemStack.hasItemMeta()) {
                    ItemMeta meta = itemStack.getItemMeta();
                    Method getTransKeyMethod = meta.getClass().getMethod("getLocalizedName");
                    Object localized = getTransKeyMethod.invoke(meta);
                    if (localized != null && !localized.toString().isEmpty()) {
                        return localized.toString();
                    }
                }
            } catch (Exception ignored) {}
            
            // 构造默认翻译键
            String key = getItemKeyName(itemStack.getType());
            if (itemStack.getType().isBlock()) {
                return "block.minecraft." + key;
            } else {
                return "item.minecraft." + key;
            }
        }
    }
    
    /**
     * 获取 Material 的 key 名称（兼容新旧API）
     */
    private static String getItemKeyName(Material material) {
        try {
            Method getKeyMethod = material.getClass().getMethod("getKey");
            Object key = getKeyMethod.invoke(material);
            Method getKeyNameMethod = key.getClass().getMethod("getKey");
            return getKeyNameMethod.invoke(key).toString();
        } catch (Exception e) {
            return material.name().toLowerCase();
        }
    }
    
    /**
     * 格式化 Material 名称（将下划线转换为空格，首字母大写）
     */
    private static String formatMaterialName(String name) {
        String[] words = name.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                if (result.length() > 0) result.append(" ");
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1));
                }
            }
        }
        return result.toString();
    }

    /**
     * 创建带有悬停效果的物品组件（支持 1.13-1.21.1+）
     */
    public static BaseComponent componentWithPlayer(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().equals(Material.AIR)) {
            return null;
        }
        
        ItemStack item = itemStack.clone();
        
        // 处理书本 - 清空页面内容以减少数据量
        try {
            if (item.getType().equals(Material.WRITABLE_BOOK) || item.getType().equals(Material.WRITTEN_BOOK)) {
                BookMeta bm = (BookMeta) item.getItemMeta();
                bm.setPages(Collections.emptyList());
                item.setItemMeta(bm);
            }
        } catch (Exception | Error ignored) {}
        
        // 处理潜影盒 - 简化内容
        try {
            if (isShulkerBox(item.getType())) {
                if (item.hasItemMeta()) {
                    BlockStateMeta bsm = (BlockStateMeta) item.getItemMeta();
                    if (bsm.hasBlockState()) {
                        ShulkerBox sb = (ShulkerBox) bsm.getBlockState();
                        for (ItemStack i : sb.getInventory()) {
                            if (i == null || i.getType().equals(Material.AIR) || !i.hasItemMeta()) {
                                continue;
                            }
                            ItemMeta im = Bukkit.getItemFactory().getItemMeta(i.getType());
                            ItemMeta original = i.getItemMeta();
                            if (original != null && original.hasDisplayName()) {
                                im.setDisplayName(original.getDisplayName());
                            }
                            i.setItemMeta(im);
                        }
                        bsm.setBlockState(sb);
                    }
                    item.setItemMeta(bsm);
                }
            }
        } catch (Exception | Error ignored) {}

        // 构建显示文本
        TextComponent component = new TextComponent("");
        component.addExtra("§r§7[§r");
        
        try {
            component.addExtra(getItemComponent(item));
        } catch (Exception | Error e) {
            component.addExtra(formatMaterialName(item.getType().name()));
        }
        
        if (item.getAmount() > 1) {
            component.addExtra(" x" + item.getAmount());
        }
        component.addExtra("§r§7]§r");

        // 设置悬停事件
        setItemHoverEvent(component, itemStack);

        return component;
    }
    
    /**
     * 检查是否是潜影盒
     */
    private static boolean isShulkerBox(Material material) {
        String name = material.name();
        return name.endsWith("SHULKER_BOX");
    }
    
    /**
     * 获取物品ID（兼容新旧API）
     */
    private static String getItemId(ItemStack itemStack) {
        try {
            // 尝试使用新API
            Material type = itemStack.getType();
            Method getKeyMethod = type.getClass().getMethod("getKey");
            Object key = getKeyMethod.invoke(type);
            return key.toString();
        } catch (Exception e) {
            // 回退到Material名称
            return "minecraft:" + itemStack.getType().name().toLowerCase();
        }
    }
    
    /**
     * 设置物品悬停事件（兼容多个版本）
     */
    private static void setItemHoverEvent(TextComponent component, ItemStack itemStack) {
        // 方法1: 尝试使用新的 Content-based HoverEvent API (BungeeCord-Chat 1.16+)
        if (supportsNewHoverEvent()) {
            try {
                String itemId = getItemId(itemStack);
                int count = itemStack.getAmount();
                
                // 获取物品的 NBT/组件数据
                String itemTag = null;
                String itemJson = convertItemToJson(itemStack);
                if (itemJson != null && !itemJson.isEmpty()) {
                    // 提取 tag 或 components 部分
                    itemTag = extractItemTag(itemJson);
                }
                
                // 使用反射创建 Item 和 ItemTag，因为这些类可能不存在于旧版本
                Class<?> itemClass = Class.forName("net.md_5.bungee.api.chat.hover.content.Item");
                Object itemContent;
                
                if (itemTag != null && !itemTag.isEmpty()) {
                    Class<?> itemTagClass = Class.forName("net.md_5.bungee.api.chat.hover.content.ItemTag");
                    Method ofNbtMethod = itemTagClass.getMethod("ofNbt", String.class);
                    Object itemTagObj = ofNbtMethod.invoke(null, itemTag);
                    
                    java.lang.reflect.Constructor<?> itemConstructor = itemClass.getConstructor(String.class, int.class, itemTagClass);
                    itemContent = itemConstructor.newInstance(itemId, count, itemTagObj);
                } else {
                    java.lang.reflect.Constructor<?> itemConstructor = itemClass.getConstructor(String.class, int.class);
                    itemContent = itemConstructor.newInstance(itemId, count);
                }
                
                // 创建 HoverEvent
                Class<?> contentClass = Class.forName("net.md_5.bungee.api.chat.hover.content.Content");
                java.lang.reflect.Constructor<HoverEvent> hoverConstructor = 
                    HoverEvent.class.getConstructor(HoverEvent.Action.class, contentClass.arrayType());
                Object contentArray = java.lang.reflect.Array.newInstance(contentClass, 1);
                java.lang.reflect.Array.set(contentArray, 0, itemContent);
                HoverEvent hoverEvent = hoverConstructor.newInstance(HoverEvent.Action.SHOW_ITEM, contentArray);
                
                component.setHoverEvent(hoverEvent);
                return;
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.FINE, "Failed to use new HoverEvent API, falling back", e);
            }
        }
        
        // 方法2: 使用传统的 JsonArray-based HoverEvent
        try {
            String itemJson = convertItemToJson(itemStack);
            if (itemJson == null || itemJson.isEmpty()) {
                return;
            }
            
            // 使用反射创建旧版 HoverEvent
            com.google.gson.JsonArray jsonArray = new com.google.gson.JsonArray();
            jsonArray.add(itemJson);
            
            java.lang.reflect.Constructor<HoverEvent> constructor = 
                HoverEvent.class.getDeclaredConstructor(HoverEvent.Action.class, com.google.gson.JsonArray.class);
            HoverEvent event = constructor.newInstance(HoverEvent.Action.SHOW_ITEM, jsonArray);
            component.setHoverEvent(event);
        } catch (Exception e) {
            // 最终回退: 使用文本悬停显示物品信息
            try {
                String displayText = getItemDisplayText(itemStack);
                TextComponent hoverText = new TextComponent(displayText);
                // 使用反射创建 Text Content（如果可用）
                try {
                    Class<?> textClass = Class.forName("net.md_5.bungee.api.chat.hover.content.Text");
                    java.lang.reflect.Constructor<?> textConstructor = textClass.getConstructor(BaseComponent[].class);
                    Object textContent = textConstructor.newInstance((Object) new BaseComponent[]{hoverText});
                    
                    Class<?> contentClass = Class.forName("net.md_5.bungee.api.chat.hover.content.Content");
                    java.lang.reflect.Constructor<HoverEvent> hoverConstructor = 
                        HoverEvent.class.getConstructor(HoverEvent.Action.class, contentClass.arrayType());
                    Object contentArray = java.lang.reflect.Array.newInstance(contentClass, 1);
                    java.lang.reflect.Array.set(contentArray, 0, textContent);
                    HoverEvent hoverEvent = hoverConstructor.newInstance(HoverEvent.Action.SHOW_TEXT, contentArray);
                    component.setHoverEvent(hoverEvent);
                } catch (Exception e2) {
                    // 使用旧版 API
                    BaseComponent[] hoverComponents = new BaseComponent[]{hoverText};
                    @SuppressWarnings("deprecation")
                    HoverEvent event = new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverComponents);
                    component.setHoverEvent(event);
                }
            } catch (Exception ignored) {}
        }
    }
    
    /**
     * 从物品 JSON 中提取 tag/components 数据
     */
    private static String extractItemTag(String itemJson) {
        if (itemJson == null || itemJson.isEmpty()) {
            return null;
        }
        try {
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(itemJson).getAsJsonObject();
            
            // 1.20.5+ 使用 "components"
            if (json.has("components")) {
                return json.get("components").toString();
            }
            
            // 1.20.4 及更早版本使用 "tag"
            if (json.has("tag")) {
                return json.get("tag").toString();
            }
            
            // 如果没有额外数据，返回整个 JSON（除了基本字段）
            json.remove("id");
            json.remove("Count");
            json.remove("count");
            if (json.size() > 0) {
                return json.toString();
            }
        } catch (Exception ignored) {
            // JSON 解析失败时，尝试从 SNBT 中提取 tag/components
            return extractItemTagFromSnbt(itemJson);
        }
        
        return null;
    }

    /**
     * 从 SNBT 字符串中提取 tag/components 数据
     */
    private static String extractItemTagFromSnbt(String snbt) {
        String key = null;
        int componentsIndex = snbt.indexOf("components:");
        int tagIndex = snbt.indexOf("tag:");
        if (componentsIndex >= 0 && (tagIndex < 0 || componentsIndex < tagIndex)) {
            key = "components:";
        } else if (tagIndex >= 0) {
            key = "tag:";
        }
        if (key == null) {
            return null;
        }
        int start = snbt.indexOf('{', snbt.indexOf(key));
        if (start < 0) {
            return null;
        }
        int depth = 0;
        for (int i = start; i < snbt.length(); i++) {
            char c = snbt.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return snbt.substring(start, i + 1);
                }
            }
        }
        return null;
    }
    
    /**
     * 获取物品的显示文本（用于文本悬停备选方案）
     */
    private static String getItemDisplayText(ItemStack itemStack) {
        StringBuilder sb = new StringBuilder();
        
        // 物品名称
        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
            sb.append(itemStack.getItemMeta().getDisplayName());
        } else {
            sb.append(formatMaterialName(itemStack.getType().name()));
        }
        
        // 数量
        if (itemStack.getAmount() > 1) {
            sb.append(" x").append(itemStack.getAmount());
        }
        
        // Lore
        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasLore()) {
            List<String> lore = itemStack.getItemMeta().getLore();
            if (lore != null) {
                for (String line : lore) {
                    sb.append("\n").append(line);
                }
            }
        }
        
        return sb.toString();
    }

    /**
     * 获取物品的 JSON 表示（用于序列化传输）
     */
    public static String itemJsonWithPlayer(ItemStack itemStack) {
        BaseComponent component = componentWithPlayer(itemStack);
        if (component == null) return null;
        return ComponentSerializer.toString(component);
    }
    
    /**
     * 获取物品的简化数据（用于跨服传输）
     * 包含: id, count, displayName, lore, enchantments, nbt, fullItemData
     */
    public static String getItemDataForTransfer(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().equals(Material.AIR)) {
            return null;
        }
        
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        
        // 基本信息
        json.addProperty("id", getItemId(itemStack));
        json.addProperty("count", itemStack.getAmount());
        
        // 显示名称
        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
            json.addProperty("displayName", itemStack.getItemMeta().getDisplayName());
        }
        
        // Lore
        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasLore()) {
            com.google.gson.JsonArray loreArray = new com.google.gson.JsonArray();
            List<String> lore = itemStack.getItemMeta().getLore();
            if (lore != null) {
                for (String line : lore) {
                    loreArray.add(line);
                }
            }
            json.add("lore", loreArray);
        }
        
        // 附魔
        if (!itemStack.getEnchantments().isEmpty()) {
            com.google.gson.JsonObject enchants = new com.google.gson.JsonObject();
            itemStack.getEnchantments().forEach((ench, level) -> {
                // 使用反射获取附魔 key
                String enchKey;
                try {
                    Method getKeyMethod = ench.getClass().getMethod("getKey");
                    enchKey = getKeyMethod.invoke(ench).toString();
                    // 确保 key 格式正确（minecraft:knockback -> knockback）
                    if (enchKey.startsWith("minecraft:")) {
                        enchKey = enchKey.substring(10);
                    }
                } catch (Exception e) {
                    // 使用附魔名称作为备选，但要清理格式
                    enchKey = ench.toString().toLowerCase().replace("enchantment{", "").replace("}", "");
                    if (enchKey.contains("minecraft:")) {
                        enchKey = enchKey.substring(enchKey.lastIndexOf(":") + 1);
                    }
                }
                enchants.addProperty(enchKey, level);
            });
            json.add("enchantments", enchants);
        }
        
        // 尝试添加 NBT/组件数据（用于原版 Hover）
        String fullJson = convertItemToJson(itemStack);
        String itemTag = extractItemTag(fullJson);
        if (itemTag != null && !itemTag.isEmpty()) {
            json.addProperty("nbt", itemTag);
        }
        
        // 添加完整的序列化数据（用于跨服完整恢复物品，支持插件自定义物品）
        String fullItemData = serializeItemFully(itemStack);
        if (fullItemData != null && !fullItemData.isEmpty()) {
            json.addProperty("fullItemData", fullItemData);
        }
        
        return json.toString();
    }
    
    /**
     * 完整序列化物品（包含所有 NBT 数据）
     * 使用 Bukkit 的序列化 API，确保插件自定义物品的完整性
     */
    private static String serializeItemFully(ItemStack itemStack) {
        try {
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            org.bukkit.util.io.BukkitObjectOutputStream dataOutput = new org.bukkit.util.io.BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(itemStack);
            dataOutput.close();
            return java.util.Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.FINE, "Failed to fully serialize item", e);
            return null;
        }
    }
}