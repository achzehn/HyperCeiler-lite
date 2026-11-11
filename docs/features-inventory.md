# HyperCeiler Lite 功能清单

> 生成基准：`library/core/src/main/res/xml/*.xml` 中的偏好项（`SwitchPreference` / `Preference` / `DropDownPreference` / `SeekBarPreference` 等），标题取中文资源，缺失时回退英文。
> 规模：**55 个功能页 / 509 个偏好项**（含子页面入口与内联分组）。
> 用途：作为精简决策的基线。`[待补]` 表示该文案的资源键在任何语言中都不存在（上游遗留问题）。

---

## 1. 总览

| 应用 / 模块 | 页面数 | 功能项 | 占比 |
|---|---|---|---|
| 系统框架 SystemFramework | 6 | 85 | 16.7% |
| 系统界面 SystemUI | 24 | 253 | 49.7% |
| 系统桌面 MiHome | 0 | **0** | 0% |
| 安全服务 SecurityCenter | 8 | 74 | 14.5% |
| 设置 Settings | 1 | 22 | 4.3% |
| 其他应用（16 页） | 16 | 75 | 14.7% |
| **合计** | **55** | **509** | 100% |

> 页面归属按文件名前缀归并：`framework*` → 系统框架，`system_ui*` → 系统界面，`security*` → 安全服务，`system_settings.xml` → 设置，其余 → 其他应用。

> 注：系统桌面在 `libhook` 中存在大量规则类（`rules/home/**`，139 个文件），但当前 **core 模块没有对应的偏好页 xml**，说明桌面功能已全部从 UI 下线，仅保留 hook 实现。这是精简时最大的一块"尸块"。

### 各模块明细占比

| 模块 | 明细 |
|---|---|
| 系统界面（253） | 状态栏图标 37、通知与控制中心 22、锁屏 19、媒体卡片 18、磁贴 18、超级岛媒体卡片 16、移动网络 13、时钟指示器 13、电池信息 12、网速 10、焦点歌词 9、状态栏主页 8、焦点通知 5、导航栏 6、其他 24、目录 5，以及电池自定义/移动网络类型/双排图标等子页 |
| 系统框架（85） | 其他 44、包管理服务 15、小窗 10、显示 6、音量 4、Pad 5、框架目录 6（含 5 个入口） |
| 安全服务（74） | 其他 27、全局侧边栏 23、应用管理 8、省电与电池 8、隐私 1、安全服务目录 6，及侧边栏功能子页 |
| 其他应用（75） | 应用包管理组件 10、应用商店 9、系统更新 9、互联互通 5、弹幕 3、浏览器 3、系统安全组件 2、换机 1、云服务 1、互传 1、系统服务组件 1、信任服务 1，及其他目录页（13）、弹窗子页（3+3）、隐藏特性（6）等 |

---

## 2. 系统框架（85 项）

### 2.1 其他（`framework_other.xml`，44 项）

