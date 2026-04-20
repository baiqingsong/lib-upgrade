package com.dawn.lib_upgrade;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * 前台下载服务
 * <p>
 * 在前台 Service 中执行下载任务，避免后台被系统杀死。
 * 同时在通知栏展示下载进度。
 */
public class DownloadService extends Service {

    private static final String TAG = "DownloadService";
    private static final int NOTIFICATION_ID = 9527;

    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_MD5 = "extra_md5";
    public static final String EXTRA_SIZE = "extra_size";

    private DownloadEngine mEngine;
    private NotificationManager mNotificationManager;
    private NotificationCompat.Builder mNotificationBuilder;
    private UpgradeConfig mConfig;

    /** 静态引用，用于外部获取 Service 实例状态 */
    private static UpgradeListener sExternalListener;
    private static UpgradeConfig sConfig;

    /**
     * 启动下载服务
     */
    public static void start(Context context, String url, String md5, long size,
                             UpgradeConfig config, UpgradeListener listener) {
        sConfig = config;
        sExternalListener = listener;
        Intent intent = new Intent(context, DownloadService.class);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_MD5, md5);
        intent.putExtra(EXTRA_SIZE, size);

        if (config != null && config.isUseForegroundService()) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    /**
     * 停止下载服务
     */
    public static void stop(Context context) {
        context.stopService(new Intent(context, DownloadService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mConfig = sConfig != null ? sConfig : UpgradeConfig.defaultConfig();
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String url = intent.getStringExtra(EXTRA_URL);
        String md5 = intent.getStringExtra(EXTRA_MD5);
        long size = intent.getLongExtra(EXTRA_SIZE, 0);

        if (url == null || url.isEmpty()) {
            Log.e(TAG, "Download URL is empty");
            stopSelf();
            return START_NOT_STICKY;
        }

        // 创建前台通知
        if (mConfig.isUseForegroundService()) {
            startForeground(NOTIFICATION_ID, buildNotification("准备下载...", 0));
        }

        // 启动下载引擎
        mEngine = new DownloadEngine(this, mConfig);
        mEngine.setListener(new UpgradeListener() {
            @Override
            public void onStart() {
                updateNotification("开始下载...", 0);
                if (sExternalListener != null) sExternalListener.onStart();
            }

            @Override
            public void onProgress(long current, long total, int percent) {
                updateNotification("下载中 " + percent + "%", percent);
                if (sExternalListener != null) sExternalListener.onProgress(current, total, percent);
            }

            @Override
            public void onDownloadComplete(String apkPath) {
                updateNotification("下载完成", 100);
                if (sExternalListener != null) sExternalListener.onDownloadComplete(apkPath);

                // 自动安装
                if (mConfig.isAutoInstall()) {
                    if (sExternalListener != null) sExternalListener.onInstallStart(apkPath);
                    ApkInstaller.install(DownloadService.this, apkPath);
                }

                stopSelf();
            }

            @Override
            public void onVerifySuccess(String apkPath) {
                if (sExternalListener != null) sExternalListener.onVerifySuccess(apkPath);
            }

            @Override
            public void onVerifyFailed(String apkPath, String expectedMd5, String actualMd5) {
                if (sExternalListener != null) sExternalListener.onVerifyFailed(apkPath, expectedMd5, actualMd5);
            }

            @Override
            public void onRetry(int currentRetry, int maxRetry, String reason) {
                updateNotification("重试中 (" + currentRetry + "/" + maxRetry + ")", -1);
                if (sExternalListener != null) sExternalListener.onRetry(currentRetry, maxRetry, reason);
            }

            @Override
            public void onFailed(String error) {
                updateNotification("下载失败", -1);
                if (sExternalListener != null) sExternalListener.onFailed(error);
                stopSelf();
            }

            @Override
            public void onCancelled() {
                if (sExternalListener != null) sExternalListener.onCancelled();
                stopSelf();
            }
        });

        mEngine.download(url, md5, size);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mEngine != null) {
            mEngine.release();
        }
        sExternalListener = null;
        sConfig = null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ==================== 通知 ====================

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                mConfig.getNotificationChannelId(),
                mConfig.getNotificationChannelName(),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("应用版本更新下载通知");
        channel.setSound(null, null);
        mNotificationManager.createNotificationChannel(channel);
    }

    private Notification buildNotification(String text, int progress) {
        mNotificationBuilder = new NotificationCompat.Builder(this, mConfig.getNotificationChannelId())
                .setContentTitle("应用更新")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setAutoCancel(false);

        if (progress >= 0) {
            mNotificationBuilder.setProgress(100, progress, false);
        } else {
            mNotificationBuilder.setProgress(0, 0, true);
        }

        return mNotificationBuilder.build();
    }

    private void updateNotification(String text, int progress) {
        if (!mConfig.isNotificationEnabled()) return;
        if (mNotificationBuilder == null) return;

        mNotificationBuilder.setContentText(text);
        if (progress >= 0) {
            mNotificationBuilder.setProgress(100, progress, false);
        } else {
            mNotificationBuilder.setProgress(0, 0, true);
        }

        if (progress == 100 || text.contains("失败")) {
            mNotificationBuilder.setOngoing(false);
            mNotificationBuilder.setAutoCancel(true);
            if (progress == 100) {
                mNotificationBuilder.setSmallIcon(android.R.drawable.stat_sys_download_done);
            }
        }

        mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
    }
}
