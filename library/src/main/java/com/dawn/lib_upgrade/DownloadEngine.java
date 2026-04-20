package com.dawn.lib_upgrade;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * APK 下载引擎
 * <p>
 * 核心设计原则：
 * <ul>
 *   <li>断点续传：利用 HTTP Range 请求头实现</li>
 *   <li>自动重试：网络异常/IO异常自动重试，默认 5 次</li>
 *   <li>完整性校验：MD5 + 文件大小双重校验</li>
 *   <li>网络恢复自动续传：监听网络状态，恢复后自动继续下载</li>
 *   <li>线程安全：使用原子变量控制状态</li>
 * </ul>
 */
public class DownloadEngine {

    private static final String TAG = "DownloadEngine";

    // 下载状态
    public static final int STATE_IDLE = 0;
    public static final int STATE_DOWNLOADING = 1;
    public static final int STATE_PAUSED = 2;
    public static final int STATE_COMPLETED = 3;
    public static final int STATE_FAILED = 4;
    public static final int STATE_CANCELLED = 5;
    public static final int STATE_VERIFYING = 6;

    private final Context mContext;
    private final UpgradeConfig mConfig;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean mCancelled = new AtomicBoolean(false);
    private final AtomicBoolean mPaused = new AtomicBoolean(false);
    private final AtomicInteger mState = new AtomicInteger(STATE_IDLE);

    private UpgradeListener mListener;
    private NetworkMonitor mNetworkMonitor;

    private String mDownloadUrl;
    private String mSavePath;
    private String mTempPath;
    private String mExpectedMd5;
    private long mExpectedSize;
    private long mDownloadedBytes;
    private long mTotalBytes;

    public DownloadEngine(Context context, UpgradeConfig config) {
        this.mContext = context.getApplicationContext();
        this.mConfig = config != null ? config : UpgradeConfig.defaultConfig();
    }

    public void setListener(UpgradeListener listener) {
        this.mListener = listener;
    }

    /**
     * 开始下载
     *
     * @param url          下载地址
     * @param expectedMd5  期望 MD5（可为 null）
     * @param expectedSize 期望文件大小（<=0 跳过）
     */
    public void download(String url, String expectedMd5, long expectedSize) {
        if (mState.get() == STATE_DOWNLOADING) {
            Log.w(TAG, "Already downloading");
            return;
        }

        this.mDownloadUrl = url;
        this.mExpectedMd5 = expectedMd5;
        this.mExpectedSize = expectedSize;

        // 保存路径
        File dir = new File(mContext.getExternalFilesDir(null), mConfig.getDownloadDir());
        if (!dir.exists()) dir.mkdirs();
        this.mSavePath = new File(dir, mConfig.getFileName()).getAbsolutePath();
        this.mTempPath = mSavePath + ".tmp";

        mCancelled.set(false);
        mPaused.set(false);

        // 启用网络监听，断网后恢复自动续传
        if (mConfig.isAutoResumeOnNetworkRecover()) {
            setupNetworkMonitor();
        }

        startDownloadWithRetry();
    }

    /**
     * 暂停下载
     */
    public void pause() {
        mPaused.set(true);
        mState.set(STATE_PAUSED);
    }

    /**
     * 恢复下载
     */
    public void resume() {
        if (mState.get() == STATE_PAUSED || mState.get() == STATE_FAILED) {
            mPaused.set(false);
            mCancelled.set(false);
            startDownloadWithRetry();
        }
    }

    /**
     * 取消下载
     */
    public void cancel() {
        mCancelled.set(true);
        mState.set(STATE_CANCELLED);
        cleanupNetworkMonitor();
        postCallback(() -> {
            if (mListener != null) mListener.onCancelled();
        });
    }

    /**
     * 获取下载状态
     */
    public int getState() {
        return mState.get();
    }

    /**
     * 获取保存路径
     */
    public String getSavePath() {
        return mSavePath;
    }

    /**
     * 释放资源
     */
    public void release() {
        mCancelled.set(true);
        cleanupNetworkMonitor();
        mExecutor.shutdownNow();
    }

