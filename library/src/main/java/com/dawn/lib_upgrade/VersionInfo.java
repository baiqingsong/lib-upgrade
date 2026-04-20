package com.dawn.lib_upgrade;

import java.io.Serializable;

/**
 * 版本信息模型
 */
public class VersionInfo implements Serializable {

    /** 版本号（如 "2.0.0"） */
    private String versionName;

    /** 版本码（如 200） */
    private int versionCode;

    /** APK 下载地址 */
    private String downloadUrl;

    /** APK 文件大小（字节），用于校验完整性 */
    private long fileSize;

    /** APK MD5 校验值（可选，用于完整性校验） */
    private String md5;

    /** 更新日志 */
    private String updateLog;

    /** 是否强制更新 */
    private boolean forceUpdate;

    /** 最低兼容版本码（低于此版本必须更新） */
    private int minVersionCode;

    public VersionInfo() {
    }

    /**
     * 判断是否需要更新
     *
     * @param currentVersionCode 当前应用的 versionCode
     * @return 是否有新版本
     */
    public boolean hasNewVersion(int currentVersionCode) {
        return versionCode > currentVersionCode;
    }

    /**
     * 判断是否必须强制更新
     *
     * @param currentVersionCode 当前应用的 versionCode
     * @return 是否必须更新
     */
    public boolean isMustUpdate(int currentVersionCode) {
        return forceUpdate || (minVersionCode > 0 && currentVersionCode < minVersionCode);
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final VersionInfo info = new VersionInfo();

        public Builder versionName(String versionName) {
            info.versionName = versionName;
            return this;
        }

        public Builder versionCode(int versionCode) {
            info.versionCode = versionCode;
            return this;
        }

        public Builder downloadUrl(String downloadUrl) {
            info.downloadUrl = downloadUrl;
            return this;
        }

        public Builder fileSize(long fileSize) {
            info.fileSize = fileSize;
            return this;
        }

        public Builder md5(String md5) {
            info.md5 = md5;
            return this;
        }

        public Builder updateLog(String updateLog) {
            info.updateLog = updateLog;
            return this;
        }

        public Builder forceUpdate(boolean forceUpdate) {
            info.forceUpdate = forceUpdate;
            return this;
        }

        public Builder minVersionCode(int minVersionCode) {
            info.minVersionCode = minVersionCode;
            return this;
        }

        public VersionInfo build() {
            return info;
        }
    }

    // ==================== Getters & Setters ====================

    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = versionName; }

    public int getVersionCode() { return versionCode; }
    public void setVersionCode(int versionCode) { this.versionCode = versionCode; }

    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getMd5() { return md5; }
    public void setMd5(String md5) { this.md5 = md5; }

    public String getUpdateLog() { return updateLog; }
    public void setUpdateLog(String updateLog) { this.updateLog = updateLog; }

    public boolean isForceUpdate() { return forceUpdate; }
    public void setForceUpdate(boolean forceUpdate) { this.forceUpdate = forceUpdate; }

    public int getMinVersionCode() { return minVersionCode; }
    public void setMinVersionCode(int minVersionCode) { this.minVersionCode = minVersionCode; }

    @Override
    public String toString() {
        return "VersionInfo{" +
                "versionName='" + versionName + '\'' +
                ", versionCode=" + versionCode +
                ", downloadUrl='" + downloadUrl + '\'' +
                ", fileSize=" + fileSize +
                ", forceUpdate=" + forceUpdate +
                '}';
    }
}