| # | 功能 | key |
|---|---|---|
| 1 | 屏幕旋转 | `prefs_key_system_framework_screen_all_rotations` |
| 2 | 秒截图（禁用截图延迟） | `prefs_key_system_framework_quick_screenshot` |
| 3 | 禁用设备名称敏感词校验 | `prefs_key_system_settings_anti_ques` |
| 4 | 禁用每 72 小时验证锁屏密码 | `prefs_key_system_framework_disable_72h_verify` |
| 5 | 禁止自动降低屏幕亮度（非温控） | `prefs_key_system_framework_other_thermal_brightness` |
| 6 | 禁用应用链接验证 | `prefs_key_system_framework_disable_app_link_verify` |
| 7 | 关闭访问设备日志确认 | `prefs_key_various_disable_access_device_logs` |
| 8 | 隐藏流量加速 Toast | `prefs_key_system_framework_disable_link_turbo_toast` |
| 9 | 允许截屏（任意应用） | `prefs_key_system_other_flag_secure` |
| 10 | 允许不受信任的触摸 | `prefs_key_system_framework_allow_untrusted_touch` |
| 11 | 引导式访问 [待补] | `prefs_key_system_framework_guided_access` |
| 12 | 引导式访问·磁贴 [待补] | `prefs_key_system_framework_guided_access_tile` |
| 13 | 引导式访问·阻止弹窗 [待补] | `prefs_key_system_framework_guided_access_block_dialog` |
| 14 | 引导式访问·状态栏提示 [待补] | `prefs_key_system_framework_guided_access_status` |
| 15 | 引导式访问·锁屏退出 [待补] | `prefs_key_system_framework_guided_access_exit_lockscreen` |
| 16 | 移除"上层显示"通知 | `prefs_key_system_other_delete_on_post_notification` |
| 17 | 禁用全局水印 | `prefs_key_system_framework_disable_miui_watermark` |
| 18 | 快速安装（安装到内存） | `prefs_key_system_framework_other_speed_install` |
| 19 | 使用原生安装器 | `prefs_key_system_framework_use_android_package_installer` |
| 20 | 使用原生照片/文件选择器 | `prefs_key_system_framework_native_file_picker` |
| 21 | 禁止自动终止后台应用 | `prefs_key_system_framework_other_disable_cleaner` |
| 22 | 禁止监控手势 | `prefs_key_system_framework_other_disable_gesture_monitor` |
| 23 | 禁用温控 | `prefs_key_system_framework_other_disable_thermal` |
| 24 | 解锁导航断充保护限制 | `prefs_key_system_framework_hook_entry` |
| 25 | 禁用"是否允许禁用 APP"校验 | `prefs_key_system_framework_disable_verify_can_ve_disabled` |
| 26 | 禁用精简版系统检查 | `prefs_key_system_framework_disablt_miuilite_check` |
| 27 | 禁止剪贴板粘贴 Toast | `prefs_key_system_framework_posted_clipboard` |
| 28 | 允许后台读取剪切板 | `prefs_key_system_framework_clipboard_whitelist` |
| 29 | 后台读剪切板白名单（应用） | `prefs_key_system_framework_clipboard_whitelist_apps` |
| 30 | 后台读剪切板白名单（文本） | `prefs_key_system_framework_clipboard_whitelist_list` |
| 31 | 允许冻结受保护的应用 | `prefs_key_system_framework_allow_disable_protected_package` |
| 32 | 默认允许未知来源安装 | `prefs_key_system_framework_bypass_unknown_sources_restrictions` |
| 33 | 跳应用商店时强制选择器 | `prefs_key_system_framework_bypass_force_mi_appstore` |
| 34 | 跳小米商店时用小窗 | `prefs_key_system_framework_market_use_detailmini` |
| 35 | 下载页不强制小米下载器 | `prefs_key_system_framework_bypass_force_downloadui` |
| 36 | 绕过链式启动检测 | `prefs_key_system_framework_bypass_wake_path_checker` |
| 37 | 清理分享菜单 | `prefs_key_system_framework_clean_share_menu` |
| 38 | 分享菜单排除应用 | `prefs_key_system_framework_clean_share_apps` |
| 39 | 清理打开方式菜单 | `prefs_key_system_framework_clean_open_menu` |
| 40 | 打开方式排除应用 | `prefs_key_system_framework_clean_open_apps` |
| 41 | 清理文本上下文菜单 | `prefs_key_system_framework_clean_process_text_menu` |
| 42 | 文本菜单排除应用 | `prefs_key_system_framework_clean_process_text_apps` |
| 43 | 限制链豁免 | `prefs_key_system_framework_auto_start_menu` |
| 44 | 限制链豁免应用列表 | `prefs_key_system_framework_auto_start_apps` |

### 2.2 包管理服务（`framework_core_patch.xml`，15 项）

