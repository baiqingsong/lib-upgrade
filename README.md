# lib-upgrade

Android APK 版本升级库 — 可靠的 APK 下载、版本判断、版本升级更新。

## 核心特性

- **断点续传** — HTTP Range 断点下载，中断后自动从断点恢复
- **自动重试** — 下载失败自动重试（默认5次），确保只要有网络就能下载成功
- **网络恢复自动续传** — 监听网络状态变化，断网恢复后自动继续下载
- **完整性双重校验** — MD5 + 文件大小校验，保证 APK 完整无损
- **前台 Service 下载** — 避免后台被系统杀死，保证下载完成
- **通知栏进度** — 实时显示下载进度
- **FileProvider 安全安装** — 兼容 Android 7.0+ 安全安装
- **版本智能判断** — 支持 versionCode 对比、语义化版本号比较、强制更新判断
- **缓存复用** — 已下载 APK 自动复用，避免重复下载

## 引入

```gradle
// 根 build.gradle
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}

// 模块 build.gradle
dependencies {
    implementation 'com.github.baiqingsong:lib-upgrade:1.0.0'
}
```

## 快速使用

### 初始化

```java
UpgradeConfig config = UpgradeConfig.builder()
        .maxRetry(5)                          // 最大重试次数
        .autoInstall(true)                    // 下载完成自动安装
        .notificationEnabled(true)            // 通知栏显示进度
        .useForegroundService(true)           // 使用前台Service
        .autoResumeOnNetworkRecover(true)     // 网络恢复自动续传
        .md5CheckEnabled(true)                // 启用MD5校验
        .build();

UpgradeManager.getInstance()
        .init(context)
        .setConfig(config);
```

### 版本检测与升级

```java
// 构建版本信息（通常从服务器获取）
VersionInfo versionInfo = VersionInfo.builder()
        .versionName("2.0.0")
        .versionCode(200)
        .downloadUrl("https://example.com/app-release.apk")
        .md5("d41d8cd98f00b204e9800998ecf8427e")
        .fileSize(10485760)
        .forceUpdate(false)
        .updateLog("1. 新增功能\n2. 修复BUG")
        .build();

// 判断是否有新版本
if (VersionUtil.hasNewVersion(context, versionInfo)) {
    // 执行升级（自动下载 + 校验 + 安装）
    UpgradeManager.getInstance()
            .setListener(listener)
            .upgrade(versionInfo);
}
```

### 监听升级状态

```java
UpgradeManager.getInstance().setListener(new UpgradeListener() {
    @Override
    public void onStart() { }

    @Override
    public void onProgress(long current, long total, int percent) { }

    @Override
    public void onDownloadComplete(String apkPath) { }

    @Override
    public void onVerifySuccess(String apkPath) { }

    @Override
    public void onVerifyFailed(String apkPath, String expectedMd5, String actualMd5) { }

    @Override
    public void onRetry(int currentRetry, int maxRetry, String reason) { }

    @Override
    public void onFailed(String error) { }

    @Override
    public void onCancelled() { }

    @Override
    public void onInstallStart(String apkPath) { }
});
```

### 控制下载

```java
// 暂停
UpgradeManager.getInstance().pause();

// 恢复
UpgradeManager.getInstance().resume();

// 取消
UpgradeManager.getInstance().cancel();

// 清除缓存
UpgradeManager.getInstance().clearCache();
```

### 直接下载 APK

```java
// 不检查版本，直接下载
UpgradeManager.getInstance().downloadApk(url);

// 带 MD5 校验
UpgradeManager.getInstance().downloadApk(url, md5, fileSize);
```

## 类说明

| 类名 | 说明 |
|------|------|
| `UpgradeManager` | 统一入口，整合版本检测、下载、校验、安装完整流程 |
| `UpgradeConfig` | 升级配置（重试次数、超时、通知、自动安装等） |
| `VersionInfo` | 版本信息模型 |
| `VersionUtil` | 版本工具（获取版本号、版本比较、APK 有效性检查） |
| `DownloadEngine` | 下载引擎（断点续传、自动重试、网络监听） |
| `DownloadService` | 前台下载服务（保证后台下载不被杀） |
| `ApkVerifier` | APK 完整性校验（MD5 + 文件大小） |
| `ApkInstaller` | APK 安装工具（FileProvider、未知来源权限、静默安装） |
| `NetworkMonitor` | 网络状态监听（自动恢复下载） |
| `UpgradeListener` | 升级状态回调接口 |

## 权限说明

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

## 可靠性保障

1. **断点续传** — 下载中断（断网、进程被杀）后，重新启动会从上次断点继续
2. **自动重试** — 网络波动/服务器临时错误，自动重试最多5次（可配置）
3. **网络恢复自动续传** — 注册 NetworkCallback 监听，网络恢复后自动恢复下载
4. **前台 Service** — 使用 foregroundService 保证下载不被系统后台杀死
5. **双重校验** — MD5 + 文件大小确保 APK 完整无损，校验失败自动删除重下
6. **缓存复用** — 已下载且校验通过的 APK 直接复用，避免重复下载
