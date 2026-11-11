# HyperCeiler Lite 功能清单（逐行版）

> 基准：`library/core/src/main/res/xml/*.xml` 全部偏好项，一行一项。
> 规模：**55 个功能页 / 509 个偏好项**。
> 标记：`[开关]` 开关、`[其他]` 下拉/拖动条/颜色/应用选择、`[子页面]` 入口、`[分组]` 内联分组。
> 缩进行 = 内联分组内的子项；`[待补]` = 资源键在任意语言中都不存在。

---

## 总览

| 模块 | 页面 | 项数 |
|---|---|---|
| 系统框架 | 6 | 85 |
| 系统界面 | 24 | 253 |
| 安全服务 | 8 | 74 |
| 设置 | 1 | 22 |
| 其他应用 | 16 | 75 |
| 系统桌面 | 0 | 0 |
| **合计** | **55** | **509** |

---
## 系统框架（6 页 / 85 项）


### framework_other.xml — 其他（44 项）
- [开关] 禁用设备名称敏感词校验 `prefs_key_system_settings_anti_ques`
- [开关] 禁用每 72 小时验证锁屏密码 `prefs_key_system_framework_disable_72h_verify`
- [开关] 移除上层显示通知 `prefs_key_system_other_delete_on_post_notification`
- [开关] 禁用是否允许禁用 APP 校验 `prefs_key_system_framework_disable_verify_can_ve_disabled`
- [开关] 禁用精简版系统检查 `prefs_key_system_framework_disablt_miuilite_check`
- [开关] 允许冻结受保护的应用 `prefs_key_system_framework_allow_disable_protected_package`
- [开关] 跳转到小米应用商店时始终使用小窗口打开 `prefs_key_system_framework_market_use_detailmini`

### framework_core_patch.xml — 包管理服务（15 项）

- [提示/自定义布局] 此列表下的功能需要在您了解此类功能后再选择性启用，部分选项存在一定危险性，请知悉\n常规选项的功能除总开关外均即时生效，扩展功能无论开关都需要重启才能生效
- [开关] 启用 `prefs_key_system_framework_core_patch_enable`
- [开关] 允许降级安装应用 `prefs_key_system_framework_core_patch_downgr`
- [开关] 禁用软件包管理器签名验证 `prefs_key_system_framework_core_patch_auth_creak`
- [开关] 禁用系统完整性校验 `prefs_key_system_framework_core_patch_disable_integrity`
- [开关] 绕过共享用户签名验证 `prefs_key_system_framework_core_patch_shared_user`
- [开关] 禁用 APK 签名验证 `prefs_key_system_framework_core_patch_digest_creak`
- [开关] 禁用分包签名匹配 `prefs_key_system_framework_core_patch_exact_signature_check`
- [开关] 安装时始终使用已安装应用的签名 `prefs_key_system_framework_core_patch_use_pre_signature`
- [开关] 禁用低 API 校验 `prefs_key_system_framework_disable_low_api_check`
- [开关] 禁用持久性检查 `prefs_key_system_framework_disable_persistent`
- [开关] 禁用安装包验证代理 `prefs_key_system_framework_disable_verification_agent`
- [开关] 禁用隔离应用检查 `prefs_key_system_framework_core_patch_bypass_isolation_violation`
- [开关] 允许安装系统应用 `prefs_key_system_framework_core_patch_allow_update_system_app`
- [开关] 禁用重置指纹 `prefs_key_system_framework_core_patch_unloss_fingerprint`

### framework_freeform.xml — [待补]（10 项）

- [开关] 多小窗 `prefs_key_system_framework_freeform_count`
- [开关] 强制允许小窗 `prefs_key_system_framework_disable_freeform_blacklist`
- [开关] 记住状态及位置 `prefs_key_system_framework_freeform_sticky`
- [开关] 小窗气泡 `prefs_key_system_framework_freeform_bubble`
- [开关] 贴边小窗强制前台 `prefs_key_system_framework_freeform_foreground_pin`
- [开关] 跳转应用使用小窗打开 `prefs_key_system_framework_freeform_jump`
- [开关] 通知 `prefs_key_system_framework_freeform_open_notification`
- [开关] 通知悬浮窗 `prefs_key_system_framework_freeform_open_notification_fw`
- [开关] 传送门 `prefs_key_system_framework_freeform_content_extension`
- [开关] 分享至三方应用 `prefs_key_system_framework_freeform_app_share`

### framework.xml — -（6 项）

- [子页面] [待补] `FreeFormSettings`
- [子页面] 音量 `VolumeSettings`
- [子页面] 显示 `DisplaySettings`
- [子页面] 其他 `OtherSettings`
- [子页面] Pad 相关 `prefs_key_system_framework_mipad` `MiPadSettings`
- [子页面] 包管理服务 `CorePatchSettings`

### framework_display.xml — 显示（6 项）
- [开关] 允许所有 APP 使用深色模式 `prefs_key_system_framework_allow_all_dark_mode`

### framework_volume.xml — 音量（4 项）


## 系统界面（24 页 / 253 项）


### system_ui_status_bar_icon_manage_new.xml — 图标（37 项）