| # | 功能 | key |
|---|---|---|
| 1 | 总开关 | `prefs_key_system_framework_core_patch_enable` |
| 2 | 允许降级安装 | `prefs_key_system_framework_core_patch_downgr` |
| 3 | 禁用包管理器签名验证 | `prefs_key_system_framework_core_patch_auth_creak` |
| 4 | 禁用系统完整性校验 | `prefs_key_system_framework_core_patch_disable_integrity` |
| 5 | 绕过共享用户签名验证 | `prefs_key_system_framework_core_patch_shared_user` |
| 6 | 禁用 APK 签名验证 | `prefs_key_system_framework_core_patch_digest_creak` |
| 7 | 禁用分包签名匹配 | `prefs_key_system_framework_core_patch_exact_signature_check` |
| 8 | 使用已安装应用签名 | `prefs_key_system_framework_core_patch_use_pre_signature` |
| 9 | 禁用低 API 校验 | `prefs_key_system_framework_disable_low_api_check` |
| 10 | 禁用持久性检查 | `prefs_key_system_framework_disable_persistent` |
| 11 | 禁用安装包验证代理 | `prefs_key_system_framework_disable_verification_agent` |
| 12 | 禁用隔离应用检查 | `prefs_key_system_framework_core_patch_bypass_isolation_violation` |
| 13 | 允许安装系统应用 | `prefs_key_system_framework_core_patch_allow_update_system_app` |
| 14 | 禁用重置指纹 | `prefs_key_system_framework_core_patch_unloss_fingerprint` |
| 15 | 风险提示条（非功能） | — |

### 2.3 小窗（`framework_freeform.xml`，10 项）

多小窗、强制允许小窗、记住状态及位置、小窗气泡、贴边小窗强制前台、跳转应用使用小窗、通知、通知悬浮窗、传送门、分享至三方应用。

### 2.4 显示（`framework_display.xml`，6 项）

强制高级材质、允许所有 APP 深色模式、允许第三方主题、使用原生截屏、增强最近任务可见性、隐藏屏幕缺口。

### 2.5 音量（`framework_volume.xml`，4 项）

默认调整音量、移除高音量安全限制、音量即时调整、媒体音量阶数。

### 2.6 Pad 相关（`various_mipad.xml`，5 项）

恢复 ESC、忽略触控笔按键手势、交换手势手指数、移除触控笔蓝牙限制、驱动版本。

### 2.7 框架目录（`framework.xml`，6 项）

> 仅子页面入口：小窗、音量、显示、其他、Pad 相关、包管理服务。

---

## 3. 系统界面（253 项）

### 3.1 状态栏（8 项入口 + 子页）

| # | 功能 | key |
|---|---|---|
| 1 | 图标（子页 37 项） | `IconManageNewSettings` |
| 2 | 移动网络（子页 13 项） | `prefs_key_statubar_mobile_network` |
| 3 | 网速指示器（子页 10 项） | `NetworkSpeedIndicatorSettings` |
| 4 | 时钟指示器（子页 13 项） | `NewClockIndicatorSettings` |
| 5 | 焦点通知（子页 5 项） | `FocusNotificationSettings` |
| 6 | 电池信息（子页 12 项） | `BatteryDetailIndicatorSettings` |
| 7 | 双击状态栏锁屏 | `prefs_key_system_ui_status_bar_double_tap_to_sleep` |
| 8 | 截屏时隐藏状态栏 | `prefs_key_system_ui_status_bar_hide_icon` |

### 3.2 状态栏图标（`system_ui_status_bar_icon_manage_new.xml`，37 项）

- 开关 2：隐藏 WIFI 网络活动指示器、交换 WIFI 与移动网络
- 无线类图标样式 5：WIFI 图标、WIFI 标准、WIFI 热点、飞行模式、流量节省
- 连接/状态图标 12：蓝牙、蓝牙电量、位置、麦克风、闹钟、NFC、VPN、静音、勿扰、汽车、PAD、电脑
- 设备图标 6：手机、小爱音箱、小爱触屏音箱、小爱音箱组合、组合立体声、电视
- 音频图标 2：有线耳机、无线耳机
- 电池 4：隐藏电量百分比、百分比符号、充电指示器、电池图标
- 通话 4：麦克风静音、免提、录音、隐身模式
- 通知 1：通知图标最大数量
- 子页 1：电池自定义（3 项：交换位置、启用修改、加粗）

