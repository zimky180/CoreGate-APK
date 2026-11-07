# Release uses R8; Android manifest components are preserved by AGP defaults.
# Preserve source/line metadata for retrace using the published CI mapping artifact.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
