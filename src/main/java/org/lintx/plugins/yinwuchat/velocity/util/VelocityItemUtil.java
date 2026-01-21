package org.lintx.plugins.yinwuchat.velocity.util;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Velocity 物品工具类
 * 用于处理物品信息的显示和悬停效果
 * 参考 InteractiveChat 的实现方式
 */
public class VelocityItemUtil {

    /**
     * 创建物品显示组件
     * @param itemJson 物品的 JSON 字符串
     * @param itemName 物品名称
     * @param amount 物品数量
     * @return 带有悬停效果和点击事件的 Component
     */
    public static Component createItemComponent(String itemJson, String itemName, int amount) {
        // 创建基础的物品文本
        Component itemText = Component.text("[")
            .color(NamedTextColor.GRAY)
            .append(Component.text(itemName)
                .color(NamedTextColor.WHITE))
            .append(Component.text("]"))
            .color(NamedTextColor.GRAY);

        // 如果数量大于1，添加数量显示
        if (amount > 1) {
            itemText = itemText.append(Component.text(" x" + amount)
                .color(NamedTextColor.YELLOW));
        }

        // 创建悬停事件 - 优先使用原版物品展示格式
        HoverEvent<?> hoverEvent = createVanillaHoverEvent(itemJson, amount);
        if (hoverEvent == null) {
        Component hoverText = createItemHoverText(itemJson, itemName, amount);
            hoverEvent = HoverEvent.showText(hoverText);
        }
        itemText = itemText.hoverEvent(hoverEvent);
        
        // 尝试提取物品展示ID并添加点击事件
        String displayId = extractDisplayId(itemJson);
        if (displayId != null && !displayId.isEmpty()) {
            // 添加点击事件 - 执行 /viewitem 命令来查看物品
            itemText = itemText.clickEvent(ClickEvent.runCommand("/viewitem " + displayId));
        }
        
        return itemText;
    }
    
    /**
     * 从物品 JSON 中提取展示ID
     * @param itemJson 物品的 JSON 字符串
     * @return 展示ID，如果不存在则返回 null
     */
    private static String extractDisplayId(String itemJson) {
        if (itemJson == null || itemJson.isEmpty()) {
            return null;
        }
        
        try {
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(itemJson).getAsJsonObject();
            if (json.has("displayId")) {
                return json.get("displayId").getAsString();
            }
        } catch (Exception e) {
            // 解析失败，返回 null
        }
        
        return null;
    }

