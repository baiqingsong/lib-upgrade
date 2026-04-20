package com.dawn.lib_upgrade;

import android.content.Context;
import android.util.Log;

import java.io.File;

/**
 * 升级管理器 — 统一入口
 * <p>
 * 整合版本检测、APK下载、完整性校验、安装的完整流程。
 * <p>
 * 核心保障：
 * <ul>
 *   <li>断点续传：下载中断后自动从断点恢复</li>
 *   <li>自动重试：失败自动重试（默认5次）</li>
 *   <li>网络恢复自动续传：断网恢复后自动继续下载</li>
 *   <li>MD5 + 文件大小双重校验：保证 APK 完整性</li>
 *   <li>前台 Service：保证后台下载不被系统杀死</li>
 *   <li>FileProvider 安全安装：兼容 Android 7.0+</li>
 * </ul>
 * <p>
 * 使用方式：
 * <pre>
 *   UpgradeManager.getInstance()
 *       .init(context)
 *       .setConfig(UpgradeConfig.builder().maxRetry(5).build())
 *       .setListener(listener)
 *       .upgrade(versionInfo);
 * </pre>
 */
public class UpgradeManager {

    private static final String TAG = "UpgradeManager";
    private static volatile UpgradeManager sInstance;

    private Context mContext;
    private UpgradeConfig mConfig;
    private UpgradeListener mListener;
    private DownloadEngine mEngine;
    private boolean mInitialized = false;

    private UpgradeManager() {
    }

    public static UpgradeManager getInstance() {
        if (sInstance == null) {
            synchronized (UpgradeManager.class) {
                if (sInstance == null) {
                    sInstance = new UpgradeManager();
                }
            }
        }
        return sInstance;
    }

    /**
     * 初始化
     */
    public UpgradeManager init(Context context) {
        this.mContext = context.getApplicationContext();
        this.mInitialized = true;
        return this;
    }

    /**
     * 设置配置
     */
    public UpgradeManager setConfig(UpgradeConfig config) {
        this.mConfig = config;
        return this;
    }

    /**
     * 设置监听器
     */
    public UpgradeManager setListener(UpgradeListener listener) {
        this.mListener = listener;
        return this;
    }

    /**
     * 获取配置
     */
    public UpgradeConfig getConfig() {
        if (mConfig == null) {
            mConfig = UpgradeConfig.defaultConfig();
        }
        return mConfig;
    }

    // ==================== 升级流程 ====================

    /**
     * 执行完整升级流程（推荐方式）
     * <p>
     * 流程：版本判断 → 检查本地缓存 → 下载（前台Service） → 校验 → 安装
     *
     * @param versionInfo 远程版本信息
     */
    public void upgrade(VersionInfo versionInfo) {
        checkInit();

        if (versionInfo == null) {
            notifyFailed("VersionInfo 为 null");
            return;
        }

        // 版本判断
        if (!VersionUtil.hasNewVersion(mContext, versionInfo)) {
            Log.d(TAG, "当前已是最新版本");
            return;
        }

        String url = versionInfo.getDownloadUrl();
        if (url == null || url.isEmpty()) {
            notifyFailed("下载地址为空");
            return;
        }

        // 检查本地是否已有完整的 APK
        String cachedApk = checkCachedApk(versionInfo);
        if (cachedApk != null) {
            Log.d(TAG, "Found cached APK: " + cachedApk);
            notifyComplete(cachedApk);
            if (getConfig().isAutoInstall()) {
                notifyInstallStart(cachedApk);
                ApkInstaller.install(mContext, cachedApk);
            }
            return;
        }

        // 使用前台 Service 下载
        if (getConfig().isUseForegroundService()) {
            DownloadService.start(mContext, url, versionInfo.getMd5(),
                    versionInfo.getFileSize(), getConfig(), mListener);
        } else {
            // 不使用 Service，直接在引擎中下载
            downloadDirect(url, versionInfo.getMd5(), versionInfo.getFileSize());
        }
    }

    /**
     * 直接通过 URL 下载 APK（不检查版本）
     */
    public void downloadApk(String url) {
        downloadApk(url, null, 0);
    }

    /**
     * 直接通过 URL 下载 APK（带校验）
     */
    public void downloadApk(String url, String md5, long size) {
        checkInit();

        if (url == null || url.isEmpty()) {
            notifyFailed("下载地址为空");
            return;
        }

        if (getConfig().isUseForegroundService()) {
            DownloadService.start(mContext, url, md5, size, getConfig(), mListener);
        } else {
            downloadDirect(url, md5, size);
        }
    }