    // ==================== 核心下载逻辑 ====================

    private void startDownloadWithRetry() {
        mExecutor.execute(() -> {
            int retryCount = 0;
            boolean success = false;

            while (!success && retryCount <= mConfig.getMaxRetry() && !mCancelled.get()) {
                if (retryCount > 0) {
                    final int currentRetry = retryCount;
                    postCallback(() -> {
                        if (mListener != null) {
                            mListener.onRetry(currentRetry, mConfig.getMaxRetry(),
                                    "第 " + currentRetry + " 次重试");
                        }
                    });

                    // 重试等待
                    try {
                        Thread.sleep(mConfig.getRetryDelay());
                    } catch (InterruptedException e) {
                        if (mCancelled.get()) return;
                    }
                }

                // 检查网络
                if (!NetworkMonitor.isNetworkAvailable(mContext)) {
                    Log.w(TAG, "No network, waiting...");
                    retryCount++;
                    continue;
                }

                success = doDownload();

                if (!success && !mCancelled.get() && !mPaused.get()) {
                    retryCount++;
                }
            }

            if (mCancelled.get()) return;
            if (mPaused.get()) return;

            if (!success) {
                mState.set(STATE_FAILED);
                postCallback(() -> {
                    if (mListener != null) {
                        mListener.onFailed("下载失败，已重试 " + mConfig.getMaxRetry() + " 次");
                    }
                });
            }
        });
    }

    private boolean doDownload() {
        mState.set(STATE_DOWNLOADING);
        postCallback(() -> {
            if (mListener != null) mListener.onStart();
        });

        HttpURLConnection conn = null;
        InputStream is = null;
        RandomAccessFile raf = null;

        try {
            File tempFile = new File(mTempPath);

            // 断点续传：检查已下载的大小
            long downloadedBefore = 0;
            if (mConfig.isResumeEnabled() && tempFile.exists()) {
                downloadedBefore = tempFile.length();
            }

            URL urlObj = new URL(mDownloadUrl);
            conn = (HttpURLConnection) urlObj.openConnection();
            conn.setConnectTimeout(mConfig.getConnectTimeout());
            conn.setReadTimeout(mConfig.getReadTimeout());
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept-Encoding", "identity");

            // 断点续传请求头
            if (downloadedBefore > 0) {
                conn.setRequestProperty("Range", "bytes=" + downloadedBefore + "-");
            }

            conn.connect();

            int responseCode = conn.getResponseCode();

            // 206 = 断点续传成功; 200 = 完整下载
            if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                // 断点续传
                mTotalBytes = conn.getContentLength() + downloadedBefore;
                mDownloadedBytes = downloadedBefore;
            } else if (responseCode == HttpURLConnection.HTTP_OK) {
                // 完整下载（服务器不支持Range或从头开始）
                mTotalBytes = conn.getContentLength();
                mDownloadedBytes = 0;
                downloadedBefore = 0;
                // 删除旧的临时文件
                if (tempFile.exists()) tempFile.delete();
            } else if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                    || responseCode == HttpURLConnection.HTTP_MOVED_PERM
                    || responseCode == 307 || responseCode == 308) {
                // 重定向
                String newUrl = conn.getHeaderField("Location");
                conn.disconnect();
                if (newUrl != null && !newUrl.isEmpty()) {
                    mDownloadUrl = newUrl;
                    return doDownload();
                }
                return false;
            } else {
                Log.e(TAG, "HTTP error: " + responseCode);
                return false;
            }

            // 如果服务器返回了 Content-Length=-1 且有 expectedSize
            if (mTotalBytes <= 0 && mExpectedSize > 0) {
                mTotalBytes = mExpectedSize;
            }

            is = conn.getInputStream();
            raf = new RandomAccessFile(mTempPath, "rw");
            if (downloadedBefore > 0) {
                raf.seek(downloadedBefore);
            }

            byte[] buffer = new byte[mConfig.getBufferSize()];
            int len;
            long lastProgressTime = 0;

