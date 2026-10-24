# 本工程 minifyEnabled = false，这份规则目前不生效。
# 保留它是为了万一以后要开混淆时不用重新想。

# ── libxposed ──────────────────────────────────────────────────
# 以下三条逐字照抄自 libxposed/example 的 app/proguard-rules.pro。
# 注解包只在编译期可见、运行时不加载，故不警告。
-dontwarn io.github.libxposed.annotation.**
# 混淆时把 java_init.list 里的入口类全限定名一起替换，
# 否则框架按原名去找，找不到就认为模块没有入口。
-adaptresourcefilecontents META-INF/xposed/java_init.list
# 保住继承 XposedModule 的入口类（含无参构造）：类名可混淆，但必须能被反射实例化。
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