    /**
     * 不使用 Service 的直接下载
     */
    private void downloadDirect(String url, String md5, long size) {
        if (mEngine != null) {
            mEngine.release();
        }
        mEngine = new DownloadEngine(mContext, getConfig());
        mEngine.setListener(new UpgradeListener() {
            @Override
            public void onStart() {
                if (mListener != null) mListener.onStart();
            }

            @Override
            public void onProgress(long current, long total, int percent) {
                if (mListener != null) mListener.onProgress(current, total, percent);
            }

            @Override
            public void onDownloadComplete(String apkPath) {
                if (mListener != null) mListener.onDownloadComplete(apkPath);
                if (getConfig().isAutoInstall()) {
                    if (mListener != null) mListener.onInstallStart(apkPath);
                    ApkInstaller.install(mContext, apkPath);
                }
            }

            @Override
            public void onVerifySuccess(String apkPath) {
                if (mListener != null) mListener.onVerifySuccess(apkPath);
            }

            @Override
            public void onVerifyFailed(String apkPath, String expectedMd5, String actualMd5) {
                if (mListener != null) mListener.onVerifyFailed(apkPath, expectedMd5, actualMd5);
            }

            @Override
            public void onRetry(int currentRetry, int maxRetry, String reason) {
                if (mListener != null) mListener.onRetry(currentRetry, maxRetry, reason);
            }

            @Override
            public void onFailed(String error) {
                if (mListener != null) mListener.onFailed(error);
            }

            @Override
            public void onCancelled() {
                if (mListener != null) mListener.onCancelled();
            }
        });
        mEngine.download(url, md5, size);
    }

    /**
     * 暂停下载
     */
    public void pause() {
        if (mEngine != null) mEngine.pause();
    }

    /**
     * 恢复下载
     */
    public void resume() {
        if (mEngine != null) mEngine.resume();
    }

    /**
     * 取消下载
     */
    public void cancel() {
        if (mEngine != null) mEngine.cancel();
        DownloadService.stop(mContext);
    }

    /**
     * 安装已下载的 APK
     */
    public void installApk(String apkPath) {
        checkInit();
        ApkInstaller.install(mContext, apkPath);
    }

    /**
     * 获取当前下载状态
     */
    public int getDownloadState() {
        return mEngine != null ? mEngine.getState() : DownloadEngine.STATE_IDLE;
    }

    /**
     * 清除已下载的 APK 缓存
     */
    public void clearCache() {
        checkInit();
        File dir = new File(mContext.getExternalFilesDir(null), getConfig().getDownloadDir());
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) f.delete();
            }
        }
    }

    /**
     * 获取缓存 APK 路径（如果存在）
     */
    public String getCachedApkPath() {
        checkInit();
        File dir = new File(mContext.getExternalFilesDir(null), getConfig().getDownloadDir());
        File apk = new File(dir, getConfig().getFileName());
        return apk.exists() ? apk.getAbsolutePath() : null;
    }

    /**
     * 释放资源
     */
    public void release() {
        if (mEngine != null) {
            mEngine.release();
            mEngine = null;
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 检查本地缓存的 APK 是否可用
     */
    private String checkCachedApk(VersionInfo versionInfo) {
        File dir = new File(mContext.getExternalFilesDir(null), getConfig().getDownloadDir());
        File apk = new File(dir, getConfig().getFileName());
        if (!apk.exists() || apk.length() == 0) return null;

        // 大小校验
        if (getConfig().isSizeCheckEnabled() && versionInfo.getFileSize() > 0) {
            if (apk.length() != versionInfo.getFileSize()) {
                apk.delete();
                return null;
            }
        }

        // MD5 校验
        if (getConfig().isMd5CheckEnabled()
                && versionInfo.getMd5() != null && !versionInfo.getMd5().isEmpty()) {
            String actualMd5 = ApkVerifier.calculateMd5(apk.getAbsolutePath());
            if (!versionInfo.getMd5().equalsIgnoreCase(actualMd5)) {
                apk.delete();
                return null;
            }
        }

        // 包名和版本校验
        if (!VersionUtil.isValidUpdateApk(mContext, apk.getAbsolutePath())) {
            apk.delete();
            return null;
        }

        return apk.getAbsolutePath();
    }

    private void checkInit() {
        if (!mInitialized || mContext == null) {
            throw new IllegalStateException("UpgradeManager 未初始化，请先调用 init(context)");
        }
    }

    private void notifyFailed(String error) {
        Log.e(TAG, error);
        if (mListener != null) mListener.onFailed(error);
    }

    private void notifyComplete(String path) {
        if (mListener != null) mListener.onDownloadComplete(path);
    }

    private void notifyInstallStart(String path) {
        if (mListener != null) mListener.onInstallStart(path);
    }
}