- [开关] 隐藏 WIFI 网络活动指示器 `prefs_key_system_ui_status_bar_icon_wifi_network_indicator_new`
- [开关] 交换 WIFI 与移动网络 `prefs_key_system_ui_status_bar_swap_wifi_and_mobile_network`
- [其他] WIFI 图标 `prefs_key_system_ui_status_bar_icon_wifi`
- [其他] WIFI 标准 `prefs_key_system_ui_status_bar_icon_wifi_standard`
- [其他] WIFI 热点 `prefs_key_system_ui_status_bar_icon_hotspot`
- [其他] 飞行模式 `prefs_key_system_ui_status_bar_icon_airplane`
- [其他] 蓝牙 `prefs_key_system_ui_status_bar_icon_bluetooth`
- [其他] 蓝牙电量 `prefs_key_system_ui_status_bar_icon_bluetooth_battery`
- [其他] 位置信息 `prefs_key_system_ui_status_bar_icon_location`
- [其他] 麦克风 `prefs_key_system_ui_status_bar_icon_mic`
- [其他] 闹钟 `prefs_key_system_ui_status_bar_icon_alarm_clock`
- [其他] NFC `prefs_key_system_ui_status_bar_icon_nfc`
- [其他] VPN `prefs_key_system_ui_status_bar_icon_vpn`
- [其他] 流量节省 `prefs_key_system_ui_status_bar_icon_data_saver`
- [其他] 静音 `prefs_key_system_ui_status_bar_icon_volume`
- [其他] 勿扰 `prefs_key_system_ui_status_bar_icon_zen`
- [开关] 隐藏隐身模式图标 `prefs_key_system_ui_status_bar_hide_icon_stealth`
- [开关] 通知图标最大数量 `prefs_key_system_ui_status_bar_notification_icon_maximum_enable`
- [开关] 隐藏电量百分比 `prefs_key_system_ui_status_bar_battery_percent`
- [开关] 隐藏电量百分比符号 `prefs_key_system_ui_status_bar_battery_percent_mark`
- [开关] 隐藏充电指示器 `prefs_key_system_ui_status_bar_battery_charging`
- [开关] 隐藏电池图标 `prefs_key_system_ui_status_bar_battery_icon`
- [子页面] 电池自定义 `BatteryStyleSettings`
- [开关] 隐藏麦克风静音图标 `prefs_key_system_ui_status_bar_hide_icon_mute`
- [开关] 隐藏免提图标 `prefs_key_system_ui_status_bar_hide_icon_speakerphone`
- [开关] 隐藏录音图标 `prefs_key_system_ui_status_bar_hide_icon_call_record`
- [其他] 汽车 `prefs_key_system_ui_status_bar_icon_car`
- [其他] PAD `prefs_key_system_ui_status_bar_icon_pad`
- [其他] 电脑 `prefs_key_system_ui_status_bar_icon_pc`
- [其他] 手机 `prefs_key_system_ui_status_bar_icon_phone`
- [其他] 小爱音箱 `prefs_key_system_ui_status_bar_icon_soundbox`
- [其他] 小爱触屏音箱 `prefs_key_system_ui_status_bar_icon_soundbox_screen`
- [其他] 小爱音箱组合 `prefs_key_system_ui_status_bar_icon_soundbox_group`
- [其他] 小爱音箱组合立体声 `prefs_key_system_ui_status_bar_icon_soundbox_group_stereo`
- [其他] 电视 `prefs_key_system_ui_status_bar_icon_tv`
- [其他] 有线耳机 `prefs_key_system_ui_status_bar_icon_headset`
- [其他] 无线耳机 `prefs_key_system_ui_status_bar_icon_wireless_headset`

### system_ui_other.xml — 其他（24 项）

- [开关] 允许管理所有通知 `prefs_key_system_framework_allow_manage_all_notifications`
- [开关] 更多应用通知栏下拉打开小窗 `prefs_key_system_ui_notification_freeform`
- [其他] 数据百分比显示样式 `prefs_key_system_ui_others_pct_style`
- [开关] 亮度值显示 `prefs_key_system_ui_control_center_qs_brightness_top_value_show`
- [开关] 音量值显示 `prefs_key_system_ui_control_center_qs_volume_top_value_show`
- [开关] 显示音量百分比 `prefs_key_system_cc_volume_showpct_title`
- [开关] 显示亮度百分比 `prefs_key_system_showpct_title`
- [开关] 使用高级材质效果 `prefs_key_system_showpct_use_blur`
- [开关] 按压展开二级音量条 `prefs_key_system_ui_volume_collpased_column_press`
- [开关] 隐藏静音和勿扰按钮 `prefs_key_system_ui_volume_hide_foot_button`
- [开关] 音量条模糊 `prefs_key_system_ui_plugin_enable_volume_blur`
- [开关] 隐藏快捷窗口按钮 `prefs_key_system_ui_disable_miui_multi_win_switch`
- [开关] 禁用快捷窗口按钮 `prefs_key_system_ui_remove_miui_multi_win_switch`
- [开关] 禁用无极缩放手势 `prefs_key_system_ui_disable_infinitymode_gesture`
- [开关] 隐藏小窗的手势提示线 `prefs_key_system_ui_disable_bottombar`
- [开关] Toast 高级材质 `prefs_key_system_framework_background_blur_toast`
- [开关] 原生音量条 `prefs_key_system_ui_disable_volume`
- [开关] 原生关机菜单 `prefs_key_system_ui_disable_power`
- [开关] 解锁原生剪切板编辑器 `prefs_key_system_ui_unlock_clipboard`
- [开关] 解除高级材质模糊限制 `prefs_key_system_ui_other_default_plugin_theme`
- [开关] 将 statusbar_gestures.dat 移动到 MIUI 目录 `prefs_key_system_ui_move_log_to_miui`
- [开关] 快捷开关标签 `prefs_key_system_ui_qs_label`

### system_ui_control_center.xml — 通知与控制中心（22 项）

- [开关] 通知与控制中心头部渐变模糊 `prefs_key_system_ui_shade_header_gradient_blur`
- [开关] 显示天气 `prefs_key_system_ui_control_center_show_weather`
- [开关] 显示地区 `prefs_key_system_ui_control_center_show_weather_city`
- [子页面] 媒体通知卡片 `prefs_key_system_ui_control_center_media_control_media_custom` `MediaCardSettings`
- [其他] 展开通知 `prefs_key_system_ui_control_center_expand_notification`
- [开关] 自动隐藏展开的通知 `prefs_key_system_ui_control_center_auto_clean_expand_notification`
- [开关] 禁止折叠为历史通知 `prefs_key_system_ui_control_center_unimportant_notification`
- [开关] 亮屏时静音 `prefs_key_system_ui_control_center_mute_visible_notice`
- [开关] 勿扰模式修复 `prefs_key_system_ui_control_center_zen_fix`
- [开关] 重定向通知设置 `prefs_key_system_ui_control_center_redirect_notice`
- [开关] 移除通知数量限制 `prefs_key_system_ui_control_center_remove_notif_num_limit`
- [开关] 移除焦点通知白名单 `prefs_key_system_ui_unlock_all_focus`
- [其他] Focus notification list `prefs_key_system_ui_focus_notification_list`
- [开关] 优化焦点通知背景混色 `prefs_key_system_ui_control_center_opt_notification_element_background_color`
- [开关] 增强优化焦点通知背景混色 `prefs_key_system_ui_control_center_opt_notification_element_background_color_advanced`
- [开关] 禁用透明模式 `prefs_key_system_ui_control_center_notification_disable_transparent`
- [子页面] 磁贴 `prefs_key_system_ui_control_center_tile_settings` `TileSettings`
- [开关] 隐藏编辑按钮 `prefs_key_system_ui_control_center_hide_edit_botton`
- [开关] 隐藏网络监控提示 `prefs_key_system_ui_control_center_disable_device_managed`
- [开关] 强制背景模糊 `prefs_key_system_ui_control_center_statusbar_blur`
- [其他] 运营商名称自定义 `prefs_key_system_ui_control_center_hide_operator`
- [开关] 自定义行列数 `prefs_key_system_control_center_old_enable`

