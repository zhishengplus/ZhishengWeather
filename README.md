![枳生天气 · ZHISHENG WEATHER TERMINAL](assets/banner.png)

<p align="center">
  <img src="assets/app-icon.png" width="96" alt="枳生天气应用图标"/><br/>
  <b>打开就是天气。</b><br/>
  一款信息密度较高的 Android 天气应用，采用磷光终端风格。没有广告、账号和统计 SDK。
</p>

<p align="center">
  <a href="https://github.com/zhishengplus/ZhishengWeather/releases">
    <img alt="下载公共版 APK" src="https://img.shields.io/badge/下载公共版_APK_·_v0.1.3-FF6F1E?style=for-the-badge&labelColor=10151C"/>
  </a>
</p>

<p align="center">
  <a href="https://github.com/zhishengplus/ZhishengWeather"><img alt="GitHub stars" src="https://img.shields.io/github/stars/zhishengplus/ZhishengWeather?style=flat-square&labelColor=10151C&color=FF6F1E"/></a>
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-31C9DB?style=flat-square"/>
  <img alt="当前版本 0.1.3" src="https://img.shields.io/badge/当前版本-0.1.3-31C9DB?style=flat-square"/>
  <img alt="无广告、账号和埋点" src="https://img.shields.io/badge/广告·账号·埋点-无-31C9DB?style=flat-square"/>
  <img alt="MIT License" src="https://img.shields.io/badge/许可-MIT-31C9DB?style=flat-square"/>
</p>

<p align="center">
  <b>简体中文</b> · <a href="README.en.md">English</a>
</p>

---

## 界面

<table>
  <tr>
    <td align="center"><a href="assets/screenshot-home.jpg"><img src="assets/screenshot-home.jpg" width="160" alt="枳生天气主页"/></a></td>
    <td align="center"><a href="assets/screenshot-details.jpg"><img src="assets/screenshot-details.jpg" width="160" alt="遥测数据与空气质量"/></a></td>
    <td align="center"><a href="assets/screenshot-cities.jpg"><img src="assets/screenshot-cities.jpg" width="160" alt="城市列表"/></a></td>
    <td align="center"><a href="assets/screenshot-add-city.jpg"><img src="assets/screenshot-add-city.jpg" width="160" alt="城市搜索"/></a></td>
    <td align="center"><a href="assets/screenshot-settings.jpg"><img src="assets/screenshot-settings.jpg" width="160" alt="设置"/></a></td>
  </tr>
  <tr>
    <td align="center"><sub>主页</sub></td>
    <td align="center"><sub>遥测与空气</sub></td>
    <td align="center"><sub>城市</sub></td>
    <td align="center"><sub>搜索</sub></td>
    <td align="center"><sub>设置</sub></td>
  </tr>
</table>

<p align="center"><sub>点击截图查看原图。</sub></p>

## 这个项目

我平时打开天气 App，只想尽快确认几件事：现在多少度，什么时候下雨，空气怎么样，明后天会不会突然降温。枳生天气把这些内容放在同一条纵向信息流里，不需要先看开屏广告，也不用登录账号。

界面走的是磷光终端风：黑色背景、细线分区、青色主信号和少量橙色提示。信息排得比较紧，但主次是固定的。实况、预警、逐时和短时降水在前，空气质量、生活指数、月相等内容继续往下滑就能看到。

## 能看什么

- 当前位置或已保存城市的实况天气、体感温度、风向风速和气压
- 24 小时逐时预报、15 天高低温趋势，以及未来两小时降水
- 气象预警、空气质量六项分测和常用生活指数
- 日出日落、月相、月出月落和昨日天气对比
- 多城市收藏，底部长按后可用发牌式卡组连续滑选
- 首页天气模块可自定义顺序
- 4x1 / 2x2 / 4x2 / 2x4 / 4x4 五种 3D 终端桌面小组件
- 长按应用图标可刷新天气、搜索城市或打开设置

每种天气各有一套背景氛围效果，画在读数之下，强度可调，也可以完全关闭。温度、风速和气压单位分别设置；不用的页面模块可以单独隐藏。

数据源没有提供的字段会留空，不用估算值补齐。

## 公开发行版

GitHub Releases 只发布正式公共版 APK，这也是普通用户唯一需要下载的版本。它默认使用免配置的公共数据链路；需要和风或彩云额外内容时，可在应用的实验室中填写自己的凭据，凭据只保存在本机。

