# YinwuChat Bukkit 后端插件

## 问题说明

当前的构建只产生了 Velocity 版本的 JAR 文件，但物品显示功能需要同时在 Velocity 代理和 Bukkit/Spigot 后端服务器上运行插件。

## 临时解决方案

由于构建系统的问题，请按以下步骤手动创建 Bukkit 版本：

### 步骤 1: 复制现有的 JAR 文件
```bash
copy "C:\Users\kirov\Desktop\Minecraft-Plugin-YinwuChat\Minecraft-Plugin-YinwuChat\target\YinwuChat-2.12.jar" "C:\Users\kirov\Desktop\Minecraft-Plugin-YinwuChat\Bukkit-Plugin\YinwuChat-Bukkit-2.12.jar"
```

### 步骤 2: 创建 plugin.yml
文件已创建在 `Bukkit-Plugin/plugin.yml`

### 步骤 3: 使用工具修改 JAR
您需要使用 ZIP 工具打开 `YinwuChat-Bukkit-2.12.jar`，删除 `velocity-plugin.json`，并添加 `plugin.yml`。

或者，您可以暂时在后端服务器上也安装相同的 JAR 文件（尽管它主要是为 Velocity 设计的），这样至少物品请求能被处理。

## 推荐的完整解决方案

为了正确支持多平台，应该修改 pom.xml 来构建独立的 JAR 文件：
- `YinwuChat-Velocity-2.12.jar` - 仅包含 Velocity 代码
- `YinwuChat-Bukkit-2.12.jar` - 仅包含 Bukkit 代码

但这需要修改 Maven 配置，目前暂时使用上述临时方案。
