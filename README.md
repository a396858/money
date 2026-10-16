# 记账本 MoneyBook

极简本地记账 App，两个版本功能一致：

| | 网页版 (web/) | 安卓原生版 (android/) |
|---|---|---|
| 技术 | HTML + JS（PWA） | Kotlin + Jetpack Compose + Room |
| 安装 | 浏览器打开即用，可加到主屏幕 | 需用 Android Studio 编译安装 |
| 数据存放 | 手机浏览器本地 (localStorage) | 手机应用数据库 (Room/SQLite) |
| 导出 | Excel (.xlsx) / CSV / JSON 备份 | CSV |

功能：
- **打开即记账**：首页顶部就是记账区（金额 + 自带数字键盘），打开 App 直接输金额就行，没有单独的「记一笔」按钮
- **内置数字键盘**：App 自绘的数字键盘（0-9 / . / 00 / ⌫ / 保存），不弹系统输入法，盲敲最快
- **记一笔**：金额、分类、备注、日期、收/支切换；保存后金额和备注自动清空，方便连着记下一笔（分类和日期保留）
- **分类网格**（汇总卡下方，一行 5 个共 4 行）：19 个分类每个都是一个按钮，**点一下即选中该分类**（高亮），键盘输入的账就归到它；末尾「⚡快捷」按钮弹出 8 个一键记账模板
- **分类清单**（顺序即按钮顺序）：餐饮、买菜、零食、百货、医疗、大巴、火车、汽油、路桥、打车、水电、燃气、物业、取暖、房租、保险、给于、工资、其他、快捷
- **快捷模板**：8 个槽位可自定义编辑（名称/金额/分类/备注/收支），点一下立即记录一条账，网页版点 ✎ 编辑
- **流水列表**：按日分组，默认显示最近 10 条，其余折叠在「展开其余 N 条」按钮里
- **月度统计**：本月支出 / 收入 / 结余，可切换月份
- **导出**：全年 Excel 表格（.xlsx）、全年 CSV、完整 JSON 备份与恢复
- 数据 100% 存在本地，不联网、不上传

## 文件清单
```
MoneyBook/
├── start-moneybook.bat        # ★ 双击启动（监听局域网，手机可访问）
├── 手机访问二维码.png          # 手机扫码直接打开
├── web/                       # 网页版（PWA，纯静态）
├── android/                   # 安卓原生工程（Gradle + Kotlin）
└── tools/
    ├── start_server.py        # 启动脚本本体（打印地址 + 开浏览器）
    ├── make_qr.py             # 重新生成手机访问二维码
    ├── test_export.js         # 导出功能回归测试（node tools/test_export.js）
    └── make_icons.py          # 生成 PWA 图标
```

---

## 一、网页版：马上就能用

### 方式 1：电脑浏览器直接打开
双击 `web/index.html` 即可（键盘 1-9、`.`、退格可输入金额，回车保存）。

### 方式 2：手机扫码用起来（推荐，零配置）
1. 双击项目根目录的 **`start-moneybook.bat`** —— 会弹出一个黑窗口，自动打开电脑浏览器；
2. 手机连**同一个 Wi-Fi**，扫码打开：

   ![手机访问二维码](手机访问二维码.png)

   或手动在手机浏览器输入窗口里显示的那行地址（形如 `http://192.168.0.100:8765`）。
3. 首次运行 Windows 可能弹防火墙提示，勾选「专用网络」并点「允许访问」。
4. 用完直接关掉黑窗口即停止服务；**记账数据存在手机浏览器里，不受影响**。

> 局域网是 `http`，浏览器不会启用离线缓存，也不能「添加到主屏幕」。
> 想离线、想装到桌面，需要用下面的方式 3（发布到 https 链接）。

#### 手机打不开时怎么排查

本机已实测过的环境情况（2026-09 记录）：

- 当前 Wi-Fi（SSID `HXZB_5G`）被 Windows 归为 **「公用网络」**；
- Windows 防火墙三个配置文件均为**启用**，入站默认 `BlockInbound`；
- 但防火墙里**已存在 `python.exe` 的入站放行规则**（TCP + UDP、端口“任何”、操作“允许”、配置文件“公用”）。

也就是说按上面步骤操作，手机一般能直接连上。如果打不开，按顺序查：

1. **IP 变了**：重跑 `python tools/make_qr.py`，以 `start-moneybook.bat` 窗口里打印的地址为准；
2. **不同网段**：确认手机连的就是同一个 Wi-Fi（不是手机流量、不是访客网络）；
3. **路由器的客户端隔离**：部分路由器/访客网络开了「AP 隔离 / 客户端隔离」，会阻断设备互访，
   关掉它，或改用方式 3 的公网链接；
