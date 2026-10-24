# Release compactado (R8). Hilt, Compose e kotlinx.serialization trazem as próprias regras; estas
# garantem as classes salvas no letras.json e as rotas de navegação (que viram JSON por nome).
-keep @kotlinx.serialization.Serializable class com.juliano.stagebook.** { *; }
-keepclassmembers class com.juliano.stagebook.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers enum com.juliano.stagebook.** { *; }