### system_ui_lock_screen.xml — 锁屏（19 项）

- [其他] 底部左侧按钮 `prefs_key_system_ui_lock_screen_bottom_left_button`
- [开关] 隐藏相机 `prefs_key_system_ui_lock_screen_hide_camera`
- [开关] 隐藏屏幕底部解锁提示 `prefs_key_system_ui_lock_screen_unlock_tip`
- [开关] 锁屏时隐藏状态栏 `prefs_key_system_ui_lock_screen_hide_status_bar`
- [开关] 锁屏按钮背景模糊 `prefs_key_system_ui_lock_screen_blur_button`
- [开关] 使用高级材质 `prefs_key_system_ui_lock_screen_hyper_blur_button`
- [其他] 背景颜色 `prefs_key_system_ui_lock_screen_blur_button_bg_color`
- [开关] 解除通知限制 `prefs_key_system_ui_lock_screen_unlock_notification_restrict`
- [开关] 解锁后保留通知 `prefs_key_system_ui_lock_screen_keep_notification`
- [开关] 隐藏勿扰模式通知 `prefs_key_system_ui_lock_screen_not_disturb_mode`
- [开关] 锁屏底部显示充电信息 `prefs_key_system_ui_lock_screen_show_charging_cv`
- [开关] 显示详细电流 `prefs_key_system_ui_show_charging_c_more`
- [开关] 显示电池温度 `prefs_key_system_ui_show_battery_temperature`
- [开关] 显示刷新间隔 `prefs_key_system_ui_lock_screen_show_spacing_value`
- [开关] 乱序 PIN `prefs_key_system_ui_lock_screen_scramble_pin`
- [开关] 允许三方锁屏界面唤醒系统验证识别 `prefs_key_system_ui_lock_screen_allow_third_face`
- [开关] 禁止显示 \"已通过蓝牙设备解锁\" Toast `prefs_key_system_ui_lock_screen_disable_unlock_by_ble_toast`
- [开关] 双击锁屏 `prefs_key_system_ui_lock_screen_double_lock`
- [开关] 覆盖联动息屏壁纸动画参数 `prefs_key_system_ui_lock_screen_linkage_anim`

### system_ui_control_center_media_cards.xml — 媒体通知卡片（18 项）

- [其他] 背景风格 `prefs_key_system_ui_control_center_media_control_background_mode`
- [开关] 颜色动画 `prefs_key_system_ui_control_center_media_control_control_color_anim`
- [开关] 自动反色 `prefs_key_system_ui_control_center_media_control_inverse_color`
- [开关] 氛围光效 `prefs_key_system_ui_control_center_media_control_ambient_light`
- [开关] 优化取色 `prefs_key_system_ui_control_center_media_control_ambient_light_opt`
- [开关] 始终深色模式 `prefs_key_system_ui_control_center_media_control_always_dark`
- [开关] 解除自定义动作应用限制 `prefs_key_system_ui_control_center_media_control_unlock_custom_actions`
- [开关] 启用布局修改 `prefs_key_system_ui_control_center_media_control_media_button_layout_switch`
- [开关] 动作按钮左对齐 `prefs_key_system_ui_control_center_media_control_media_button_actions_left_aligned`
- [开关] 隐藏设备切换按钮 `prefs_key_system_ui_control_center_media_control_media_button_hide_seamless`
- [其他] 媒体封面 `prefs_key_system_ui_control_center_media_control_media_album_mode`
- [其他] 媒体按钮顺序 `prefs_key_system_ui_control_center_media_control_media_button_mode`
- [开关] 启用字体修改 `prefs_key_system_ui_control_center_media_control_media_button_size_switch`
- [开关] 启用 `prefs_key_system_ui_control_center_media_control_progress_on`
- [其他] 媒体进度条样式 `prefs_key_system_ui_control_center_media_control_progress_mode`
- [其他] 媒体进度条按钮样式 `prefs_key_system_ui_control_center_media_control_progress_thumb_mode`
- [开关] 彗星进度条 `prefs_key_system_ui_control_center_media_control_progress_comet`
- [开关] 圆角进度条 `prefs_key_system_ui_control_center_media_control_progress_round`

### system_ui_control_center_tiles.xml — 磁贴（18 项）

- [开关] 磁贴自动收起 `prefs_key_system_ui_control_auto_close`
- [开关] 雪豹模式 `prefs_key_system_ui_control_center_snow_leopard_mode`
- [开关] GMS 开关 `prefs_key_security_center_gms_open`
- [开关] 传送门开关 `prefs_key_security_center_taplus`
- [开关] 极暗磁贴图标替换 `prefs_key_security_center_reduce_bright_colors_tile`
- [开关] 补全磁贴列表 `prefs_key_system_ui_control_center_fix_tiles_list`
- [其他] 5G 网络开关 `prefs_key_system_control_center_5g_new_tile`
- [其他] [待补] `prefs_key_system_control_center_sunshine_new_mode`
- [其他] [待补] `prefs_key_system_control_center_sunshine_new_mode_high`
- [其他] 手电筒亮度控制 `prefs_key_security_flash_light_switch`
- [子页面] 卡片磁贴自定义 `prefs_key_card_tile` `CardTileSettings`
- [开关] 圆角矩形磁贴 `prefs_key_system_ui_control_center_rounded_rect`
- [开关] 已启用磁贴颜色自定义 `prefs_key_system_ui_control_center_qs_open_color`
- [其他] 背景颜色 `prefs_key_system_ui_control_center_qs_bg_color`
- [其他] 图标颜色 `prefs_key_system_ui_control_center_qs_color`
- [开关] 已启用卡片磁贴颜色自定义 `prefs_key_system_ui_control_center_qs_big_open_color`
- [其他] 背景颜色 `prefs_key_system_ui_control_center_qs_big_bg_color`
- [其他] 图标颜色 `prefs_key_system_ui_control_center_qs_big_color`