4. **防火墙没放行**：`Windows 安全中心 → 防火墙和网络保护 → 允许应用通过防火墙`，
   找到 `python.exe`，把「专用」和「公用」都勾上；或者把当前网络的类型改成「专用网络」；
5. 仍不行就用方式 3 发布到公网，绕开局域网问题。

### 方式 3：发布到公网（离线可用 + 可添加到主屏幕）
把 `web` 文件夹整个传到任意支持 HTTPS 的静态托管（GitHub Pages / Vercel / 对象存储 等），
手机打开后用浏览器菜单「添加到主屏幕」，之后就是离线可用的完整 App。

> 提示：清浏览器缓存/清除网站数据会删掉记账数据。重要数据请定期用右上角「⋮」菜单做 JSON 备份。

### 数据安全
- 右上角「⋮」菜单：导出本月 Excel(.xlsx)、CSV、下载完整备份(JSON)、从备份恢复、清空数据。

### 换了网络怎么办
局域网 IP 会变，重新生成二维码即可：
```bash
python tools/make_qr.py
```
（脚本会自动探测当前 IP。也可以直接看 `start-moneybook.bat` 窗口里打印的地址。）

---

## 二、安卓原生版：需要 Android Studio

### 1. 准备环境
1. 安装 Android Studio（最新稳定版）：<https://developer.android.com/studio>
   - 国内直链可搜「Android Studio 下载 中文官网」： developer.android.google.cn
2. 首次启动时按向导默认安装即可（会自动下载 Android SDK）。
3. 电脑需要能访问谷歌 Maven 仓库（下载依赖时用）。如果网络不通，可在
   `C:\Users\72635\.gradle\gradle.properties` 里加国内镜像，见下方「常见问题」。

### 2. 打开项目
1. 打开 Android Studio → `Open`；
2. 选择 `android` 这个文件夹（注意是含 `settings.gradle.kts` 的那层）；
3. 等待 Gradle Sync 完成（首次会下载 Gradle 8.11.1 和依赖，约 5-15 分钟）。

### 3. 运行
- **真机**：手机打开「开发者选项 → USB 调试」，插上电脑，Android Studio 顶部选中设备，点绿色 ▶ Run；
- **模拟器**：Device Manager 里创建一台虚拟机（推荐 Pixel 系列 + API 35），然后点 ▶ Run。

### 4. 打 APK 安装包
菜单 `Build → Build App Bundle(s) / APK(s) → Build APK(s)`，
完成后点通知里的 `locate` 找到 `app-debug.apk`，传到手机安装即可。

### 项目结构
```
android/
├── app/
│   ├── build.gradle.kts          # 应用模块构建配置
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/moneybook/app/
│       │   ├── MainActivity.kt   # 全部界面（Compose）
│       │   └── data/
│       │       ├── Record.kt     # 数据表实体
│       │       ├── RecordDao.kt  # 数据库操作
│       │       ├── AppDatabase.kt# Room 数据库
│       │       └── Templates.kt  # 8 个快捷记账模板的本地存储
│       └── res/                  # 图标、主题、应用名
├── build.gradle.kts              # 项目级配置（插件版本）
├── settings.gradle.kts
└── gradle/wrapper/               # Gradle Wrapper（已附带 jar）
```

技术栈版本：AGP 8.7.3 / Kotlin 2.0.21 / Compose BOM 2024.12.01 / Room 2.6.1 / minSdk 26 (Android 8.0+)。

### 使用说明（网页版）
- **打开页面就是记账区**（页面顶部）：先敲金额，需要时点下方分类网格选分类、填备注、改日期，最后点键盘右侧「保存」；
- 分类网格：**🍚餐饮 / 🥬买菜 / … / 📦其他** 共 19 个，点一下即选中（蓝框高亮），点「工资」会自动切到收入；最右 **⚡快捷** 打开 8 个一键记账模板；
- 模板槽位点 ✎ 编辑（名称、金额、分类、备注、收支），空槽点「＋ 设置模板」；点模板主体立即记一条账；
- 保存后金额与备注自动清空，分类和日期保留，可以连着记好几笔；
- 流水默认只显示最近 10 条，点「展开其余 N 条 ▾」看全部，切换月份后重新折叠；
- 右上角「⋮」菜单：导出今年 **Excel(.xlsx)**、CSV、JSON 备份/恢复、清空数据。

