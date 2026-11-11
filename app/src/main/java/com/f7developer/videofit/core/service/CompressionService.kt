package com.f7developer.videofit.core.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.f7developer.videofit.MainActivity
import com.f7developer.videofit.R
import com.f7developer.videofit.VideoFitApp
import com.f7developer.videofit.core.engine.CompressionException
import com.f7developer.videofit.core.engine.CompressionJob
import com.f7developer.videofit.core.engine.CompressionResult
import com.f7developer.videofit.core.model.VideoCodec
import com.f7developer.videofit.core.service.CompressionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs the compression batch in a foreground service so it survives the screen
 * turning off or the user leaving the app. The work itself is hardware codec
 * work on background threads; the service only adds lifecycle guarantees.
 */
class CompressionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            CompressionController.cancel()
            return START_NOT_STICKY
        }
        if (job?.isActive == true) return START_NOT_STICKY

        val args = intent ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val uris = args.getStringArrayListExtra(EXTRA_URIS) ?: emptyList()
        if (uris.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val targetBytes = args.getLongExtra(EXTRA_TARGET, 0L)
        val codec = runCatching {
            VideoCodec.valueOf(args.getStringExtra(EXTRA_CODEC) ?: VideoCodec.H264.name)
        }.getOrDefault(VideoCodec.H264)
        val maxShortSide = args.getIntExtra(EXTRA_MAX_SHORT_SIDE, 0).takeIf { it > 0 }
        val measureQuality = args.getBooleanExtra(EXTRA_MEASURE, true)
        val allowDownscale = args.getBooleanExtra(EXTRA_DOWNSCALE, true)

        startForegroundCompat(buildNotification(0, uris.size, "Preparing", 0f, true))
        acquireWakeLock()
        CompressionController.startClock()
        CompressionController.publish(
            CompressionState.Running(
                index = 0,
                total = uris.size,
                name = "Preparing",
                progress = 0f,
                overall = 0f,
                etaSeconds = null,
                speedLabel = null,
                thermalWarning = false,
            ),
        )

        val app = application as VideoFitApp
        job = scope.launch {
            val results = mutableListOf<CompressionResult>()
            val failures = mutableListOf<String>()
            var cancelled = false

            for ((index, uriString) in uris.withIndex()) {
                if (CompressionController.isCancelled()) {
                    cancelled = true
                    break
                }
                val uri = Uri.parse(uriString)
                val name = Uri.decode(uri.lastPathSegment ?: "video").substringAfterLast('/')
                try {
                    CompressionController.publish(
                        CompressionState.Running(
                            index = index,
                            total = uris.size,
                            name = name,
                            progress = 0f,
                            overall = index.toFloat() / uris.size,
                            etaSeconds = null,
                            speedLabel = "Analysing",
                            thermalWarning = thermalWarning(),
                        ),
                    )
                    val info = withContext(Dispatchers.IO) {
                        app.engine.probeVideo(uri, name)
                    }
                    // Content URIs end in a numeric id; prefer the real file name
                    // that MediaStore reports once the video is probed.
                    val displayName = info.displayName.ifBlank { name }
                    val plan = app.engine.planFor(
                        info, targetBytes, codec, allowDownscale, maxShortSide,
                    )
                    val jobSpec = CompressionJob(
                        uri = uri,
                        displayName = displayName,
                        info = info,
                        targetBytes = targetBytes,
                        codec = codec,
                        allowDownscale = allowDownscale,
                        maxShortSide = maxShortSide,
                        measureQuality = measureQuality,
                    )

                    val result = app.engine.compress(
                        job = jobSpec,
                        onProgress = { progress ->
                            val overall = (index + progress) / uris.size
                            val eta = estimateEta(overall)
                            CompressionController.publish(
                                CompressionState.Running(
                                    index = index,
                                    total = uris.size,
                                    name = jobSpec.displayName,
                                    progress = progress,
                                    overall = overall,
                                    etaSeconds = eta,
                                    speedLabel = formatProgress(progress, eta),
                                    thermalWarning = thermalWarning(),
                                ),
                            )
                            updateNotification(
                                buildNotification(index, uris.size, jobSpec.displayName, overall, false),
                            )
                        },
                        isCancelled = { CompressionController.isCancelled() },
                    )
                    results += result
                    app.settings.recordCompression()
                    Log.d(TAG, "done ${result.outputFile} ${result.outputBytes}B in ${result.elapsedMs}ms")
                } catch (t: Throwable) {
                    if (CompressionController.isCancelled()) {
                        cancelled = true
                        break
                    }
                    val message = when (t) {
                        is CompressionException -> t.message ?: "Compression failed"
                        else -> t.message ?: "Compression failed"
                    }
                    failures += "${name.ifBlank { "Video" }}: $message"
                    Log.w(TAG, "failed $uriString", t)
                }
            }

            if (CompressionController.isCancelled()) cancelled = true
            CompressionController.publish(
                CompressionState.Finished(results, failures, cancelled),
            )
            withContext(Dispatchers.Main) {
                stopForegroundCompat()
                releaseWakeLock()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun estimateEta(overall: Float): Long? {
        if (overall <= 0.02f) return null
        val elapsed = CompressionController.elapsedSeconds()
        val total = elapsed / overall
        return (total - elapsed).toLong().coerceIn(0, 60 * 60)
    }

    private fun formatProgress(progress: Float, eta: Long?): String {
        val percent = (progress * 100).toInt()
        return if (eta != null && eta > 2) "~${eta}s left" else "$percent%"
    }

    private fun thermalWarning(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val power = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return power.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        // A missing WAKE_LOCK permission must not take the whole job down: the
        // service still runs, it just loses the guarantee while the CPU sleeps.
        wakeLock = runCatching {
            power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VideoFit:compress").apply {
                setReferenceCounted(false)
                acquire(60 * 60 * 1000L)
            }
        }.onFailure { Log.w(TAG, "wake lock unavailable", it) }.getOrNull()
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(
        index: Int,
        total: Int,
        name: String,
        overall: Float,
        indeterminate: Boolean,
    ) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(if (total > 1) "${index + 1}/$total · $name" else name)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setProgress(1000, (overall * 1000).toInt(), indeterminate)
        .setContentIntent(openAppIntent())
        .addAction(
            0,
            getString(R.string.cancel),
            PendingIntent.getService(
                this,
                2,
                Intent(this, CompressionService::class.java).setAction(ACTION_CANCEL),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        1,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(notification: android.app.Notification) {
        runCatching {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CompressionService"
        private const val CHANNEL_ID = "videofit_compression"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_CANCEL = "com.f7developer.videofit.CANCEL"
        private const val EXTRA_URIS = "uris"
        private const val EXTRA_TARGET = "target"
        private const val EXTRA_CODEC = "codec"
        private const val EXTRA_MAX_SHORT_SIDE = "maxShortSide"
        private const val EXTRA_MEASURE = "measure"
        private const val EXTRA_DOWNSCALE = "downscale"

        fun start(
            context: Context,
            uris: List<String>,
            targetBytes: Long,
            codec: VideoCodec,
            maxShortSide: Int?,
            measureQuality: Boolean,
            allowDownscale: Boolean,
        ) {
            val intent = Intent(context, CompressionService::class.java).apply {
                putStringArrayListExtra(EXTRA_URIS, ArrayList(uris))
                putExtra(EXTRA_TARGET, targetBytes)
                putExtra(EXTRA_CODEC, codec.name)
                putExtra(EXTRA_MAX_SHORT_SIDE, maxShortSide ?: 0)
                putExtra(EXTRA_MEASURE, measureQuality)
                putExtra(EXTRA_DOWNSCALE, allowDownscale)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancel(context: Context) {
            val intent = Intent(context, CompressionService::class.java).setAction(ACTION_CANCEL)
            runCatching { context.startService(intent) }
            CompressionController.cancel()
        }
    }
}