### system_ui_status_bar_island_media_card.xml — 媒体卡片（16 项）

- [其他] 背景风格 `prefs_key_system_ui_island_media_control_background_mode`
- [开关] 颜色动画 `prefs_key_system_ui_island_media_control_control_color_anim`
- [开关] 自动反色 `prefs_key_system_ui_island_media_control_inverse_color`
- [其他] 氛围光效 `prefs_key_system_ui_island_media_control_ambient_light`
- [开关] 优化取色 `prefs_key_system_ui_island_media_control_ambient_light_opt`
- [开关] 启用布局修改 `prefs_key_system_ui_island_media_control_media_button_layout_switch`
- [开关] 动作按钮左对齐 `prefs_key_system_ui_island_media_control_media_button_actions_left_aligned`
- [开关] 隐藏设备切换按钮 `prefs_key_system_ui_island_media_control_media_button_hide_seamless`
- [其他] 媒体封面 `prefs_key_system_ui_island_media_control_media_album_mode`
- [其他] 媒体按钮顺序 `prefs_key_system_ui_island_media_control_media_button_mode`
- [开关] 启用字体修改 `prefs_key_system_ui_island_media_control_media_button_size_switch`
- [开关] 启用 `prefs_key_system_ui_island_media_control_progress_on`
- [其他] 媒体进度条样式 `prefs_key_system_ui_island_media_control_progress_mode`
- [其他] 媒体进度条按钮样式 `prefs_key_system_ui_island_media_control_progress_thumb_mode`
- [开关] 彗星进度条 `prefs_key_system_ui_island_media_control_progress_comet`
- [开关] 圆角进度条 `prefs_key_system_ui_island_media_control_progress_round`

### system_ui_status_bar_mobile.xml — 移动网络（13 项）

- [其他] 移动信号显示逻辑 `prefs_key_system_ui_status_bar_icon_mobile_network_signal_mode`
- [其他] 无 SIM 卡图标 `prefs_key_system_ui_status_bar_icon_mobile_network_signal_no_card`
- [开关] 隐藏 SIM 卡 1 信号图标 `prefs_key_system_ui_status_bar_icon_mobile_network_hide_card_1`
- [开关] 隐藏 SIM 卡 2 信号图标 `prefs_key_system_ui_status_bar_icon_mobile_network_hide_card_2`
- [其他] 新 HD 图标 `prefs_key_system_ui_status_bar_icon_new_hd`
- [其他] 小 HD 图标 `prefs_key_system_ui_status_bar_icon_small_hd`
- [其他] 大 HD 图标 `prefs_key_system_ui_status_bar_icon_big_hd`
- [开关] 隐藏漫游图标 `prefs_key_system_ui_status_bar_mobile_hide_roaming_icon`
- [开关] 隐藏 VoWiFi 图标 `prefs_key_system_ui_status_bar_icon_vowifi`
- [开关] 隐藏 VoLTE 图标 `prefs_key_system_ui_status_bar_icon_volte`
- [开关] 大网络类型图标 `prefs_key_system_ui_status_bar_big_mobile_network_type`
- [子页面] 移动网络类型图标 `prefs_key_system_ui_status_bar_mobile_type` `MobileNetworkTypeSettings`
- [子页面] 双排移动网络图标 `prefs_key_system_ui_statusbar_iconmanage_mobile_network` `DoubleLineNetworkSettings`

### system_ui_status_bar_new_clock_indicator.xml — 时钟指示器（13 项）

- [开关] 启用修改 `prefs_key_system_ui_statusbar_clock_all_status_enable`
- [开关] 不同步状态栏时钟设置 `prefs_key_system_ui_disable_clock_synch`
- [开关] 隐藏状态栏日期时钟 `prefs_key_system_ui_statusbar_clock_pad_hide`
- [其他] 状态栏日期时钟格式 `prefs_key_system_ui_statusbar_clock_editor_p`
- [分组] 状态栏日期时钟
- [分组] 状态栏时钟
- [分组] 通知中心大时钟
- [分组] 通知中心迷你时钟
- [其他] 状态栏时钟格式 `prefs_key_system_ui_statusbar_clock_editor_s`
- [其他] 通知中心大时钟格式 `prefs_key_system_ui_statusbar_clock_editor_b`
- [其他] 通知中心迷你时钟格式 `prefs_key_system_ui_statusbar_clock_editor_n`
- [其他] 时钟格式示例
- [其他] 状态栏时钟排列 `prefs_key_system_ui_statusbar_clock_style`

### system_ui_status_bar_battery_detail.xml — Display battery info（12 项）

- [开关] Display battery info `prefs_key_system_ui_statusbar_battery_detail_enable`
- [其他] Display content `prefs_key_system_ui_statusbar_battery_detail_content`
- [其他] Hide unit `prefs_key_system_ui_statusbar_battery_detail_hide_unit`
- [开关] Display on the right `prefs_key_system_ui_statusbar_battery_detail_at_right`
- [开关] Temperature is always displayed as a decimal `prefs_key_system_ui_statusbar_battery_detail_temp_decimal`
- [开关] Current value is always positive `prefs_key_system_ui_statusbar_battery_detail_positive`
- [开关] Fix current value ratio on some devices `prefs_key_system_ui_statusbar_battery_detail_fix_current_ratio`
- [开关] Display in single row `prefs_key_system_ui_statusbar_battery_detail_single_row`
- [开关] Reverse order `prefs_key_system_ui_statusbar_battery_detail_reverse_order`
- [开关] Show when charging only `prefs_key_system_ui_statusbar_battery_detail_in_charge`
- [开关] Bold `prefs_key_system_ui_statusbar_battery_detail_bold`
- [其他] Alignment `prefs_key_system_ui_statusbar_battery_detail_align`

