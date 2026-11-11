# libxposed API 102：入口类由 META-INF/xposed/java_init.list 按类名加载，
# 允许混淆但必须保留类与无参构造函数，并由 -adaptresourcefilecontents 同步改写资源中的类名。
-dontwarn io.github.libxposed.annotation.**
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
-adaptresourcefilecontents META-INF/xposed/java_init.list

# 规则入口类由 XposedInitEntry 按字符串类名反射实例化，必须保留类名与无参构造函数，
# 否则 R8 会同时裁剪类与构造函数，导致该进程的规则全部加载失败。
-keep class * extends com.sevtinge.hyperceiler.libhook.base.BaseLoad {
    public <init>();
}