    /**
     * 创建原版风格的物品 Hover 展示
     * 注意：原版 ShowItem 需要正确格式的 SNBT 数据，而我们的数据可能是 JSON 格式
     * 如果有自定义名称、lore 或附魔，优先使用文本悬停以确保正确显示
     */
    private static HoverEvent<?> createVanillaHoverEvent(String itemJson, int amount) {
        if (itemJson == null || itemJson.isEmpty()) {
            return null;
        }
        
        try {
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(itemJson).getAsJsonObject();
            
            // 如果有自定义显示名称、lore 或附魔，优先使用文本悬停以正确显示
            // 因为原版 ShowItem 的 NBT 解析在跨版本时可能不稳定
            boolean hasCustomData = json.has("displayName") || json.has("lore") || json.has("enchantments");
            if (hasCustomData) {
                // 返回 null 让调用者使用文本悬停
                return null;
            }
            
            // 对于普通物品（无自定义数据），尝试使用原版 ShowItem
            String itemId = extractItemId(itemJson);
            if (itemId == null || itemId.isEmpty()) {
                return null;
            }
            
            String itemTag = extractItemTag(itemJson);
            HoverEvent.ShowItem showItem;
            if (itemTag != null && !itemTag.isEmpty()) {
                @SuppressWarnings("deprecation")
                BinaryTagHolder tagHolder = BinaryTagHolder.of(itemTag);
                @SuppressWarnings("deprecation")
                HoverEvent.ShowItem tempShowItem = HoverEvent.ShowItem.of(Key.key(itemId), amount, tagHolder);
                showItem = tempShowItem;
            } else {
                @SuppressWarnings("deprecation")
                HoverEvent.ShowItem tempShowItem = HoverEvent.ShowItem.of(Key.key(itemId), amount);
                showItem = tempShowItem;
            }
            return HoverEvent.showItem(showItem);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从传输 JSON 中提取物品 ID
     */
    private static String extractItemId(String itemJson) {
        if (itemJson == null || itemJson.isEmpty()) {
            return null;
        }
        try {
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(itemJson).getAsJsonObject();
            if (json.has("id")) {
                return json.get("id").getAsString();
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 从传输 JSON 中提取 NBT/组件数据
     */
    private static String extractItemTag(String itemJson) {
        if (itemJson == null || itemJson.isEmpty()) {
            return null;
        }
        try {
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(itemJson).getAsJsonObject();
            if (json.has("nbt")) {
                return json.get("nbt").getAsString();
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 创建物品悬停文本
     * 参考 InteractiveChat 的悬停信息显示方式
     * 支持解析 ModernItemUtil.getItemDataForTransfer 返回的 JSON 格式
     */
    private static Component createItemHoverText(String itemJson, String itemName, int amount) {
        // 先尝试从 JSON 中获取自定义名称
        String customDisplayName = null;
        java.util.List<String> loreLines = null;
        String enchantmentsText = null;
        
        if (itemJson != null && !itemJson.isEmpty()) {
            try {
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(itemJson).getAsJsonObject();
                
                // 提取自定义显示名称
                if (json.has("displayName")) {
                    customDisplayName = json.get("displayName").getAsString();
                }
                
                // 提取 Lore
                if (json.has("lore") && json.get("lore").isJsonArray()) {
                    loreLines = new java.util.ArrayList<>();
                    com.google.gson.JsonArray loreArray = json.getAsJsonArray("lore");
                    for (int i = 0; i < loreArray.size(); i++) {
                        loreLines.add(loreArray.get(i).getAsString());
                    }
                }
                
                // 提取附魔
                enchantmentsText = parseEnchantments(itemJson);
            } catch (Exception ignored) {}
        }
        
        // 使用自定义名称或传入的物品名称
        String displayName = customDisplayName != null ? customDisplayName : itemName;
        
        // 构建悬停组件 - 模拟原版 Minecraft 物品提示样式
        Component hoverComponent = Component.empty();
        
        // 物品名称 - 使用斜体白色（自定义名称）或普通白色
        if (customDisplayName != null) {
            // 自定义名称 - 解析颜色代码并显示
            hoverComponent = hoverComponent.append(parseColoredText(customDisplayName));
        } else {
            hoverComponent = hoverComponent.append(Component.text(displayName, NamedTextColor.WHITE));
        }
        
        // 附魔信息 - 蓝色显示
        if (enchantmentsText != null && !enchantmentsText.isEmpty()) {
            String[] enchants = enchantmentsText.split(", ");
            for (String ench : enchants) {
                hoverComponent = hoverComponent
                    .append(Component.text("\n"))
                    .append(Component.text(ench, NamedTextColor.GRAY));
            }
        }
        
        // Lore - 紫色斜体显示
        if (loreLines != null && !loreLines.isEmpty()) {
            for (String loreLine : loreLines) {
            hoverComponent = hoverComponent
                .append(Component.text("\n"))
                    .append(parseColoredText(loreLine).color(NamedTextColor.DARK_PURPLE).decorate(TextDecoration.ITALIC));
            }
        }

        // 尝试解析更多物品信息（旧格式兼容）
        if (itemJson != null && !itemJson.isEmpty()) {
            try {
                // 解析耐久度
                String damage = extractJsonValue(itemJson, "Damage");
                if (damage != null && !damage.equals("0")) {
                    hoverComponent = hoverComponent
                        .append(Component.text("\n"))
                        .append(Component.text("耐久度: ", NamedTextColor.GRAY))
                        .append(Component.text(damage, NamedTextColor.RED));
                }

                // 解析药水效果
                if (itemJson.contains("\"Potion\"") || itemJson.contains("\"CustomPotionEffects\"")) {
                    hoverComponent = hoverComponent
                        .append(Component.text("\n"))
                        .append(Component.text("药水效果", NamedTextColor.LIGHT_PURPLE));
                }

            } catch (Exception ignored) {}
                }

        // 如果有展示ID，添加点击提示
        String displayId = extractDisplayId(itemJson);
        if (displayId != null && !displayId.isEmpty()) {
                    hoverComponent = hoverComponent
                .append(Component.text("\n"))
                .append(Component.text("点击查看物品详情", NamedTextColor.YELLOW, TextDecoration.ITALIC));
        }

        return hoverComponent;
    }
    
    /**
     * 解析带有 Minecraft 颜色代码的文本
     * 支持 § 和 & 颜色代码
     */
    private static Component parseColoredText(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        
        // 替换 & 为 §
        text = text.replace('&', '§');
        
        Component result = Component.empty();
        StringBuilder currentText = new StringBuilder();
        NamedTextColor currentColor = NamedTextColor.WHITE;
        boolean bold = false;
        boolean italic = false;
        boolean underlined = false;
        boolean strikethrough = false;
        boolean obfuscated = false;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            if (c == '§' && i + 1 < text.length()) {
                // 先添加当前累积的文本
                if (currentText.length() > 0) {
                    Component part = Component.text(currentText.toString()).color(currentColor);
                    if (bold) part = part.decorate(TextDecoration.BOLD);
                    if (italic) part = part.decorate(TextDecoration.ITALIC);
                    if (underlined) part = part.decorate(TextDecoration.UNDERLINED);
                    if (strikethrough) part = part.decorate(TextDecoration.STRIKETHROUGH);
                    if (obfuscated) part = part.decorate(TextDecoration.OBFUSCATED);
                    result = result.append(part);
                    currentText = new StringBuilder();
                }
                
                char code = text.charAt(i + 1);
                i++; // 跳过颜色代码字符
                
                switch (Character.toLowerCase(code)) {
                    case '0': currentColor = NamedTextColor.BLACK; break;
                    case '1': currentColor = NamedTextColor.DARK_BLUE; break;
                    case '2': currentColor = NamedTextColor.DARK_GREEN; break;
                    case '3': currentColor = NamedTextColor.DARK_AQUA; break;
                    case '4': currentColor = NamedTextColor.DARK_RED; break;
                    case '5': currentColor = NamedTextColor.DARK_PURPLE; break;
                    case '6': currentColor = NamedTextColor.GOLD; break;
                    case '7': currentColor = NamedTextColor.GRAY; break;
                    case '8': currentColor = NamedTextColor.DARK_GRAY; break;
                    case '9': currentColor = NamedTextColor.BLUE; break;
                    case 'a': currentColor = NamedTextColor.GREEN; break;
                    case 'b': currentColor = NamedTextColor.AQUA; break;
                    case 'c': currentColor = NamedTextColor.RED; break;
                    case 'd': currentColor = NamedTextColor.LIGHT_PURPLE; break;
                    case 'e': currentColor = NamedTextColor.YELLOW; break;
                    case 'f': currentColor = NamedTextColor.WHITE; break;
                    case 'k': obfuscated = true; break;
                    case 'l': bold = true; break;
                    case 'm': strikethrough = true; break;
                    case 'n': underlined = true; break;
                    case 'o': italic = true; break;
                    case 'r':
                        currentColor = NamedTextColor.WHITE;
                        bold = italic = underlined = strikethrough = obfuscated = false;
                        break;
                }
            } else {
                currentText.append(c);
            }
        }
        
        // 添加剩余的文本
        if (currentText.length() > 0) {
            Component part = Component.text(currentText.toString()).color(currentColor);
            if (bold) part = part.decorate(TextDecoration.BOLD);
            if (italic) part = part.decorate(TextDecoration.ITALIC);
            if (underlined) part = part.decorate(TextDecoration.UNDERLINED);
            if (strikethrough) part = part.decorate(TextDecoration.STRIKETHROUGH);
            if (obfuscated) part = part.decorate(TextDecoration.OBFUSCATED);
            result = result.append(part);
        }
        
        return result;
    }

    /**
     * 提取物品的显示名称
     */
    private static String extractDisplayName(String itemJson) {
        try {
            // 查找 display.Name 字段
            String displayPattern = "\"display\"\\s*:\\s*\\{[^}]*\"Name\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(displayPattern, java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher matcher = pattern.matcher(itemJson);

            if (matcher.find()) {
                return matcher.group(1);
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从物品 JSON 解析物品名称
     * 支持 ModernItemUtil.getItemDataForTransfer 生成的 JSON 格式
     */
    public static String parseItemName(String itemJson) {
        System.out.println("DEBUG VelocityItemUtil: parseItemName called with: " + itemJson);
        if (itemJson == null || itemJson.isEmpty()) {
            System.out.println("DEBUG VelocityItemUtil: itemJson is null or empty");
            return "未知物品";
        }

        try {
            // 使用 Gson 进行更可靠的 JSON 解析
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(itemJson).getAsJsonObject();
            System.out.println("DEBUG VelocityItemUtil: Parsed JSON successfully");

            // 优先使用 displayName（自定义显示名称）
            if (json.has("displayName")) {
                String displayName = json.get("displayName").getAsString();
                System.out.println("DEBUG VelocityItemUtil: Using displayName: " + displayName);
                return displayName;
            }

            // 提取物品 ID（忽略 _raw 等复杂字段）
            String id = null;
            if (json.has("id")) {
                id = json.get("id").getAsString();
                System.out.println("DEBUG VelocityItemUtil: Found item ID: " + id);
            } else {
                // 检查是否是组件序列化格式（没有 id 字段）
                System.out.println("DEBUG VelocityItemUtil: No 'id' field found, checking for component format");
                System.out.println("DEBUG VelocityItemUtil: JSON has hoverEvent: " + json.has("hoverEvent"));
                System.out.println("DEBUG VelocityItemUtil: JSON has text: " + json.has("text"));

                if (json.has("hoverEvent")) {
                    System.out.println("DEBUG VelocityItemUtil: Detected component format, extracting name from hoverEvent");
                    // 尝试从 hoverEvent 中提取物品名称
                    com.google.gson.JsonObject hoverEvent = json.getAsJsonObject("hoverEvent");
                    if (hoverEvent.has("contents") && hoverEvent.get("contents").isJsonArray()) {
                        com.google.gson.JsonArray contents = hoverEvent.getAsJsonArray("contents");
                        System.out.println("DEBUG VelocityItemUtil: Contents array size: " + contents.size());
                        if (contents.size() > 0 && contents.get(0).isJsonObject()) {
                            com.google.gson.JsonObject content = contents.get(0).getAsJsonObject();
                            if (content.has("text")) {
                                String text = content.get("text").getAsString();
                                System.out.println("DEBUG VelocityItemUtil: Extracted name from component: " + text);
                                return text;
                            }
                        }
                    } else {
                        System.out.println("DEBUG VelocityItemUtil: No contents array in hoverEvent");
                    }
                }

                // 如果是简单的 text 字段，直接返回
                if (json.has("text")) {
                    String text = json.get("text").getAsString();
                    System.out.println("DEBUG VelocityItemUtil: Using simple text field: " + text);
                    // 如果是物品组件格式，如 "§7[§f物品§7]"
                    if (text.contains("物品")) {
                        // 尝试从 extra 字段提取实际物品名称
                        if (json.has("extra") && json.get("extra").isJsonArray()) {
                            com.google.gson.JsonArray extra = json.getAsJsonArray("extra");
                            for (int i = 0; i < extra.size(); i++) {
                                if (extra.get(i).isJsonObject()) {
                                    com.google.gson.JsonObject extraObj = extra.get(i).getAsJsonObject();
                                    if (extraObj.has("extra") && extraObj.get("extra").isJsonArray()) {
                                        com.google.gson.JsonArray innerExtra = extraObj.getAsJsonArray("extra");
                                        for (int j = 0; j < innerExtra.size(); j++) {
                                            if (innerExtra.get(j).isJsonObject()) {
                                                com.google.gson.JsonObject innerObj = innerExtra.get(j).getAsJsonObject();
                                                if (innerObj.has("translate")) {
                                                    String translate = innerObj.get("translate").getAsString();
                                                    if (translate.startsWith("block.") || translate.startsWith("item.")) {
                                                        // 从翻译键提取物品名称
                                                        String itemName = translate.substring(translate.lastIndexOf('.') + 1).replace('_', ' ');
                                                        // 首字母大写
                                                        String[] words = itemName.split(" ");
                                                        StringBuilder result = new StringBuilder();
                                                        for (String word : words) {
                                                            if (word.length() > 0) {
                                                                result.append(Character.toUpperCase(word.charAt(0)))
                                                                     .append(word.substring(1).toLowerCase())
                                                                     .append(" ");
                                                            }
                                                        }
                                                        String finalName = result.toString().trim();
                                                        System.out.println("DEBUG VelocityItemUtil: Extracted name from translate: " + finalName);
                                                        return finalName;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        return "物品";
                    }
                    return text;
                }

                System.out.println("DEBUG VelocityItemUtil: No 'id' field found in JSON and not a recognized component format");
            }

            if (id != null && !id.isEmpty()) {
                // 转换 minecraft:stone 这样的 ID 为可读名称
                String formattedName = formatItemName(id);
                System.out.println("DEBUG VelocityItemUtil: Formatted item name: " + formattedName);
                return formattedName;
            }

            // 如果无法解析，返回默认名称
            System.out.println("DEBUG VelocityItemUtil: Returning default name '物品'");
            return "物品";
        } catch (Exception e) {
            // 如果 JSON 解析失败，返回默认名称
            System.out.println("DEBUG VelocityItemUtil: Exception during parsing: " + e.getMessage());
            return "未知物品";
        }
    }

    /**
     * 解析物品的附魔信息
     */
    private static String parseEnchantments(String itemJson) {
        try {
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(itemJson).getAsJsonObject();

            if (json.has("enchantments")) {
                com.google.gson.JsonObject enchantments = json.getAsJsonObject("enchantments");
                java.util.Set<java.util.Map.Entry<String, com.google.gson.JsonElement>> entries = enchantments.entrySet();

                if (!entries.isEmpty()) {
                    StringBuilder result = new StringBuilder();
                    for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : entries) {
                        String enchName = formatEnchantmentName(entry.getKey());
                        int level = entry.getValue().getAsInt();

                        if (result.length() > 0) {
                            result.append(", ");
                        }
                        result.append(enchName).append(" ").append(level);
                    }
                    return result.toString();
                }
            }

            // 检查旧格式的附魔字段
            if (json.has("Enchantments") && json.get("Enchantments").isJsonArray()) {
                com.google.gson.JsonArray enchArray = json.getAsJsonArray("Enchantments");
                if (!enchArray.isEmpty()) {
                    StringBuilder result = new StringBuilder();
                    for (int i = 0; i < enchArray.size(); i++) {
                        com.google.gson.JsonObject ench = enchArray.get(i).getAsJsonObject();
                        String enchName = "未知附魔";
                        int level = 1;

                        if (ench.has("id")) {
                            String id = ench.get("id").getAsString();
                            if (id.startsWith("minecraft:")) {
                                id = id.substring(10);
                            }
                            enchName = formatEnchantmentName(id);
                        }

                        if (ench.has("lvl")) {
                            level = ench.get("lvl").getAsInt();
                        }

                        if (result.length() > 0) {
                            result.append(", ");
                        }
                        result.append(enchName).append(" ").append(level);
                    }
                    return result.toString();
                }
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 格式化附魔名称，使其更易读
     */
    private static String formatEnchantmentName(String enchId) {
        // 常见的附魔名称映射
        switch (enchId.toLowerCase()) {
            case "protection": return "保护";
            case "fire_protection": return "火焰保护";
            case "feather_falling": return "摔落保护";
            case "blast_protection": return "爆炸保护";
            case "projectile_protection": return "弹射物保护";
            case "respiration": return "水下呼吸";
            case "aqua_affinity": return "水下速掘";
            case "thorns": return "荆棘";
            case "depth_strider": return "深海探索者";
            case "frost_walker": return "冰霜行者";
            case "binding_curse": return "绑定诅咒";
            case "soul_speed": return "灵魂疾行";
            case "swift_sneak": return "迅捷潜行";
            case "sharpness": return "锋利";
            case "smite": return "亡灵杀手";
            case "bane_of_arthropods": return "节肢杀手";
            case "knockback": return "击退";
            case "fire_aspect": return "火焰附加";
            case "looting": return "抢夺";
            case "sweeping": return "横扫之刃";
            case "efficiency": return "效率";
            case "silk_touch": return "精准采集";
            case "unbreaking": return "耐久";
            case "fortune": return "时运";
            case "power": return "力量";
            case "punch": return "冲击";
            case "flame": return "火矢";
            case "infinity": return "无限";
            case "luck_of_the_sea": return "海之眷顾";
            case "lure": return "饵钓";
            case "loyalty": return "忠诚";
            case "impaling": return "穿刺";
            case "riptide": return "激流";
            case "channeling": return "引雷";
            case "multishot": return "多重射击";
            case "quick_charge": return "快速装填";
            case "piercing": return "穿透";
            case "density": return "致密";
            case "breach": return "破裂";
            case "wind_burst": return "风爆";
            case "mending": return "经验修补";
            default:
                // 对于未知附魔，将下划线替换为空格并首字母大写
                String[] parts = enchId.split("_");
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < parts.length; i++) {
                    if (i > 0) result.append(" ");
                    String part = parts[i];
                    if (!part.isEmpty()) {
                        result.append(Character.toUpperCase(part.charAt(0)))
                              .append(part.substring(1).toLowerCase());
                    }
                }
                return result.toString();
        }
    }

    /**
     * 从物品 JSON 解析物品数量
     */
    public static int parseItemAmount(String itemJson) {
        if (itemJson == null || itemJson.isEmpty()) {
            return 1;
        }

        try {
            // 使用 Gson 进行更可靠的 JSON 解析
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(itemJson).getAsJsonObject();

            // 尝试不同的字段名（count 或 Count）
            if (json.has("count")) {
                return json.get("count").getAsInt();
            } else if (json.has("Count")) {
                return json.get("Count").getAsInt();
            }

            return 1;
        } catch (Exception e) {
            // 如果 JSON 解析失败，使用正则表达式作为备选方案
            try {
                String countStr = extractJsonValue(itemJson, "count");
                if (countStr == null || countStr.isEmpty()) {
                    countStr = extractJsonValue(itemJson, "Count");
                }
                if (countStr != null && !countStr.isEmpty()) {
                    return Integer.parseInt(countStr);
                }
            } catch (Exception e2) {
                // 完全解析失败
            }
            return 1;
        }
    }

    /**
     * 从 JSON 字符串中提取指定键的值
     */
    private static String extractJsonValue(String json, String key) {
        try {
            String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }

            // 尝试匹配数字值
            pattern = "\"" + key + "\"\\s*:\\s*(\\d+)";
            p = java.util.regex.Pattern.compile(pattern);
            m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 格式化物品名称，使其更易读
     */
    private static String formatItemName(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return "未知物品";
        }

        // 移除 minecraft: 前缀
        if (itemId.startsWith("minecraft:")) {
            itemId = itemId.substring(10);
        }

        // 将下划线替换为空格，并首字母大写
        String[] parts = itemId.split("_");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            if (i > 0) result.append(" ");
            String part = parts[i];
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)))
                      .append(part.substring(1).toLowerCase());
            }
        }

        return result.toString();
    }

    /**
     * 创建背包显示组件
     */
    public static Component createInventoryComponent(String inventoryJson, String playerName) {
        Component invText = Component.text("[" + playerName + "的背包]")
            .color(NamedTextColor.BLUE)
            .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/invsee " + playerName));

        Component hoverText = Component.text("点击查看 " + playerName + " 的完整背包")
            .color(NamedTextColor.GREEN);

        return invText.hoverEvent(HoverEvent.showText(hoverText));
    }

    /**
     * 创建末影箱显示组件
     */
    public static Component createEnderChestComponent(String enderChestJson, String playerName) {
        Component ecText = Component.text("[" + playerName + "的末影箱]")
            .color(NamedTextColor.DARK_PURPLE)
            .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/endersee " + playerName));

        Component hoverText = Component.text("点击查看 " + playerName + " 的末影箱")
            .color(NamedTextColor.LIGHT_PURPLE);

        return ecText.hoverEvent(HoverEvent.showText(hoverText));
    }
}
