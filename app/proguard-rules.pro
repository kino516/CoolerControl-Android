# ============================================================
# CoolerControl release 混淆规则
#
# release 已启用 minify + shrinkResources。空规则文件在本项目当前代码下
# 也能构建成功（Compose / OkHttp / kotlinx-coroutines 都自带 consumer rules），
# 但以下入口依赖运行时名称，必须显式保留。
# ============================================================

# 保留行号，便于线上崩溃栈定位。
# 不保留 LocalVariableTable —— 那会显著增大体积且对定位帮助有限。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# HiddenApiBypass 通过反射访问隐藏 API（TemplateApplication 用它开启预测性返回）
-keep class org.lsposed.hiddenapibypass.** { *; }
-dontwarn org.lsposed.hiddenapibypass.**

# 数据模型：目前全部由 org.json 手工解析，不依赖反射；
# 保留字段名是为了防止后续改用反射式绑定（Gson / Moshi）时被混淆成 a/b/c，
# 那类问题只在运行时暴露，排查成本很高。
-keep class com.example.kernelsustyleuikit.data.model.** { *; }

# activity-alias 的 targetActivity 在 Manifest 中以完整类名引用，
# 且运行时通过 ComponentName 拼名启停（见 CcAppIcon），类名不能变。
-keep class com.example.kernelsustyleuikit.TemplateApplication { *; }
-keep class com.example.kernelsustyleuikit.ui.MainActivity { *; }