### system_ui_status_bar_network_speed_indicator.xml — 网速指示器（10 项）

- [开关] 启用修改 `prefs_key_system_ui_statusbar_network_speed_all_status_enable`
- [开关] 网速字体大小 `prefs_key_system_ui_statusbar_network_speed_font_size_enable`
- [其他] 网速字体样式 `prefs_key_system_ui_statusbar_network_speed_font_style`
- [开关] 网速隐藏 *b/s 单位 `prefs_key_system_ui_statusbar_network_speed_sec_unit`
- [开关] 隐藏慢速 `prefs_key_system_ui_statusbar_network_speed_hide`
- [开关] 当上下行均为慢速时隐藏 `prefs_key_system_ui_statusbar_network_speed_hide_all`
- [其他] 网速指示器样式 `prefs_key_system_ui_statusbar_network_speed_style`
- [开关] 交换上下行网速与图标位置 `prefs_key_system_ui_statusbar_network_speed_swap_places`
- [其他] 指示器图标 `prefs_key_system_ui_statusbar_network_speed_icon`
- [其他] 水平对齐 `prefs_key_system_ui_statusbar_network_speed_align`

### system_ui_status_bar_music.xml — 焦点歌词（9 项）

- [提示/自定义布局] 使用前注意事项：\n- 开启此功能后，请勾选并重启系统界面作用域\n- 此功能需要搭配 \"SuperLyric\" 模块使用 `prefs_key_system_ui_statusbar_music_tip`
- [其他] 下载 SuperLyric
- [开关] 启用 `prefs_key_system_ui_statusbar_music_switch`
- [开关] 通知注册在 App 上 `prefs_key_system_ui_statusbar_music_show_app`
- [开关] 显示通知歌词 `prefs_key_system_ui_statusbar_music_show_notific`
- [开关] 不在息屏下显示焦点通知 `prefs_key_system_ui_statusbar_music_hide_aod`
- [开关] 使用自定义焦点息屏布局 `prefs_key_system_ui_statusbar_music_show_aod_mode`
- [开关] 隐藏状态栏时钟 `prefs_key_system_ui_statusbar_music_hide_clock`
- [开关] 点按显示时钟 `prefs_key_system_ui_statusbar_music_click_clock`

### system_ui_status_bar.xml — 状态栏（8 项）

- [子页面] 图标 `IconManageNewSettings`
- [子页面] 移动网络 `prefs_key_statubar_mobile_network` `MobileNetworkSettings`
- [子页面] 网速指示器 `NetworkSpeedIndicatorSettings`
- [子页面] 时钟指示器 `NewClockIndicatorSettings`
- [子页面] 焦点通知 `FocusNotificationSettings`
- [子页面] Battery info `BatteryDetailIndicatorSettings`
- [开关] 双击状态栏锁屏 `prefs_key_system_ui_status_bar_double_tap_to_sleep`
- [开关] 截屏时隐藏状态栏 `prefs_key_system_ui_status_bar_hide_icon`

### system_ui_navigation.xml — 导航栏（6 项）

- [其他] 旋转建议 `prefs_key_system_framework_other_rotation_button_int`
- [开关] 自定义导航栏 `prefs_key_system_ui_navigation_custom`
- [开关] [待补] `prefs_key_system_ui_hide_navigation_bar`
- [开关] 自定义手势提示线 `prefs_key_system_ui_navigation_handle_custom`
- [其他] 深色背景下颜色 `prefs_key_system_ui_navigation_handle_custom_color_dark`
- [其他] 浅色背景下颜色 `prefs_key_system_ui_navigation_handle_custom_color`

### system_ui_status_bar_mobile_network_type.xml — 移动网络类型图标（6 项）

- [其他] 显示逻辑 `prefs_key_system_ui_status_bar_icon_show_mobile_network_type`
- [开关] 隐藏移动网络活动指示器 `prefs_key_system_ui_status_bar_mobile_indicator`
- [其他] 自定义文本内容 `prefs_key_system_ui_status_bar_mobile_type_custom`
- [开关] 移动网络类型图标单独显示 `prefs_key_system_ui_statusbar_mobile_type_enable`
- [开关] 显示在信号左侧 `prefs_key_system_ui_statusbar_mobile_type_left`
- [开关] 加粗 `prefs_key_system_ui_statusbar_mobile_type_bold`

### system_ui.xml — -（5 项）

- [子页面] 锁屏 `LockScreenSettings`
- [子页面] 状态栏 `StatusBarSettings`
- [子页面] 导航栏 `NavigationSettings`
- [子页面] 通知与控制中心 `ControlCenterSettings`
- [子页面] 其他 `OtherSettings`

### system_ui_status_bar_focus_notif.xml — 焦点通知（5 项）

- [子页面] 媒体卡片 `prefs_key_system_ui_status_bar_island_media_card` `IslandMediaCardSettings`
- [子页面] 焦点歌词 `FocusNotificationLyricsSettings`
- [开关] 隐藏灵动舞台 `prefs_key_system_ui_status_bar_hide_smart_strong_toast`
- [其他] 短文本输出 `prefs_key_system_ui_status_bar_strong_toast_test_short_text`
- [其他] 长文本输出 `prefs_key_system_ui_status_bar_strong_toast_test_long_text`

### system_ui_status_bar_battery_styles.xml — 电池自定义（3 项）

- [开关] 交换电池图标与百分比位置 `prefs_key_system_ui_status_bar_battery_style_change_location`
- [开关] 启用修改 `prefs_key_system_ui_status_bar_battery_style_enable_custom`
- [开关] 加粗 `prefs_key_system_ui_status_bar_battery_style_bold`

### system_ui_control_center_card_tile.xml — -（2 项）

- [开关] 启用 `prefs_key_systemui_plugin_card_tiles_enabled`
- [其他] (无标题) `prefs_key_systemui_plugin_card_tiles_edit`

