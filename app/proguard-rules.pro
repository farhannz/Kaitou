# Paddle Lite
-keep class com.baidu.paddle.lite.** { *; }

# Lucene general token attributes
-keep class org.apache.lucene.analysis.tokenattributes.* { *; }
-keep class org.apache.lucene.analysis.tokenattributes.*Impl { *; }

# Lucene Japanese (Kuromoji) token attributes
-keep class org.apache.lucene.analysis.ja.tokenattributes.* { *; }
-keep class org.apache.lucene.analysis.ja.tokenattributes.*Impl { *; }

# Lucene reflection-related utilities
-keep class org.apache.lucene.util.Attribute* { *; }

# Keep original names for all Lucene Japanese dictionary classes
-keepnames class org.apache.lucene.analysis.ja.dict.** { *; }

# Ensure all dictionary-related classes are preserved
-keep class org.apache.lucene.analysis.ja.dict.** { *; }

-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** {
    native <methods>;
}
-keep class ai.djl.huggingface.tokenizers.** { *; }
-keepclassmembers class ai.djl.huggingface.tokenizers.** {
    native <methods>;
}



## Optional: disable obfuscation/optimization if still debugging
-dontobfuscate
#-dontoptimize