仓库中的 `release`、`performance` 和 `previewPublic` 是维护者本地开发、真机覆盖和并行验收使用的构建类型，不作为社区安装包发布。应用内直接更新也只面向正式公共版，确保后续版本可以保持同包名、同签名覆盖安装。

## 数据源怎么工作

项目接入了和风天气、彩云天气、小米公开天气接口、Open-Meteo，以及独立的浙江省水利厅台风路径公开资料。设置页可以选择自动优选，也可以固定使用某个天气数据源，并会显示当前城市实际返回数据的来源。

| 数据源 | 配置 | 主要内容 |
|:--|:--|:--|
| 和风天气 | 实验室接入，或构建期写入 `local.properties` | 实况、预警、逐时逐日、分钟降水、空气质量、生活指数 |
| 彩云天气 | 实验室填写 Token | 实况、逐时逐日、分钟降水、空气质量、预警 |
| 小米公开天气接口 | 不需要 | 国内天气、城市搜索、昨日天气和台风辅助数据；手动选择时不混入其他来源 |
| Open-Meteo | 不需要 | 全球实况、逐时逐日、空气质量、15 分钟降水和缺项补充 |
| 浙江省水利厅台风路径系统 | 不需要 | 西北太平洋台风实况路径、中心强度、风圈与多机构预报 |

自动优选以小米天气为主：实况天气现象和降水判断以小米返回为准，Open-Meteo 只补充小米没有返回的遥测、逐时或逐日内容；仅当小米整次请求失败时才切换到 Open-Meteo。和风与彩云不进入自动链，只在开发者模式锁定后使用。

月相在本机按城市日期计算。数据源没有月出、月落时，应用会根据日期和城市坐标补算，不再发起额外请求。

和风接口使用 Ed25519 签名 JWT，也可填 API KEY。`assemblePublicRelease` 和 `assemblePreviewPublic` 会在构建阶段强制清空编译期凭据；用户在实验室填写的凭据只存在本机，不进备份、不进公共 APK。

## 图标

<p align="center">
  <img src="assets/app-icon.png" width="144" alt="枳生天气应用图标"/>
</p>

0.1.3 默认使用天气娘头像：发卡保留太阳、云和降水组成的经典标识，深色底板、青色信号和橙色提示继续沿用终端界面的视觉语言。喜欢原版图标时，可在「设置 → 界面 → 应用图标」切回经典样式。

<p align="center"><img src="assets/icons_grid.png" width="560" alt="枳生天气图标组"/></p>

应用内还有 15 枚天气图标，覆盖晴、多云、阴、雾、雨、雷暴、雪、风和霰等状态。它们单独绘制，没有从通用图标库拼接。

## 安装

