# 本工程 minifyEnabled = false，这份规则目前不生效。
# 保留它是为了万一以后要开混淆时不用重新想。
#
# 需要保留的东西只有一类：通过反射或跨进程字符串引用的入口。
# 本工程没有反射调用，MainActivity 由系统按清单实例化，
# AGP 默认就会保留 Android 组件，所以这里其实可以留空。

-keep class com.zimky.coregateUS.MainActivity { *; }
-keepattributes SourceFile,LineNumberTable