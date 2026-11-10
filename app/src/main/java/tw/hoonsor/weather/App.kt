package tw.hoonsor.weather

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import tw.hoonsor.weather.data.Repository
import tw.hoonsor.weather.data.Settings
import tw.hoonsor.weather.data.Vault
import tw.hoonsor.weather.work.Notifier
import tw.hoonsor.weather.work.Scheduler

/** 單例：ContentProvider 比 Application.onCreate 早建立，所以 AgentProvider 也會呼叫 [init] */
object Graph {
    lateinit var repo: Repository
        private set
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var ready = false

    @Synchronized
    fun init(context: Context) {
        if (ready) return
        val app = context.applicationContext
        repo = Repository(app, Settings(app), Vault(app))
        ready = true
    }

    val settings get() = repo.settings
    val vault get() = repo.vault
}

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        Notifier.createChannels(this)
        Scheduler.ensure(this)
    }
}