### 3.3 移动网络（`system_ui_status_bar_mobile.xml`，13 项）

信号显示逻辑、无 SIM 卡图标、隐藏 SIM1/SIM2 信号、HD 图标 ×3、隐藏漫游、隐藏 VoWiFi、隐藏 VoLTE、大网络类型图标、移动网络类型图标（子页 6 项）、双排移动网络图标（子页 2 项）。

### 3.4 时钟指示器（`system_ui_status_bar_new_clock_indicator.xml`，13 项）

启用修改、不同步状态栏时钟、隐藏日期时钟、状态栏日期时钟格式+加粗、状态栏时钟格式+加粗+排列、通知中心大时钟格式+加粗、通知中心迷你时钟格式+加粗、格式示例。

### 3.5 网速指示器（10 项）

启用修改、字体大小、字体样式、隐藏单位、隐藏慢速、上下行均慢速时隐藏、指示器样式、交换上下行位置、指示器图标、水平对齐。

### 3.6 焦点通知（`system_ui_status_bar_focus_notif.xml`，5 项）

媒体卡片（子页 16 项）、焦点歌词（子页 9 项）、隐藏灵动舞台、短文本测试、长文本测试。

### 3.7 媒体卡片·超级岛（16 项）

背景风格、颜色动画、自动反色、氛围光效、优化取色、布局修改开关、按钮左对齐、隐藏设备切换、封面模式、按钮顺序、字体修改、进度条启用、进度条样式、进度条按钮样式、彗星进度条、圆角进度条。

### 3.8 焦点歌词（`system_ui_status_bar_music.xml`，9 项）

注意事项条、下载 SuperLyric、启用、通知注册在 App、显示通知歌词、不在息屏显示、自定义焦点息屏布局、隐藏状态栏时钟、点按显示时钟。

### 3.9 电池信息（`system_ui_status_bar_battery_detail.xml`，12 项，全部英文未汉化）

显示电池信息、显示内容、隐藏单位、右侧显示、温度保留小数、电流恒正、修正电流比例、单行显示、逆序、仅充电时显示、加粗、对齐方式。

### 3.10 通知与控制中心（`system_ui_control_center.xml`，22 项）

头部渐变模糊、显示天气、显示地区、媒体通知卡片（子页 18 项）、展开通知、自动隐藏展开通知、禁止折叠为历史通知、亮屏静音、勿扰修复、重定向通知设置、移除通知数量限制、移除焦点通知白名单、焦点通知列表、优化焦点通知背景混色、增强焦点通知混色、禁用透明模式、磁贴（子页 18 项）、隐藏编辑按钮、隐藏网络监控提示、强制背景模糊、运营商名称自定义、自定义行列数。

### 3.11 磁贴（`system_ui_control_center_tiles.xml`，18 项）

磁贴自动收起、雪豹模式（禁音频）、GMS 开关、传送门开关、极暗磁贴图标替换、补全磁贴列表、5G 网络开关、阳光模式 ×2 [待补]、手电筒亮度控制、卡片磁贴自定义（子页 2 项）、圆角矩形磁贴、已启用磁贴颜色自定义+背景+图标、已启用卡片磁贴颜色自定义+背景+图标。

### 3.12 媒体通知卡片（18 项）

背景风格、颜色动画、自动反色、氛围光效、优化取色、布局修改、按钮左对齐、隐藏设备切换、封面模式、按钮顺序、字体修改、进度条启用、进度条样式、进度条按钮样式、彗星进度条、圆角进度条等。

