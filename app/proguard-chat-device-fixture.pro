# Applied only to benchmarkRelease when -PchatDeviceFixture=true.
# AndroidJUnitRunner.onCreate looks up androidx.tracing.Trace.beginSection
# from the instrumented app process. R8 would otherwise strip it.
-keep class androidx.tracing.Trace {
    public static void beginSection(java.lang.String);
    public static void endSection();
}

# Same classloader: AndroidJUnitRunner.registerTestStorage -> FileTestStorage
# -> TestDirCalculator uses kotlin.lazy. The app's minified kotlin-stdlib is
# the parent loader, so a stripped LazyKt shadows the test APK copy.
# 4c bench probe: NoClassDefFoundError kotlin.LazyKt on both HEAD and
# b4477f42b fixture benchmarkRelease APKs.
-keep class kotlin.LazyKt { *; }
-keep class kotlin.LazyKt__LazyJVMKt { *; }
-keep class kotlin.LazyKt__LazyKt { *; }
-keep class kotlin.Lazy { *; }
-keep class kotlin.SynchronizedLazyImpl { *; }
-keep class kotlin.UnsafeLazyImpl { *; }
-keep class kotlin.InitializedLazyImpl { *; }
-keep class kotlin.jvm.internal.** { *; }

# ChatDeviceFixtureApplication.onCreate: FirebaseApp.getApps(Context) is
# loaded from the minified app classloader. After LazyKt, the next crash
# was NoSuchMethodError getApps on both HEAD and the worktree overlay.
-keep class com.google.firebase.FirebaseApp {
    public static java.util.List getApps(android.content.Context);
    public static com.google.firebase.FirebaseApp initializeApp(android.content.Context, com.google.firebase.FirebaseOptions);
    public static com.google.firebase.FirebaseApp getInstance();
    public void setDataCollectionDefaultEnabled(boolean);
}
-keep class com.google.firebase.FirebaseOptions { *; }
-keep class com.google.firebase.FirebaseOptions$Builder { *; }

# Device tests import app.amber.agent.chatfixture.streamingTailLayout so
# HEAD and the b4477f42b worktree baseline share one JVM class.
-keep class app.amber.agent.chatfixture.StreamingTailLayoutFixtureAbiKt {
    androidx.compose.ui.Modifier streamingTailLayout(...);
}
