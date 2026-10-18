package app.amber.agent.data.sync

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRestoreStartupWiringTest {
    @Test
    fun `file restore recovery completes before workers and eager data consumers start`() {
        val source = listOf(
            File("src/main/java/app/amber/agent/AmberAgentApp.kt"),
            File("app/src/main/java/app/amber/agent/AmberAgentApp.kt"),
        ).first { it.isFile }.readText()
        val bootstrap = source.substringAfter("startKoin {").substringBefore("this.createNotificationChannel()")
        val modules = bootstrap.indexOf("modules(")
        val novelBridge = bootstrap.indexOf("koin.get<NovelWorkspaceRestoreBridge>()")
        val recovery = bootstrap.indexOf("recoverInterruptedRestore()")
        val workers = bootstrap.indexOf("workManagerFactory()")

        assertTrue("Register dependencies before resolving the restore owner", modules >= 0 && recovery > modules)
        assertTrue("Register the existing Novel restore owner before journal recovery", novelBridge > modules && novelBridge < recovery)
        assertTrue("Recover file journals before WorkManager can run persisted work", workers > recovery)
        assertTrue(
            "Application startup must wait for recovery before eager consumers are created",
            bootstrap.substring(modules, recovery).contains("runBlocking(Dispatchers.IO)"),
        )
    }
}