            while ((len = is.read(buffer)) != -1) {
                if (mCancelled.get()) return false;
                if (mPaused.get()) return false;

                raf.write(buffer, 0, len);
                mDownloadedBytes += len;

                // 限制进度回调频率（最多每 200ms 一次）
                long now = System.currentTimeMillis();
                if (now - lastProgressTime >= 200 || mDownloadedBytes >= mTotalBytes) {
                    lastProgressTime = now;
                    final long current = mDownloadedBytes;
                    final long total = mTotalBytes;
                    final int percent = total > 0 ? (int) (current * 100 / total) : -1;
                    postCallback(() -> {
                        if (mListener != null) mListener.onProgress(current, total, percent);
                    });
                }
            }

            // 下载完成，关闭文件
            raf.close();
            raf = null;

            // 校验完整性
            mState.set(STATE_VERIFYING);

            // 大小校验
            File downloaded = new File(mTempPath);
            if (mConfig.isSizeCheckEnabled() && mExpectedSize > 0) {
                if (downloaded.length() != mExpectedSize) {
                    Log.e(TAG, "Size mismatch: expected=" + mExpectedSize
                            + ", actual=" + downloaded.length());
                    // 大小不匹配，删除重下
                    downloaded.delete();
                    return false;
                }
            }

            // 如果 totalBytes 已知，校验下载大小
            if (mTotalBytes > 0 && downloaded.length() < mTotalBytes) {
                Log.e(TAG, "Incomplete download: expected=" + mTotalBytes
                        + ", actual=" + downloaded.length());
                return false;
            }

            // MD5 校验
            if (mConfig.isMd5CheckEnabled() && mExpectedMd5 != null && !mExpectedMd5.isEmpty()) {
                String actualMd5 = ApkVerifier.calculateMd5(mTempPath);
                if (!mExpectedMd5.equalsIgnoreCase(actualMd5)) {
                    Log.e(TAG, "MD5 mismatch: expected=" + mExpectedMd5 + ", actual=" + actualMd5);
                    final String expected = mExpectedMd5;
                    final String actual = actualMd5;
                    postCallback(() -> {
                        if (mListener != null) mListener.onVerifyFailed(mTempPath, expected, actual);
                    });
                    // MD5 不匹配，删除后重下
                    downloaded.delete();
                    return false;
                }
                postCallback(() -> {
                    if (mListener != null) mListener.onVerifySuccess(mSavePath);
                });
            }

            // 重命名临时文件为最终文件
            File finalFile = new File(mSavePath);
            if (finalFile.exists()) finalFile.delete();
            if (!downloaded.renameTo(finalFile)) {
                Log.e(TAG, "Rename failed");
                return false;
            }

            mState.set(STATE_COMPLETED);
            postCallback(() -> {
                if (mListener != null) mListener.onDownloadComplete(mSavePath);
            });

            cleanupNetworkMonitor();
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Download IO error", e);
            return false;
        } finally {
            if (raf != null) {
                try { raf.close(); } catch (IOException ignored) {}
            }
            if (is != null) {
                try { is.close(); } catch (IOException ignored) {}
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // ==================== 网络监听 ====================

    private void setupNetworkMonitor() {
        if (mNetworkMonitor != null) return;
        mNetworkMonitor = new NetworkMonitor(mContext);
        mNetworkMonitor.setListener(new NetworkMonitor.OnNetworkChangeListener() {
            @Override
            public void onNetworkAvailable() {
                // 网络恢复，如果之前是下载中或失败状态，自动恢复
                int state = mState.get();
                if (state == STATE_FAILED || state == STATE_PAUSED) {
                    Log.d(TAG, "Network recovered, resuming download");
                    resume();
                }
            }

            @Override
            public void onNetworkLost() {
                Log.d(TAG, "Network lost during download");
            }
        });
        mNetworkMonitor.register();
    }

    private void cleanupNetworkMonitor() {
        if (mNetworkMonitor != null) {
            mNetworkMonitor.unregister();
            mNetworkMonitor = null;
        }
    }

    private void postCallback(Runnable runnable) {
        mMainHandler.post(runnable);
    }
}