### system_ui_status_bar_doubleline_network.xml — 双排移动网络图标（2 项）

- [开关] 启用 `prefs_key_system_ui_statusbar_network_icon_enable`
- [其他] 图标样式 `prefs_key_system_ui_status_mobile_network_icon_style`

### system_ui_status_bar_new_clock_indicator_1.xml — 状态栏时钟（2 项）

- [开关] 加粗 `prefs_key_system_ui_statusbar_clock_bold`
- [其他] 双排对齐方式 `prefs_key_system_ui_statusbar_clock_double_1`

### system_ui_status_bar_new_clock_indicator_2.xml — 通知中心大时钟（1 项）

- [开关] 加粗 `prefs_key_system_ui_statusbar_clock_big_bold`

### system_ui_status_bar_new_clock_indicator_3.xml — 通知中心迷你时钟（1 项）

- [开关] 加粗 `prefs_key_system_ui_statusbar_clock_small_bold`

### system_ui_status_bar_new_clock_indicator_4.xml — 状态栏日期时钟（1 项）

- [开关] 加粗 `prefs_key_system_ui_statusbar_clock_pad_bold`

## 安全服务（8 页 / 74 项）


### security_center_other.xml — 其他（27 项）

- [开关] 解锁智能调节补光灯亮度 `prefs_key_security_center_beauty_light_auto`
- [开关] 解锁视频通话美颜 `prefs_key_security_center_beauty_face`
- [开关] 解锁隐私拍摄 `prefs_key_security_center_beauty_privacy`
- [开关] 解锁人像居中 `prefs_key_security_center_beauty_pc`
- [开关] 解锁贴边小窗 `prefs_key_security_center_unlock_side_hide_freeform`
- [开关] 移除气泡通知应用限制 `prefs_key_security_center_remove_conversation_bubble_settings_restriction`
- [开关] 禁用环境检查 `prefs_key_security_center_disable_root_check_environment`
- [开关] 禁用对应用的自动检查 `prefs_key_security_center_disable_safepay_auto_check`
- [开关] 精简手机管家首页 `prefs_key_security_center_simplify_home`
- [开关] 应用锁乱序 PIN `prefs_key_security_center_applock_pin_scramble`
- [开关] 禁止妙享省电 `prefs_key_security_center_power_saver`
- [开关] 禁止自启模糊预览图 `prefs_key_security_center_privacy_thumbnail_blur`
- [开关] 禁用体验优化 `prefs_key_security_center_score`
- [开关] 跳过倒计时限制 `prefs_key_security_center_skip_count_down_limit`
- [开关] 直接跳过警告 `prefs_key_security_center_skip_count_down_limit_direct`
- [开关] 禁用 Root 检测 `prefs_key_security_center_disable_root_check`
- [开关] 禁用 USB 安装拦截 `prefs_key_security_center_install_intercept`
- [开关] 绕过开启 USB 安装时的网络校验 `prefs_key_security_center_adb_install_verify`
- [开关] 禁止自动静默关闭无障碍权限 `prefs_key_security_center_disable_auto_turn_accessibility_off`
- [开关] 隐藏低电量警告 `prefs_key_security_center_remove_low_battery_reminder`
- [开关] 移除链式启动弹窗 `prefs_key_security_center_remove_open_app_confirmation_popup`
- [开关] 禁止发送 “发现恶意应用” 通知 `prefs_key_security_center_disable_send_malicious_app_notification`
- [开关] 解锁晕车缓解 `prefs_key_security_center_unlock_car_sickness`
- [开关] 快速启动游戏 `prefs_key_security_center_game_speed`
- [开关] SIM 卡安全保护跳过登录验证 `prefs_key_security_center_bypass_simlock_miaccount_auth`
- [开关] 移除 SIM 卡安全保护验证成功的弹窗 `prefs_key_security_center_remove_simlock_success_dialog`
- [开关] 禁用网络助手离线配置 `prefs_key_security_center_disable_offline_info_manager`

### security_center_sidebar.xml — 全局侧边栏（23 项）

- [开关] 隐藏侧边栏 `prefs_key_security_center_hide_sidebar`
- [开关] 保留侧滑展开 `prefs_key_security_center_leave_open`
- [开关] 全局侧边栏提示线自定义 `prefs_key_security_center_sidebar_line_color`
- [其他] 默认全局侧边栏提示线颜色 `prefs_key_security_center_sidebar_line_color_default`
- [其他] 深色背景下全局侧边栏提示线颜色 `prefs_key_security_center_sidebar_line_color_dark`
- [其他] 浅色背景下全局侧边栏提示线颜色 `prefs_key_security_center_sidebar_line_color_light`
- [开关] 全局侧边栏背景自定义 `prefs_key_se_enable`
- [开关] 使用高级材质 `prefs_key_security_center_blur_model_super`
- [开关] 图标反色 `prefs_key_security_center_invert_color`
- [其他] 全局侧边栏背景叠加层颜色 `prefs_key_security_center_color`
- [其他] 禁止展示推荐应用 `prefs_key_security_center_sidebar_show_suggest`
- [开关] 禁止展示推荐应用 `prefs_key_disable_security_center_sidebar_show_suggest`
- [开关] 禁用移除息屏听剧与熄屏挂机 `prefs_key_security_center_disable_remove_screen_hold_on`
- [开关] 解锁新版视频工具箱 `prefs_key_security_center_unlock_new_vb`
- [开关] 关闭杜比弹窗 `prefs_key_security_center_dolby_open`
- [开关] 解锁动态画面补偿 `prefs_key_security_center_unlock_memc`
- [开关] 解锁影像轮廓增强 `prefs_key_security_center_unlock_enhance_contours`
- [开关] 解锁极清播放 `prefs_key_security_center_unlock_s_resolution`
- [开关] Remove image enhancement temperature limit `prefs_key_security_center_remove_vb_temp_limit`
- [开关] 解锁游戏加速狂暴模式 `prefs_key_security_center_game_performance_wild_mode`
- [开关] 移除自动连招黑名单 `prefs_key_security_center_remove_macro_black_list`
- [开关] 解锁准星辅助 `prefs_key_security_center_unlock_gun_service`
- [开关] 移除游戏模式 Toast 弹窗 `prefs_key_security_center_remove_game_toast`

