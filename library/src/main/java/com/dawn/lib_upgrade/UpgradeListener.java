package com.dawn.lib_upgrade;

/**
 * 升级/下载状态回调
 */
public interface UpgradeListener {

    /** 开始下载 */
    default void onStart() {}

    /**
     * 下载进度
     *
     * @param current  已下载字节数
     * @param total    文件总字节数（-1 表示未知）
     * @param percent  百分比 0~100
     */
    default void onProgress(long current, long total, int percent) {}

    /**
     * 下载完成，APK 已保存到本地
     *
     * @param apkPath APK 文件路径
     */
    default void onDownloadComplete(String apkPath) {}

    /** MD5 校验通过 */
    default void onVerifySuccess(String apkPath) {}

    /** MD5 校验失败 */
    default void onVerifyFailed(String apkPath, String expectedMd5, String actualMd5) {}

    /**
     * 正在重试
     *
     * @param currentRetry 当前重试次数
     * @param maxRetry     最大重试次数
     * @param reason       重试原因
     */
    default void onRetry(int currentRetry, int maxRetry, String reason) {}

    /**
     * 下载失败
     *
     * @param error 错误信息
     */
    default void onFailed(String error) {}

    /** 已取消 */
    default void onCancelled() {}

    /** 开始安装 */
    default void onInstallStart(String apkPath) {}
}
