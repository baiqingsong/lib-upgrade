package com.dawn.lib_upgrade;

/**
 * 升级配置
 */
public class UpgradeConfig {

    /** 默认最大重试次数 */
    public static final int DEFAULT_MAX_RETRY = 5;

    /** 默认重试间隔（毫秒） */
    public static final long DEFAULT_RETRY_DELAY = 3000L;

    /** 默认连接超时（毫秒） */
    public static final int DEFAULT_CONNECT_TIMEOUT = 30000;

    /** 默认读取超时（毫秒） */
    public static final int DEFAULT_READ_TIMEOUT = 60000;

    /** 默认下载缓冲区大小 */
    public static final int DEFAULT_BUFFER_SIZE = 8192;

    /** 下载保存目录名 */
    private String downloadDir = "upgrade";

    /** 下载文件名 */
    private String fileName = "update.apk";

    /** 最大重试次数 */
    private int maxRetry = DEFAULT_MAX_RETRY;

    /** 重试间隔（毫秒） */
    private long retryDelay = DEFAULT_RETRY_DELAY;

    /** 连接超时（毫秒） */
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;

    /** 读取超时（毫秒） */
    private int readTimeout = DEFAULT_READ_TIMEOUT;

    /** 缓冲区大小 */
    private int bufferSize = DEFAULT_BUFFER_SIZE;

    /** 是否启用断点续传 */
    private boolean resumeEnabled = true;

    /** 是否启用 MD5 校验 */
    private boolean md5CheckEnabled = true;

    /** 是否启用文件大小校验 */
    private boolean sizeCheckEnabled = true;

    /** 是否在通知栏显示下载进度 */
    private boolean notificationEnabled = true;

    /** 通知渠道 ID */
    private String notificationChannelId = "upgrade_channel";

    /** 通知渠道名称 */
    private String notificationChannelName = "应用更新";

    /** 下载完成后是否自动安装 */
    private boolean autoInstall = true;

    /** 是否使用前台Service（保证后台下载不被杀） */
    private boolean useForegroundService = true;

    /** 网络断开后自动恢复下载 */
    private boolean autoResumeOnNetworkRecover = true;

    private UpgradeConfig() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static UpgradeConfig defaultConfig() {
        return new UpgradeConfig();
    }

    public static class Builder {
        private final UpgradeConfig config = new UpgradeConfig();

        public Builder downloadDir(String dir) { config.downloadDir = dir; return this; }
        public Builder fileName(String name) { config.fileName = name; return this; }
        public Builder maxRetry(int maxRetry) { config.maxRetry = maxRetry; return this; }
        public Builder retryDelay(long delay) { config.retryDelay = delay; return this; }
        public Builder connectTimeout(int timeout) { config.connectTimeout = timeout; return this; }
        public Builder readTimeout(int timeout) { config.readTimeout = timeout; return this; }
        public Builder bufferSize(int size) { config.bufferSize = size; return this; }
        public Builder resumeEnabled(boolean enabled) { config.resumeEnabled = enabled; return this; }
        public Builder md5CheckEnabled(boolean enabled) { config.md5CheckEnabled = enabled; return this; }
        public Builder sizeCheckEnabled(boolean enabled) { config.sizeCheckEnabled = enabled; return this; }
        public Builder notificationEnabled(boolean enabled) { config.notificationEnabled = enabled; return this; }
        public Builder notificationChannelId(String id) { config.notificationChannelId = id; return this; }
        public Builder notificationChannelName(String name) { config.notificationChannelName = name; return this; }
        public Builder autoInstall(boolean auto) { config.autoInstall = auto; return this; }
        public Builder useForegroundService(boolean use) { config.useForegroundService = use; return this; }
        public Builder autoResumeOnNetworkRecover(boolean auto) { config.autoResumeOnNetworkRecover = auto; return this; }

        public UpgradeConfig build() { return config; }
    }

    // ==================== Getters ====================

    public String getDownloadDir() { return downloadDir; }
    public String getFileName() { return fileName; }
    public int getMaxRetry() { return maxRetry; }
    public long getRetryDelay() { return retryDelay; }
    public int getConnectTimeout() { return connectTimeout; }
    public int getReadTimeout() { return readTimeout; }
    public int getBufferSize() { return bufferSize; }
    public boolean isResumeEnabled() { return resumeEnabled; }
    public boolean isMd5CheckEnabled() { return md5CheckEnabled; }
    public boolean isSizeCheckEnabled() { return sizeCheckEnabled; }
    public boolean isNotificationEnabled() { return notificationEnabled; }
    public String getNotificationChannelId() { return notificationChannelId; }
    public String getNotificationChannelName() { return notificationChannelName; }
    public boolean isAutoInstall() { return autoInstall; }
    public boolean isUseForegroundService() { return useForegroundService; }
    public boolean isAutoResumeOnNetworkRecover() { return autoResumeOnNetworkRecover; }
}