### 3.13 锁屏（`system_ui_lock_screen.xml`，19 项）

底部左侧按钮、隐藏相机、隐藏解锁提示、锁屏隐藏状态栏、按钮背景模糊、高级材质、背景颜色、解除通知限制、解锁后保留通知、隐藏勿扰通知、底部充电信息、详细电流、电池温度、刷新间隔、乱序 PIN、三方锁屏唤醒识别、蓝牙解锁 Toast、"双击锁屏"、联动息屏壁纸动画参数。

### 3.14 导航栏（`system_ui_navigation.xml`，6 项）

旋转建议（说明）、自定义导航栏、隐藏导航栏、自定义手势提示线、提示线深色/浅色颜色。

### 3.15 系统界面·其他（`system_ui_other.xml`，24 项）

自定义主题色+选色、允许管理所有通知、通知栏下拉打开小窗、数据百分比样式、亮度值显示、音量值显示、音量/亮度百分比、高级材质、按压展开二级音量条、隐藏静音勿扰按钮、音量条模糊、隐藏/禁用快捷窗口按钮、禁用无极缩放（Pad）、隐藏小窗提示线、Toast 高级材质、原生音量条、原生关机菜单、解锁原生剪贴板编辑器、解除高级材质模糊限制、移动 statusbar_gestures.dat、快捷开关标签。

### 3.16 系统界面目录（`system_ui.xml`，5 项）

锁屏、状态栏、导航栏、通知与控制中心、其他。

---

## 4. 安全服务（74 项）

### 4.1 其他（`security_center_other.xml`，27 项）

智能补光、视频美颜、隐私拍摄、人像居中、解锁贴边小窗、移除气泡通知限制、禁用环境检查、禁用支付自动检查、精简管家首页、应用锁乱序 PIN、禁止妙享省电、禁止自启模糊预览图、禁用体验优化、跳过倒计时限制、直接跳过警告、禁用 Root 检测、禁用 USB 安装拦截、绕过 USB 安装网络校验、禁止自动关闭无障碍、隐藏低电量警告、移除链式启动弹窗、禁止恶意应用通知、解锁晕车缓解、游戏加速启动、SIM 卡跳过小米账号验证、移除 SIM 验证成功弹窗、禁用网络助手离线配置。

### 4.2 全局侧边栏（`security_center_sidebar.xml`，23 项）

隐藏侧边栏、保留侧滑展开、提示线自定义+三色、背景自定义、高级材质、图标反色、背景叠加色、禁止推荐应用 ×2、禁用息屏听剧/熄屏挂机、新版视频工具箱、杜比弹窗、动态画面补偿、影像轮廓增强、极清播放、移除画质温度限制、游戏狂暴模式、移除自动连招黑名单、准星辅助、移除游戏 Toast。

### 4.3 应用管理（8 项）

列表排序、移除举报键、全限制、应用打开链接管理、AOSP 应用信息 [待补]、AOSP 应用管理 [待补]、应用状态控制、显示更多应用信息。

### 4.4 省电与电池（8 项）

电池温度、电池健康度、更多电池信息、详细耗电数据、恢复耗电排行、极速无线充电、低温超级续航、补全充电与电池。

### 4.5 隐私保护（1 项）

隐藏权限使用提示。

### 4.6 安全服务目录（6 项）

安全提示条、应用管理、省电与电池（内联分组）、隐私保护、全局侧边栏、其他。

---

## 5. 设置（22 项）

解锁控制中心样式、新版触感调节页、解锁应用布局优化、国际版设置页、禁止隐藏 Google 入口、解锁澎湃 AI 入口、查看已保存 WIFI 密码、解除网络加速应用限制、新版 NFC 页、显示更多 VPN 类型、允许永不锁屏、超分辨率增强、AI 场景优化与视频画质、动态画面补偿、显示更多通知设置、上层显示权限直达、无障碍权限直达、USB 连接不弹窗+默认选项、强制显示暂停执行已缓存应用、峰值刷新率入口、极致模式入口。