### security_center_app.xml — 应用管理（8 项）

- [其他] 应用管理列表排序 `prefs_key_security_center_app_default_sort`
- [开关] 移除举报键 `prefs_key_security_center_disable_ban`
- [开关] 全限制 `prefs_key_security_center_app_restrict`
- [开关] 应用打开链接管理 `prefs_key_security_center_app_default_setting`
- [开关] [待补] `prefs_key_security_center_aosp_app_info`
- [开关] [待补] `prefs_key_security_center_aosp_app_manager`
- [开关] 应用状态控制 `prefs_key_security_center_app_disable`
- [开关] 显示更多应用信息 `prefs_key_security_center_app_details`

### security_center_battery.xml — 省电与电池（8 项）

- [开关] 显示电池温度 `prefs_key_security_center_show_battery_temperature`
- [开关] 显示具体电池健康度 `prefs_key_security_center_show_battery_health`
- [开关] 显示更多电池信息 `prefs_key_secutity_center_battery_show_more_info`
- [开关] 显示详细耗电数据 `prefs_key_security_center_unlock_screen_time`
- [开关] 恢复显示耗电排行 `prefs_key_security_center_power_consumption_ranking`
- [开关] 解锁极速无线充电模式 `prefs_key_security_center_super_wireless_charge`
- [开关] 解锁低温超级续航 `prefs_key_security_center_battery_unlock_low_temp_ext_endurance`
- [开关] 补全充电与电池功能 `prefs_key_security_center_unlock_smart_charge`

### security_center.xml — 安全服务（6 项）

- [提示/自定义布局] 当前目标作用域来源可能并不是 Xiaomi 官方，出于安全考虑，已停止加载此作用域的所有功能，请尝试更改为 Xiaomi 官方发布的应用包，或前往 \"HyperCeiler 设置-开发者选项-调试模式\" 指定挂载版本后重试。 `prefs_key_security_unsupported`
- [子页面] 应用管理 `ApplicationsSettings`
- [分组] 省电与电池
- [子页面] 隐私保护 `prefs_key_security_center_privacy_safety` `PrivacySafetySettings`
- [子页面] 全局侧边栏 `SidebarSettings`
- [子页面] 其他 `OtherSettings`

### security_center_privacy_safety.xml — 隐私保护（1 项）

- [开关] 隐藏权限使用提示 `prefs_key_security_center_hide_xopt_mode_tip`

### securityadd.xml — 系统服务组件（1 项）

- [开关] 禁用游戏中心广告 `prefs_key_securityadd_disable_gb_ads`

### security_center_sidebar_function.xml — -（0 项）


## 设置（1 页 / 22 项）


### system_settings.xml — 设置（22 项）

- [开关] 解锁控制中心样式 `prefs_key_system_control_center_unlock_old`
- [开关] 解锁新版触感调节页面 `prefs_key_system_settings_novelty_haptic`
- [开关] 解锁应用布局优化 `prefs_key_system_settings_unlock_ui_mode`
- [开关] 国际版本设置页面 `prefs_key_system_settings_international_build`
- [开关] 禁止隐藏 \"Google\" 入口 `prefs_key_system_settings_unlock_google_header`
- [开关] 解锁小米澎湃 AI 入口 `prefs_key_system_settings_unlock_xiaomihyperai_entrance`
- [开关] 查看已保存的 WIFI 密码 `prefs_key_system_settings_safe_wifi`
- [开关] 解除网络加速应用限制 `prefs_key_system_settings_linkturbo`
- [开关] 新版 NFC 页面 `prefs_key_system_settings_new_nfc_page`
- [开关] 显示更多 VPN 类型 `prefs_key_system_settings_more_vpn_types`
- [开关] 允许永不锁屏 `prefs_key_system_settings_allow_never_lock_screen`
- [开关] 解锁超分辨率增强 `prefs_key_system_settings_ai_image_unlock_sr`
- [开关] 解锁 AI 智能场景优化与视频画质增强 `prefs_key_system_settings_ai_image_unlock_ai`
- [开关] 解锁动态画面补偿 `prefs_key_system_settings_ai_image_unlock_memc`
- [开关] 显示更多通知设置项 `prefs_key_system_settings_more_notification_settings`
- [开关] 显示在其他应用上层权限直达 `prefs_key_system_settings_permission_show_app_up`
- [开关] 无障碍服务权限直达 `prefs_key_system_settings_permission_accessibility`
- [开关] 接入 USB 时不弹窗 `prefs_key_system_settings_usb_mode`
- [其他] USB 默认选项 `prefs_key_system_settings_usb_mode_choose`
- [开关] 强制显示暂停执行已缓存的应用 `prefs_key_system_settings_apps_freezer`
- [开关] 解锁强制显示峰值刷新频率 `prefs_key_system_settings_develop_max_fps`
- [开关] 解锁极致模式 `prefs_key_system_settings_develop_speed_mode`

## 其他应用（16 页 / 75 项）


### various.xml — 其他（13 项）

- [子页面] 弹窗 `prefs_key_various_dialog` `AlertDialogSettings`
- [子页面] 隐藏特性 `prefs_key_various_open_aosp_something` `AOSPSettings`
- [开关] 解除剪贴板数量限制 `prefs_key_sogou_xiaomi_clipboard`
- [开关] 添加清空剪贴板按钮 `prefs_key_add_clipboard_clear`
- [其他] 解锁输入法全面屏优化 `prefs_key_various_unlock_ime_style`
- [其他] 目标输入法 `prefs_key_various_unlock_ime_apps`
- [其他] 左侧功能键 `prefs_key_various_aosp_ime_nav_bar_layout_start`
- [其他] 右侧功能键 `prefs_key_various_aosp_ime_nav_bar_layout_end`
- [开关] 显示完整输入法列表 `prefs_key_various_unlock_ime_show_all`
- [开关] 解锁输入法列表 UI 数量 `prefs_key_various_unlock_ime_ui_count`
- [开关] 禁用越界回弹 `prefs_key_various_no_overscroll`
- [开关] 移除部分文件夹不可选择限制 `prefs_key_various_disable_folder_cantuse`
- [其他] 系统标题样式 `prefs_key_various_collapse_miui_title`