### 使用说明（原生版）
- 与网页版一致：**打开 App 就是记账区**（顶部金额 + 内置数字键盘），没有单独的「记一笔」按钮；
- 数字键盘是 App 自绘的（0-9 / . / 00 / ⌫ / 保存长条键），不弹系统输入法；
- 分类网格 19 个按钮 + ⚡模板，点一下选中分类，键盘输入的账归到它；
- **长按**流水可删除；流水默认 10 条，点「展开其余 N 条」看全部；
- 右上角分享图标：把今年流水导出为 CSV（WPS / Excel 可直接打开）。

> 原生版导出的是 CSV，不是网页版那种 `.xlsx`。要 xlsx 需要在工程里加
> Apache POI（体积 +10MB 左右），或者自己写一个纯 `java.util.zip` 的
> OOXML 写入器（网页版就是这么做的，两端可对齐）。

### 工程自检记录
本机没有 JDK / Android SDK，无法编译验证，因此对源码做了静态审计，已确认：

- 插件版本互相兼容：AGP 8.7.3 ↔ Gradle 8.11.1 ↔ Kotlin 2.0.21 ↔ KSP 2.0.21-1.0.28；
- `gradle-wrapper.jar` 完整性（zip 有效、含 `GradleWrapperMain`）、`gradlew` / `gradlew.bat` 齐备；
- 主题用平台主题 `android:Theme.Material.Light.NoActionBar`，不依赖 material 组件库；
- 图标只用 material-icons **core** 集合里的（Add / Share / KeyboardArrow*），不依赖 icons-extended；
- 全部 `ModalBottomSheet` / `FlowRow` / `combinedClickable` 的 `@OptIn` 标注齐全
  （曾漏标 `TemplateCard` 的 `ExperimentalFoundationApi`，已修复）；
- Room 实体 / DAO / 枚举 TypeConverter / 单例数据库写法正确；
- `minSdk 26` 满足 `java.time`（LocalDate / YearMonth）的 API 要求；
- 资源引用无悬空：`@style/Theme.MoneyBook`、`@string/app_name`、`@mipmap/ic_launcher`
  及自适应图标引用的两个 drawable 都存在。

### 常见问题
- **依赖下载慢/失败**：编辑 `C:\Users\72635\.gradle\gradle.properties`，加入：
  ```
  systemProp.http.proxyHost=127.0.0.1
  systemProp.http.proxyPort=7890
  systemProp.https.proxyHost=127.0.0.1
  systemProp.https.proxyPort=7890
  ```
  （端口改成你自己的代理端口；不用代理则改用国内镜像源，把 settings.gradle.kts 里
  `google()` / `mavenCentral()` 前加 `maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }`）
- **JDK 版本**：直接用 Android Studio 内置的 JBR 17，无需单独装 Java。

---

## 三、给开发者的提示

想加功能时的扩展点：
- 预算提醒：在 `RecordDao` 加月度聚合查询，首页卡片下加进度条；
- 一键快捷记账：把常用组合（分类+金额+备注）存成模板表，首页加模板按钮；
- 数据同步：`AppDatabase` 换/加导出 JSON 功能即可备份到网盘。

网页版与安卓版分类 ID 保持一致（food / grocery / snack / goods / medical / bus / train / fuel / toll / taxi / utility / gas / property / heating / rent / insurance / give / salary / other），CSV 格式也一致，两边数据可通过 CSV/JSON 互通。

改分类只需改一处：网页版 `web/index.html` 里的 `CATS` 数组，安卓版 `MainActivity.kt` 里的 `CATS` 列表（两者顺序、id 保持一致）。旧分类 id（traffic / shopping / home / fun / study / bonus / redpack / parttime / invest）已做自动映射，老数据不会丢。

### 导出功能的回归测试
改了导出逻辑（分类、列、合计）之后，跑一下：
```bash
node tools/test_export.js
```
它不会用副本，而是**直接从 `web/index.html` 里抽出真实的 `exportXLSX` / `exportCSV`
函数**，喂 10 条数据（含旧分类 id 和 `&` `<` 等特殊字符）跑一遍，断言：

- 生成的是合法 zip（PK 魔数）、只含 5 个标准 OOXML 部件；
- 旧分类 id 全部映射成中文名、不残留英文；
- CSV 带 BOM；分类名正确；
- 分类 id 无重复、总数 19、「给于」收支两种模式都能取到。

产物落在系统临时目录，可以直接用 Excel/WPS 打开肉眼核对。

已实测（2026-09）：11 项断言全通过，导出文件用 `openpyxl` 打开无异常，
金额是**数字型**（可直接 SUM），合计 支出 789.30 / 收入 12866.66 / 结余 12077.36 均正确，
备注里的 `&` `<` 中文逗号都正常转义。
