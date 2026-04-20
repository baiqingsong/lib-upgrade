package com.dawn.libupgrade;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.dawn.lib_upgrade.ApkInstaller;
import com.dawn.lib_upgrade.NetworkMonitor;
import com.dawn.lib_upgrade.UpgradeConfig;
import com.dawn.lib_upgrade.UpgradeListener;
import com.dawn.lib_upgrade.UpgradeManager;
import com.dawn.lib_upgrade.VersionInfo;
import com.dawn.lib_upgrade.VersionUtil;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "UpgradeDemo";

    private TextView tvStatus;
    private TextView tvVersion;
    private ProgressBar progressBar;
    private Button btnUpgrade;
    private Button btnPause;
    private Button btnCancel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);
        tvVersion = findViewById(R.id.tv_version);
        progressBar = findViewById(R.id.progress_bar);
        btnUpgrade = findViewById(R.id.btn_upgrade);
        btnPause = findViewById(R.id.btn_pause);
        btnCancel = findViewById(R.id.btn_cancel);

        // 显示当前版本
        String versionName = VersionUtil.getVersionName(this);
        int versionCode = VersionUtil.getVersionCode(this);
        tvVersion.setText("当前版本: " + versionName + " (" + versionCode + ")");

        // 初始化 UpgradeManager
        UpgradeConfig config = UpgradeConfig.builder()
                .maxRetry(5)
                .autoInstall(true)
                .notificationEnabled(true)
                .useForegroundService(true)
                .autoResumeOnNetworkRecover(true)
                .build();

        UpgradeManager.getInstance()
                .init(this)
                .setConfig(config)
                .setListener(new UpgradeListener() {
                    @Override
                    public void onStart() {
                        tvStatus.setText("开始下载...");
                        Log.d(TAG, "onStart");
                    }

                    @Override
                    public void onProgress(long current, long total, int percent) {
                        progressBar.setProgress(percent);
                        tvStatus.setText("下载中: " + percent + "% ("
                                + formatSize(current) + "/" + formatSize(total) + ")");
                    }

                    @Override
                    public void onDownloadComplete(String apkPath) {
                        tvStatus.setText("下载完成: " + apkPath);
                        progressBar.setProgress(100);
                        Log.d(TAG, "onDownloadComplete: " + apkPath);
                    }

                    @Override
                    public void onVerifySuccess(String apkPath) {
                        tvStatus.setText("校验通过");
                        Log.d(TAG, "onVerifySuccess");
                    }

                    @Override
                    public void onVerifyFailed(String apkPath, String expectedMd5, String actualMd5) {
                        tvStatus.setText("校验失败: MD5不匹配");
                        Log.e(TAG, "onVerifyFailed: expected=" + expectedMd5
                                + ", actual=" + actualMd5);
                    }

                    @Override
                    public void onRetry(int currentRetry, int maxRetry, String reason) {
                        tvStatus.setText("重试中 (" + currentRetry + "/" + maxRetry + ")");
                        Log.d(TAG, "onRetry: " + reason);
                    }

                    @Override
                    public void onFailed(String error) {
                        tvStatus.setText("下载失败: " + error);
                        Log.e(TAG, "onFailed: " + error);
                    }

                    @Override
                    public void onCancelled() {
                        tvStatus.setText("已取消");
                        progressBar.setProgress(0);
                        Log.d(TAG, "onCancelled");
                    }

                    @Override
                    public void onInstallStart(String apkPath) {
                        tvStatus.setText("开始安装...");
                        Log.d(TAG, "onInstallStart: " + apkPath);
                    }
                });

        // 升级按钮
        btnUpgrade.setOnClickListener(v -> {
            // 检查网络
            if (!NetworkMonitor.isNetworkAvailable(this)) {
                tvStatus.setText("无网络连接");
                return;
            }

            // 检查安装权限
            if (!ApkInstaller.canInstall(this)) {
                ApkInstaller.openInstallPermissionSetting(this);
                return;
            }

            // 模拟版本信息（实际使用时从服务器获取）
            VersionInfo versionInfo = VersionInfo.builder()
                    .versionName("2.0.0")
                    .versionCode(200)
                    .downloadUrl("https://example.com/app-release.apk")
                    .forceUpdate(false)
                    .build();

            UpgradeManager.getInstance().upgrade(versionInfo);
        });

        // 暂停按钮
        btnPause.setOnClickListener(v -> {
            UpgradeManager.getInstance().pause();
            tvStatus.setText("已暂停");
        });

        // 取消按钮
        btnCancel.setOnClickListener(v -> {
            UpgradeManager.getInstance().cancel();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        UpgradeManager.getInstance().release();
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "0B";
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1fMB", bytes / (1024.0 * 1024));
        return String.format("%.1fGB", bytes / (1024.0 * 1024 * 1024));
    }
}