---

## 6. 其他应用（75 项）

### 6.1 应用包管理组件（10 项）

净化页面、禁用推广、禁用风险检测、阻断云端配置、禁用安全守护提示、禁用频繁安装检查、显示更多安装包信息、解除系统应用安装限制、禁止上传应用信息、自定义安装来源。

### 6.2 应用商店（9 项）

禁用推广、机型伪装+型号/代号/厂商、禁止第三方图标、禁止监控安装删除、强制安全检测优秀、禁用推送弹窗。

### 6.3 系统更新（9 项）

移除 OTA 验证、移除自动更新弹窗、启用伪装版本、伪装类型、大版本、XMS 版本、版本号、Android 版本、机型。

### 6.4 互联互通服务（5 项）

解锁小米妙享、解锁全部应用流转、关闭电脑管家剪贴板地区校验、强制支持摄像头流转、阻止生成乐播投屏文件。

### 6.5 弹幕通知（3 项）

允许所有应用、允许全局、弹幕不可点击。

### 6.6 浏览器（3 项）

解锁开发者选项、启用预发环境、禁用网址黑名单。

### 6.7 系统安全组件（2 项）

禁用环境检查、阻止上传应用列表。

### 6.8 单项模块（5 项）

| 模块 | 功能 |
|---|---|
| 小米换机 | 允许迁移所有应用 |
| 小米云服务 | 应用数据云同步补全 |
| 互传 | 禁止自动关闭 [待补文案] |
| 系统服务组件 | 禁用游戏中心广告 |
| 信任服务 | 禁用风险检测 |

### 6.9 其他应用目录页（`various.xml`，13 项）

弹窗（子页 3 项）、隐藏特性（子页 6 项）、解除剪贴板数量限制、添加清空剪贴板按钮、输入法全面屏优化、目标输入法、左右功能键、显示完整输入法列表、解锁输入法列表 UI 数量、禁用越界回弹、移除文件夹不可选择限制、系统标题样式。

---

## 7. 精简参考：重复/碎片化候选

以下条目功能语义高度重叠，是精简时最先可合并的对象：

| 类别 | 重复项 | 建议 |
|---|---|---|
| 时钟指示器 | 4 个内联子页各自只有一个"加粗"开关 | 合并为 1 个"加粗"总开关 + 各时钟格式 |
| 图标样式 | 状态栏图标页 20+ 个"其他"下拉项 | 合并为 1 个"图标管理"入口 |
| 媒体卡片 | 通知中心媒体卡片与超级岛媒体卡片各 16~18 项，结构几乎一一对应 | 抽公共配置，UI 合并 |
| 颜色自定义 | 侧边栏三色、磁贴三色、导航栏两色、弹窗背景、自定义背景 | 统一"颜色自定义"控件组 |
| 焦点通知 | 焦点通知 / 焦点歌词 / 媒体卡片三层嵌套 | 收敛为 1 页 |
| 手机管家 | 其他 27 项中约 10 项为"解锁 xx 限制" | 合并为"解锁限制"分组 |
| 资源缺失 | 引导式访问 5 项、AOSP 应用信息 2 项、阳光模式 2 项、互传 1 项共 10 项无任何语言文案 | 优先核查是否废弃 |
| 系统桌面 | core 无偏好页，libhook 保留约 130 个规则文件 | 确认后整模块移除 |

---

## 8. 数据口径说明

- 统计单位为偏好项，包含：开关、下拉、拖动条、颜色选择、应用选择器、子页面入口、内联分组、提示条。
- 子页面入口计入所属父页，其内部选项单独列出；因此"总览"按模块汇总时已避免重复计数。
- 部分 key 存在复用（如"显示电池温度"在锁屏与省电页各有一处），按页面分别计数。
- 文案为英文的页面：电池信息（12 项）、焦点通知列表、画质温度限制等，属未汉化，非功能缺失。