1. 从 [Releases](https://github.com/zhishengplus/ZhishengWeather/releases) 下载最新公共版 APK。
2. 安装到 Android 8.0 或更高版本的设备。
3. 首次打开默认显示北京，可通过搜索保存自己的城市。

APK 只在 GitHub 发布。Android 可能提示允许当前应用安装未知来源文件，这是安装渠道提示，不是枳生天气申请了额外系统权限。

## 0.1.3 更新

- 新增可开关的横屏气象时钟，关闭后应用保持竖屏
- 定位可选择精确位置并尽量显示街道，识别失败时回退到城市
- 自动优选改为小米天气主导，其他公共源只补小米未返回的字段；手动选择时保持单一来源
- 修复和风凭据状态、逐时逐日解析、降水雨强、空气质量和生活指数显示问题
- 和风会优先请求账号套餐支持的较长逐时预报和更多生活指数，未返回的内容不显示
- 修复彩云实况与摘要冲突、逐日天数不足及跨区块数据不一致问题
- 遥测与生活指数均可在开发者模式中逐项选择，卡片按实际数量自适应排满
- 逐日预报增加日号，星期、日期、图标、概率与温度保持固定列宽对齐
- 跨月时增加轻量月份分隔，不占用每天的日期栏
- 短时降水优先显示开始/停止时间、峰值雨势及 30 分钟刻度
- 标明短时降水来源、真实时间粒度和更新时间，不再伪造逐分钟精度
- 无雨时的短时降水卡改为完整的两小时晴窗状态布局
- 实况、逐时「现在」和短时降水共用同一时刻；正在下雨时不再显示晴窗
- 高低温、更新时间和小组件按城市当地日期与时刻显示
- 实况降水按毫米/小时标注；没有日累计的源不再用峰值冒充全天降水量
- 修复南纬、西经坐标方向，并改善单城市主页滚动与小尺寸小组件文字
- 天气氛围增加“强烈”档并改善夜间可见度
- 桌面小组件采用分层半透明玻璃外壳，壁纸可自然透出
- 桌面小组件重新整理边框、字号与对齐，大尺寸增加逐时趋势和生活信息，点击刷新会显示明确状态
- 新增天气娘启动图标，并可在设置中随时切换回经典天气图标
- 主界面延伸到系统手势导航区域，消除底部割裂黑边

## 0.1.1 更新

0.1.1 是基于 0.1.0 的问题修复版本。

- 修复已配置的和风天气偶尔显示为未配置的问题
- 修复和风逐时预报只显示“现在”、彩云逐日预报天数过少的问题
- 统一遥测卡片高度，并补齐可获取的能见度、露点、云量和阵风等信息
- 海外城市的逐时、星期、日期和小组件时间改按城市时区显示
- 修复南纬、西经坐标方向，并改善只保存一个城市时的主页滚动
- 提高小尺寸小组件的日期和更新时间字号

## 0.1.0 更新

0.1.0 是一次覆盖主屏交互、天气效果、数据源和桌面小组件的大更新。

### 多城市与主页

- 保存两个或更多城市后，长按屏幕底部的呼吸光即可打开城市卡组；震动后保持按住左右滑动，松手切换到中央城市
- 卡组出现后向上推至第二次震动，可把卡组固定在屏幕上，随后松手左右浏览并点选城市
- 设置页可调整主页天气模块顺序，并可一键恢复默认排列
- 首次打开 0.1.0 会显示三页更新说明；以后点击「设置 → 关于 → 版本」可再次查看

### 天气效果与界面

- 晴昼、晴夜、多云、阴、雨、雨夹雪、雪、雷暴、雾、霾、沙尘和大风等天气都有独立的终端背景效果，天气变化时平滑过渡
- 下雨时使用数据雨；氛围始终位于天气读数之后，强度可调，也可完全关闭
- 开发者模式新增天气效果预览，可用模拟天气检查全部氛围，不影响主页真实天气
- 设置页重新整理；新增亮度更低的绿、蓝强调色，浅色模式天气图标按太阳、云、降水等语义适配颜色

### 数据与显示

- 新增彩云天气，并为和风 JWT / API KEY、彩云 Token 提供分步配置和连接测试
- 高低温会随时段切换；缺少的数据保持为空，不用估算值补齐
- 统一分钟降水、雨势趋势、降雨概率和风向显示，修复异常百分比、降水卡比例失衡及不同区域说法不一致的问题
- 没雨时隐藏分钟降水卡；体感与气温差距很小时不重复显示体感温度

### 桌面小组件与社区

- 小组件扩展为 4×1、2×2、4×2、2×4、4×4 五种尺寸，采用带呼吸状态灯的终端设备外观
- 修复浅色模式下太阳、云和降水图标仍显示荧光绿的问题
- 关于页新增社区贡献者名单

本版本仍未加入通知功能。

## 0.0.8 更新

本次更新主要修复天气显示不一致的问题。

1. 修复降雨时主界面仍提示「不会下雨」的问题
2. 修复逐时预报将下一小时误标为「现在」的问题
3. 优化短时降水状态，雨停后不再显示「正在下雨」
4. 优化逐日天气展示：白天与夜间天气不同时，按更显著的天气显示，展开可查看如「晴转雷阵雨」
5. 修复阴天被显示为小雨的问题

本版本暂未加入通知功能。

## 0.0.6 更新

打开就能看懂接下来两小时：大温度下面加一句话（何时下雨、预警、明天冷不冷）；降水卡直接写清几点下；公共版小米源补上逐分钟降水柱；点某一天可展开看出日落和月相。顺手关掉 MIUI 强制深色反转，浅色启动不再闪黑。

当前回归检查见单元测试与构建。通知功能本版不做。

## 0.0.5 更新

0.0.5 的重头戏是主题。深色之外新增了浅色「清冷翡翠」——冷灰纸面打底、翡冷翠做数据色，桌面小组件跟着一起换；顺手修了两个藏得比较深的毛病。

- 主题三档：深色 / 浅色 / 跟随系统，切换即时生效（小组件同步换肤在 0.0.5.1 起改为只跟系统深浅）
- 修复「跟随系统」方向反了的问题：系统深色时 App 反而切浅色
- 修复和风天气源预警不按国标四档着色的老问题（此前和风的预警清一色红）
- 设置页新增开源仓库入口，欢迎顺手点个 star
- 文字、图标、逐日温度条等浅色细节整体盘过一遍

当前回归检查包括 46 项单元测试、Android Lint、Debug / 公共版 / 满血版构建。

## 0.0.5.1 更新

小组件小修：放大日期、更新时间、体感湿度与逐时/逐日等偏小字号；重绘添加小组件时的预览图（示范城市改为上海，与真实布局一致）；小组件主题改为只跟随系统深浅，App 内切换主题不再改变桌面小组件。

## 参与项目

可以通过 [GitHub Issues](https://github.com/zhishengplus/ZhishengWeather/issues) 报告可复现的问题。提问时请附上应用版本、手机型号、系统版本、数据源和必要截图，注意遮住 Token、API KEY 等个人凭据。

社区贡献者：`PPQ1028`、`Uinuan1`、`KZzzzo`、`睡觉了寂`、`微生之最`、`r1file`、`vsqesy3721`、`茉莉羽`、`陈大橙`、`飞667`、`一杯冰美式、、`、`M1ralce`、`紅星照耀中國`、`我爱跑步`、`河鱼天雁`、`你的心里没点高数吗`、`周月星斗`、`无敌战神暴王龙`、`control3`、`明珠有泪`、`Gstar_`、`伍拾两HZ`、`寡欲老公猪`。

## 从源码构建

需要 JDK 17 和 Android SDK 34。仓库包含 Gradle Wrapper。

```bash
git clone https://github.com/zhishengplus/ZhishengWeather.git
cd ZhishengWeather
```

不填写凭据时，构建结果使用公共版数据链路。需要和风主源时，在根目录 `local.properties` 中写入 SDK 路径和个人凭据；该文件已被 Git 忽略。

```properties
sdk.dir=<Android SDK 路径>
qw.host=<API Host>
qw.project_id=<Project ID>
qw.kid=<Key ID>
qw.private_key=<Ed25519 私钥，单行>
```

```bash
./gradlew assembleDebug                     # Windows：.\gradlew.bat assembleDebug
./gradlew assembleRelease                   # 维护者本地构建，需配置自己的签名文件
./gradlew assemblePublicRelease             # 公共版，强制清空凭据并使用随库公开证书
./gradlew assemblePreviewPublic             # 维护者本地并行验收包，不上传 Release
./scripts/package_release.ps1               # 生成 GitHub 公共版 APK 及 SHA-256
./scripts/package_release.ps1 -IncludeDevelopmentBuilds  # 另打包本地开发构建
```

随库的 `keystore/public.jks` 只用于保持公共版之间可以覆盖安装，不是私有签名身份。

主要技术栈：Kotlin 2.0.21、Jetpack Compose、Material 3、ViewModel / StateFlow、Retrofit、OkHttp、kotlinx-serialization、DataStore 和 BouncyCastle。`minSdk 26`，`targetSdk 34`。代码结构与提交约定见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 权限和数据

应用使用网络、安装更新和可选位置权限：

| 权限 | 用途 |
|:--|:--|
| 网络访问 | 请求天气和城市搜索数据 |
| 网络状态 | 判断设备是否联网 |
| 安装应用 | 仅在正式公共版中手动下载更新后打开系统安装页；不会静默安装 |
| 粗略位置 | 可选；仅在开启定位并主动重新定位时申请 |
| 精确位置 | 可选；只有主动开启街道级定位时才申请 |

应用没有广告 SDK、统计埋点、账号系统或自建后端。城市列表和设置保存在本机。天气请求会把所选城市坐标发送给当前数据源；使用定位时，坐标还用于反查城市名。

定位开启并授权后，应用回到前台会按间隔复核所在城市，不在后台持续获取位置。相关代码位于 [`app/src/main/kotlin/com/zhisheng/weather/data`](app/src/main/kotlin/com/zhisheng/weather/data)。

## 已知限制

- 公共版默认不含和风凭据；可在实验室自行接入。官方预警和生活指数取决于账号权限
- Open-Meteo 的短时降水为 15 分钟粒度，不是逐分钟雷达临近预报
- 台风路径使用独立官方公开资料并保留最近有效缓存；来源暂不可用或资料超过时效时会明确提示，不作为预警发布依据
- 跨数据源预警按标题去重；标题不同的同一条预警可能重复出现
- 项目仍处于早期版本，涉及防灾决策时请以当地气象部门信息为准

## 更新记录

<details open>
<summary><b>0.1.3 // STABLE RELEASE</b></summary>

- 新增横屏气象时钟、街道级定位、遥测与生活指数自选，以及更明显的夜间氛围
- 自动优选以小米天气为主，修复和风、彩云、逐时逐日与短时降水的多项数据问题
- 重做透明玻璃小组件，统一边框、字号、对齐、刷新状态和深浅色天气图标
- 新增天气娘启动图标并保留经典图标切换，界面延伸到手势导航区域

</details>

<details open>
<summary><b>0.1.1 // BUG FIXES</b></summary>

- 修复和风天气切换、和风/彩云预报显示、遥测内容与卡片错位
- 校准海外城市时区、南纬西经坐标和小组件时间
- 修复单城市底部手势拦截，并提高极小小组件字号
- 137 项单元测试通过，Android Lint 0 Error，个人版与公开版均可构建

</details>

<details>
<summary><b>0.1.0 // CITY DECK</b></summary>

- 底部长按城市卡组：保持按住左右滑选，或向上推后固定卡组再点选
- 主页模块可排序，并支持恢复默认顺序
- 全天气终端氛围、开发者效果预览和低亮度强调色
- 和风 / 彩云分步配置与连接测试；天气、降水和风向显示统一
- 五种终端设备式小组件，带呼吸状态灯并适配浅色模式
- 首次更新说明和社区贡献者名单

</details>

<details>
<summary><b>0.0.8 // DATA</b></summary>

- 修复降雨时主界面仍提示「不会下雨」的问题
- 修复逐时预报将下一小时误标为「现在」的问题
- 优化短时降水状态，雨停后不再显示「正在下雨」
- 优化逐日天气展示，白天与夜间天气不同时按更显著的天气显示
- 修复阴天被显示为小雨的问题

</details>

<details>
<summary><b>0.0.6 // NOWCAST</b></summary>

- 主屏大温度下增加一句话摘要：何时下雨、预警、明天温差
- 分钟降水卡写明「X 分钟后开始下雨」；公共版小米源接上 120 分钟序列
- 逐日行可点开，看出日落与月相
- 关闭系统强制深色；浅色主题冷启动不再闪黑
- 换源后降水图与逐时行高对齐；锁定源失败不再串用上一源缓存

</details>

<details>
<summary><b>0.0.5 // COLD JADE</b></summary>

- 新增「清冷翡翠」浅色主题，深色 / 浅色 / 跟随系统三档
- 修复跟随系统方向反了、和风预警不分级两个老问题
- 小组件双主题、设置页开源仓库入口；46 项单元测试通过

</details>

<details>
<summary><b>0.0.4 // WIDGET OVERHAUL</b></summary>

- 重做三档桌面小组件的圆角、字号、图标和信息层级
- 合入 0.0.3test 稳定性补丁；41 项单元测试通过，Lint 0 Error
- 离线缓存兜底、小组件后台刷新、数据源熔断、预警分级着色

</details>

<details>
<summary><b>0.0.3 // STABILITY PASS</b></summary>

- 修正月相、月出月落、小组件、快捷操作、定位换城和数据源状态
- 调整“明显”天气氛围档，保持“克制”档不变
- 统一应用内终端名称，重做启动图标
- 15 项单元测试通过，Lint 0 Error，公共版与满血版均可构建

</details>

<details>
<summary><b>0.0.2 // FEED SELECT</b></summary>

- 增加数据源选择、三种桌面小组件、天气氛围层和可选定位
- Open-Meteo 成为可独立使用的数据源
- 修复夜间图标、逐时曲线、返回与转屏等问题

</details>

**0.0.1 Preview**：首次公开预览，包含磷光终端界面、15 枚天气图标、三源数据链路和公共版构建方式。

完整版本记录见 [Releases](https://github.com/zhishengplus/ZhishengWeather/releases)。

## 许可

- 代码使用 [MIT License](LICENSE)。欢迎提交 [Issue](https://github.com/zhishengplus/ZhishengWeather/issues) 和 PR。
- 界面、启动图标、天气图标和终端文案为枳生天气项目素材，引用时请保留来源。
- 天气数据版权归 [和风天气](https://www.qweather.com/)、[Open-Meteo](https://open-meteo.com/) 和小米公开天气接口相关提供方，数据仅供参考。
- 使用和风主源时请保管好个人凭据，不要提交到公开仓库。

---

<p align="center"><sub>ZHISHENG WEATHER TERMINAL // PATTERN BLUE · Kotlin / Android</sub></p>
