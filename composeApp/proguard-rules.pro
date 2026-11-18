# ── Proteção contra Engenharia Reversa e Análise por IA ──
# Ofuscação de nomes de classes, métodos e campos
-repackageclasses ''
-allowaccessmodification

# Remoção de atributos de depuração que facilitam descompilação
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
-dontusemixedcaseclassnames

# Remover chamadas de logs e debug em builds de produção
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Ocultar e proteger modelos de dados de fontes
-keepclassmembers class * implements kotlinx.serialization.KSerializer {
    *** Companion;
}

# Manter pontos de entrada Android essenciais
-keep public class app.anim.MainActivity extends androidx.activity.ComponentActivity