### package_installer.xml — 应用包管理组件（10 项）

- [开关] 净化页面 `prefs_key_miui_package_installer_modify`
- [开关] 禁用推广 `prefs_key_miui_package_installer_disable_ad`
- [开关] 禁用风险检测 `prefs_key_miui_package_installer_install_risk`
- [开关] 阻断云端配置下发 `prefs_key_miui_package_installer_disable_cloud_check`
- [开关] 禁用安全守护提示 `prefs_key_miui_package_installer_safe_model_tip`
- [开关] 禁用频繁安装应用检查 `prefs_key_miui_package_installer_count_checking`
- [开关] 显示更多安装包信息 `prefs_key_miui_package_installer_apk_info`
- [开关] 解除系统应用安装限制 `prefs_key_miui_package_installer_update_system_app`
- [开关] 禁止安装时上传应用信息 `prefs_key_miui_package_installer_upload_appinfo`
- [其他] 自定义安装来源 `prefs_key_miui_package_installer_install_source`

### getapps.xml — 应用商店（9 项）

- [开关] 禁用推广 `prefs_key_market_disable_ads`
- [开关] 机型伪装 `prefs_key_market_device_modify_new1`
- [其他] 认证型号 `prefs_key_market_device_modify_model`
- [其他] 设备代号 `prefs_key_market_device_modify_device`
- [其他] 厂商 `prefs_key_market_device_modify_manufacturer`
- [开关] 禁止使用第三方图标 `prefs_key_market_disable_new_icon`
- [开关] 禁止监控应用安装和删除 `prefs_key_market_package_monitor`
- [开关] 强制应用安全检测为优秀 `prefs_key_market_bypass_risk_check`
- [开关] 禁用开启推送弹窗 `prefs_key_market_disable_start_push_dialog`

### updater.xml — 系统更新（9 项）

- [开关] 移除 OTA 验证 `prefs_key_updater_fuck_vab`
- [开关] 移除 “开启自动更新” 弹窗 `prefs_key_updater_diable_dialog`
- [开关] 启用伪装版本 `prefs_key_updater_enable_miui_version`
- [其他] 伪装类型 `prefs_key_updater_version_mode`
- [其他] 伪装大版本 `prefs_key_various_updater_big_version`
- [其他] 伪装热更新补丁版本 (XMS) `prefs_key_various_updater_xms_version`
- [其他] 伪装版本 `prefs_key_various_updater_miui_version`
- [其他] 伪装 Android 版本 `prefs_key_various_updater_android_version`
- [其他] 伪装机型 `prefs_key_updater_device`

### various_aosp.xml — 隐藏特性（6 项）

- [其他] 极暗
- [其他] 电池优化 `prefs_key_various_open_aosp_battery`
- [其他] 正在运行的服务
- [其他] 通知设置
- [其他] 应用管理
- [其他] 默认应用 `prefs_key_various_open_aosp_default_apps`

### milink.xml — 互联互通服务（5 项）

- [开关] 解锁小米妙享 `prefs_key_milink_unlock_mishare`
- [开关] 解锁全部应用流转 `prefs_key_milink_unlock_send_app`
- [开关] 关闭电脑管家剪贴板地区校验 `prefs_key_milink_disable_pc_clipboard_region_check`
- [开关] 强制支持摄像头流转 `prefs_key_milink_allow_camera_devices`
- [开关] 阻止生成乐播投屏相关文件 `prefs_key_milink_fuck_hpplay`

### various_mipad.xml — Pad 相关（5 项）

- [开关] 恢复 ESC 键功能 `prefs_key_mipad_input_restore_esc`
- [开关] 忽略触控笔按键手势 `prefs_key_mipad_input_ingore_gesture`
- [开关] 交换手势所需的手指数量 `prefs_key_mipad_input_need_finger_num`
- [开关] 移除触控笔蓝牙限制 `prefs_key_mipad_input_disable_bluetooth_new`
- [其他] 驱动版本 `prefs_key_mipad_input_bluetooth_version`

### barrage.xml — 弹幕通知（3 项）

- [开关] 允许所有应用使用弹幕通知 `prefs_key_barrage_any_barrage`
- [开关] 允许全局使用弹幕通知 `prefs_key_barrage_global_enable`
- [开关] 弹幕通知不可点击 `prefs_key_barrage_not_touchable`

### browser.xml — 浏览器（3 项）

- [开关] 解锁开发者选项 `prefs_key_browser_debug_mode`
- [开关] 启用预发环境 `prefs_key_browser_enable_debug_environment`
- [开关] 禁用网址黑名单 `prefs_key_browser_disable_blacklist`

### various_dialog.xml — -（3 项）

- [其他] 弹窗位置 `prefs_key_various_dialog_gravity`
- [开关] 弹窗后景模糊 `prefs_key_various_dialog_window_blur`
- [子页面] 自定义弹窗背景 `prefs_key_various_dialog_bg_blur` `CustomBackgroundSettings`

### various_dialog_custom_background.xml — -（3 项）

- [开关] 启用自定义 `prefs_key_custom_background_enabled`
- [其他] 颜色 `prefs_key_custom_background_color`
- [开关] 背景模糊 `prefs_key_custom_background_blur_enabled`

### guard_provider.xml — 系统安全组件（2 项）

- [开关] 禁用环境检查 `prefs_key_guard_provider_disable_root_check`
- [开关] 阻止自动上传已安装应用列表 `prefs_key_disable_upload_applist`

### huanji.xml — 小米换机（1 项）

- [开关] 允许迁移所有应用 `prefs_key_huanji_allow_all_apps`

### micloud_service.xml — 小米云服务（1 项）

- [开关] 应用数据云同步补全 `prefs_key_micloud_service_list`

### mishare.xml — [待补]（1 项）

- [开关] [待补] `prefs_key_disable_mishare_auto_off`

### trustservice.xml — MiTrustService（1 项）

- [开关] 禁用风险检测 `prefs_key_trustservice_disable_mrm`
