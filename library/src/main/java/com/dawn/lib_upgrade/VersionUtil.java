package com.dawn.lib_upgrade;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

/**
 * 版本工具类
 * <p>
 * 获取当前应用版本信息、比较版本号。
 */
public class VersionUtil {

    private static final String TAG = "VersionUtil";

    private VersionUtil() {
    }

    /**
     * 获取当前应用的 versionCode
     */
    public static int getVersionCode(Context context) {
        try {
            PackageInfo pi = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return (int) pi.getLongVersionCode();
            }
            return pi.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "getVersionCode error", e);
            return 0;
        }
    }

    /**
     * 获取当前应用的 versionName
     */
    public static String getVersionName(Context context) {
        try {
            PackageInfo pi = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return pi.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "getVersionName error", e);
            return "";
        }
    }

    /**
     * 获取当前应用的包名
     */
    public static String getPackageName(Context context) {
        return context.getPackageName();
    }

    /**
     * 获取 APK 文件的 versionCode
     *
     * @param context context
     * @param apkPath APK 文件路径
     */
    public static int getApkVersionCode(Context context, String apkPath) {
        try {
            PackageInfo pi = context.getPackageManager()
                    .getPackageArchiveInfo(apkPath, 0);
            if (pi != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    return (int) pi.getLongVersionCode();
                }
                return pi.versionCode;
            }
        } catch (Exception e) {
            Log.e(TAG, "getApkVersionCode error", e);
        }
        return 0;
    }

    /**
     * 获取 APK 文件的 versionName
     */
    public static String getApkVersionName(Context context, String apkPath) {
        try {
            PackageInfo pi = context.getPackageManager()
                    .getPackageArchiveInfo(apkPath, 0);
            if (pi != null) {
                return pi.versionName;
            }
        } catch (Exception e) {
            Log.e(TAG, "getApkVersionName error", e);
        }
        return "";
    }

    /**
     * 获取 APK 文件的包名
     */
    public static String getApkPackageName(Context context, String apkPath) {
        try {
            PackageInfo pi = context.getPackageManager()
                    .getPackageArchiveInfo(apkPath, 0);
            if (pi != null) {
                return pi.packageName;
            }
        } catch (Exception e) {
            Log.e(TAG, "getApkPackageName error", e);
        }
        return "";
    }

    /**
     * 判断 VersionInfo 是否有新版本
     *
     * @param context     context
     * @param versionInfo 远程版本信息
     * @return 是否有新版本
     */
    public static boolean hasNewVersion(Context context, VersionInfo versionInfo) {
        if (versionInfo == null) return false;
        return versionInfo.hasNewVersion(getVersionCode(context));
    }

    /**
     * 判断是否必须强制更新
     */
    public static boolean isMustUpdate(Context context, VersionInfo versionInfo) {
        if (versionInfo == null) return false;
        return versionInfo.isMustUpdate(getVersionCode(context));
    }

    /**
     * 比较两个语义化版本号（如 "1.2.3" vs "1.3.0"）
     *
     * @return 正数表示 v1 > v2, 负数 v1 < v2, 0 相等
     */
    public static int compareVersionName(String v1, String v2) {
        if (v1 == null) v1 = "";
        if (v2 == null) v2 = "";
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int maxLen = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < maxLen; i++) {
            int n1 = i < parts1.length ? parseIntSafe(parts1[i]) : 0;
            int n2 = i < parts2.length ? parseIntSafe(parts2[i]) : 0;
            if (n1 != n2) return n1 - n2;
        }
        return 0;
    }

    /**
     * 检查本地已有的 APK 是否为有效的新版本
     *
     * @param context context
     * @param apkPath APK 路径
     * @return true 表示是一个有效的更新包
     */
    public static boolean isValidUpdateApk(Context context, String apkPath) {
        if (apkPath == null) return false;
        java.io.File file = new java.io.File(apkPath);
        if (!file.exists() || file.length() == 0) return false;

        String apkPkg = getApkPackageName(context, apkPath);
        if (!context.getPackageName().equals(apkPkg)) return false;

        int apkCode = getApkVersionCode(context, apkPath);
        return apkCode > getVersionCode(context);
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
