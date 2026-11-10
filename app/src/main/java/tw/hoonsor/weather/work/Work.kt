package tw.hoonsor.weather.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import tw.hoonsor.weather.Graph
import tw.hoonsor.weather.R
import tw.hoonsor.weather.domain.Aqi
import tw.hoonsor.weather.domain.PlaceKind
import tw.hoonsor.weather.domain.PlacePicker
import tw.hoonsor.weather.domain.Typhoon
import tw.hoonsor.weather.ui.MainActivity
import tw.hoonsor.weather.widget.WeatherWidgets
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** 背景排程：定時更新（小工具、突發通知）與早上降雨提醒 */
object Scheduler {
    private const val REFRESH = "refresh"
    private const val MORNING = "morning"

    fun ensure(context: Context, replace: Boolean = false) {
        val wm = WorkManager.getInstance(context)
        val mins = Graph.settings.refreshMinutes.toLong()
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(mins, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        wm.enqueueUniquePeriodicWork(REFRESH, if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP, req)
        scheduleMorning(context, replace)
    }

    fun refreshNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork("refresh_now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<RefreshWorker>().build())
    }

    fun scheduleMorning(context: Context, replace: Boolean = true) {
        val wm = WorkManager.getInstance(context)
        if (!Graph.settings.morningRain) { wm.cancelUniqueWork(MORNING); return }
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atTime(Graph.settings.morningTime)
        if (!at.isAfter(now.plusMinutes(1))) at = at.plusDays(1)
        val req = OneTimeWorkRequestBuilder<MorningWorker>().setInitialDelay(Duration.between(now, at).toMinutes(), TimeUnit.MINUTES).build()
        wm.enqueueUniqueWork(MORNING, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, req)
    }
}

class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Graph.init(applicationContext)
        val repo = Graph.repo
        val kinds = buildList {
            add(PlaceKind.CURRENT)
            if (repo.settings.place(PlaceKind.HOME) != null) add(PlaceKind.HOME)
            if (repo.settings.place(PlaceKind.SCHOOL) != null) add(PlaceKind.SCHOOL)
        }
        var ok = 0
        for (k in kinds) if (runCatching { repo.snapshot(k, maxAgeMin = 10) }.isSuccess) ok++
        runCatching { Alerts.check(applicationContext) }
        runCatching { WeatherWidgets.pushAll(applicationContext) }
        return if (ok > 0 || kinds.isEmpty()) Result.success() else Result.retry()
    }
}

class MorningWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Graph.init(applicationContext)
        runCatching { Alerts.morning(applicationContext) }
        Scheduler.scheduleMorning(applicationContext)
        return Result.success()
    }
}

/** 突發通知（F18）與早上降雨提醒（F19） */
object Alerts {
    suspend fun check(context: Context) {
        val repo = Graph.repo
        val s = repo.settings
        if (s.notifyTyphoon) {
            val t = repo.typhoons().list.firstOrNull()
            if (t != null && t.warning == Typhoon.Warning.SEA_LAND) {
                val mine = listOfNotNull(s.place(PlaceKind.HOME), s.place(PlaceKind.SCHOOL)).map { it.county }.filter { it in t.landWarningCounties }
                if (mine.isNotEmpty() && s.notifiedOnce("land_${t.name}_${LocalDate.now()}")) {
                    Notifier.show(context, Notifier.ALERT, 1, "${t.kind}${t.name}：陸上警報", repo.typhoonText(t, short = false))
                }
            }
        }
        if (s.notifyAir) {
            val kind = repo.autoKind()
            val a = repo.cached(kind)?.airTomorrow
            if (a != null && a.aqi > 200 && s.notifiedOnce("air_${LocalDate.now().plusDays(1)}")) {
                val lv = Aqi.level(a.aqi)
                Notifier.show(context, Notifier.ALERT, 2, "明天空氣品質${lv.color}", "${a.area}明天預報 AQI ${a.aqi}，${lv.effect}，${lv.advice}。")
            }
        }
    }

    suspend fun morning(context: Context) {
        val repo = Graph.repo
        val today = LocalDate.now()
        val kind = PlacePicker.auto(repo.calendar().isWorkday(today), repo.settings.place(PlaceKind.HOME) != null, repo.settings.place(PlaceKind.SCHOOL) != null)
        val snap = repo.snapshot(kind, maxAgeMin = 60)
        val d = snap.day(today) ?: return
        val rain = maxOf(d.rainDay ?: 0, d.rainNight ?: 0)
        if (rain < 30) return
        val where = when (kind) { PlaceKind.SCHOOL -> "學校"; PlaceKind.HOME -> "家裡"; else -> snap.town.name }
        val verb = if (rain >= 50) "記得帶傘" else "帶把折傘"
        Notifier.show(context, Notifier.RAIN, 3, "今天${where}可能下雨", "${where}（${snap.town.name}）今天降雨機率 $rain%，$verb。")
    }
}

object Notifier {
    const val ALERT = "alert"
    const val RAIN = "rain"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(ALERT, "颱風與空品警戒", NotificationManager.IMPORTANCE_HIGH).apply { description = "颱風陸上警報、明天空品紫色以上" })
        nm.createNotificationChannel(NotificationChannel(RAIN, "早上降雨提醒", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "出門前，今天會下雨才提醒" })
    }

    fun canNotify(context: Context) = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, channel: String, id: Int, title: String, text: String) {
        if (!canNotify(context)) return
        val pi = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_notify).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(pi).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }
}
