# Release compactado (R8). Hilt, Compose e kotlinx.serialization trazem as próprias regras; estas
# garantem as classes salvas no stagebook.json e as rotas de navegação (que viram JSON por nome).
-keep @kotlinx.serialization.Serializable class br.app.stagebook.** { *; }
-keepclassmembers class br.app.stagebook.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers enum br.app.stagebook.** { *; }
