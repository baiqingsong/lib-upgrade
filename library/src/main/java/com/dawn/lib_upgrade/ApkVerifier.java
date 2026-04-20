package com.dawn.lib_upgrade;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;

/**
 * APK 完整性校验工具
 */
public class ApkVerifier {

    private static final String TAG = "ApkVerifier";

    private ApkVerifier() {
    }

    /**
     * 校验 APK 文件完整性
     *
     * @param apkPath    APK 文件路径
     * @param expectedMd5 期望的 MD5 值（可为 null 跳过）
     * @param expectedSize 期望的文件大小（<=0 跳过）
     * @return 校验结果
     */
    public static VerifyResult verify(String apkPath, String expectedMd5, long expectedSize) {
        File file = new File(apkPath);
        if (!file.exists()) {
            return VerifyResult.fail("文件不存在: " + apkPath);
        }

        if (file.length() == 0) {
            return VerifyResult.fail("文件大小为0");
        }

        // 大小校验
        if (expectedSize > 0 && file.length() != expectedSize) {
            return VerifyResult.fail("文件大小不匹配: 期望 " + expectedSize
                    + ", 实际 " + file.length());
        }

        // MD5 校验
        if (expectedMd5 != null && !expectedMd5.isEmpty()) {
            String actualMd5 = calculateMd5(apkPath);
            if (!expectedMd5.equalsIgnoreCase(actualMd5)) {
                return VerifyResult.failMd5(expectedMd5, actualMd5);
            }
        }

        return VerifyResult.success();
    }

    /**
     * 计算文件 MD5
     */
    public static String calculateMd5(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) return "";
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                md.update(buffer, 0, len);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "calculateMd5 error", e);
            return "";
        }
    }

    /**
     * 校验结果
     */
    public static class VerifyResult {
        private final boolean success;
        private final String errorMsg;
        private final String expectedMd5;
        private final String actualMd5;

        private VerifyResult(boolean success, String errorMsg, String expectedMd5, String actualMd5) {
            this.success = success;
            this.errorMsg = errorMsg;
            this.expectedMd5 = expectedMd5;
            this.actualMd5 = actualMd5;
        }

        static VerifyResult success() {
            return new VerifyResult(true, null, null, null);
        }

        static VerifyResult fail(String msg) {
            return new VerifyResult(false, msg, null, null);
        }

        static VerifyResult failMd5(String expected, String actual) {
            return new VerifyResult(false, "MD5不匹配: 期望 " + expected + ", 实际 " + actual,
                    expected, actual);
        }

        public boolean isSuccess() { return success; }
        public String getErrorMsg() { return errorMsg; }
        public String getExpectedMd5() { return expectedMd5; }
        public String getActualMd5() { return actualMd5; }
    }
}
