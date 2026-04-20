package com.dawn.lib_upgrade;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;

/**
 * APK 安装工具
 * <p>
 * 兼容 Android 7.0+ FileProvider 和 Android 8.0+ 未知来源安装。
 */
public class ApkInstaller {

    private static final String TAG = "ApkInstaller";

    private ApkInstaller() {
    }

    /**
     * 安装 APK
     *
     * @param context context
     * @param apkPath APK 文件路径
     */
    public static void install(Context context, String apkPath) {
        File file = new File(apkPath);
        if (!file.exists()) {
            Log.e(TAG, "APK file not exists: " + apkPath);
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Uri uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Android 7.0+ 使用 FileProvider
            String authority = context.getPackageName() + ".upgrade.fileprovider";
            uri = FileProvider.getUriForFile(context, authority, file);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            uri = Uri.fromFile(file);
        }

        intent.setDataAndType(uri, "application/vnd.android.package-archive");

        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "install error", e);
            // 降级：尝试不指定 type
            try {
                Intent fallback = new Intent(Intent.ACTION_VIEW);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                fallback.setData(uri);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
                context.startActivity(fallback);
            } catch (Exception e2) {
                Log.e(TAG, "install fallback error", e2);
            }
        }
    }

    /**
     * 安装前检查（Android 8.0+ 未知来源安装权限）
     *
     * @return true 表示允许安装
     */
    public static boolean canInstall(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return context.getPackageManager().canRequestPackageInstalls();
        }
        return true;
    }

    /**
     * 跳转到「允许安装未知来源」设置页
     */
    public static void openInstallPermissionSetting(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    /**
     * 静默安装（需要 root 或 system app 权限）
     * 普通应用无法使用此方法
     */
    public static boolean silentInstall(String apkPath) {
        try {
            String cmd = "pm install -r " + apkPath;
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            Log.e(TAG, "silentInstall error", e);
            return false;
        }
    }
}
