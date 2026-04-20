package com.dawn.lib_upgrade;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.util.Log;

/**
 * 网络状态监听器
 * <p>
 * 监听网络变化，网络恢复时自动触发下载恢复。
 */
public class NetworkMonitor {

    private static final String TAG = "NetworkMonitor";

    private final Context mContext;
    private ConnectivityManager.NetworkCallback mNetworkCallback;
    private OnNetworkChangeListener mListener;
    private boolean mRegistered = false;

    public interface OnNetworkChangeListener {
        void onNetworkAvailable();
        void onNetworkLost();
    }

    public NetworkMonitor(Context context) {
        this.mContext = context.getApplicationContext();
    }

    public void setListener(OnNetworkChangeListener listener) {
        this.mListener = listener;
    }

    /**
     * 注册网络监听
     */
    public void register() {
        if (mRegistered) return;

        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;

        mNetworkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                Log.d(TAG, "Network available");
                if (mListener != null) mListener.onNetworkAvailable();
            }

            @Override
            public void onLost(Network network) {
                Log.d(TAG, "Network lost");
                if (mListener != null) mListener.onNetworkLost();
            }
        };

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        cm.registerNetworkCallback(request, mNetworkCallback);
        mRegistered = true;
    }

    /**
     * 注销网络监听
     */
    public void unregister() {
        if (!mRegistered) return;
        try {
            ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && mNetworkCallback != null) {
                cm.unregisterNetworkCallback(mNetworkCallback);
            }
        } catch (Exception e) {
            Log.e(TAG, "unregister error", e);
        }
        mRegistered = false;
    }

    /**
     * 当前是否有网络连接
     */
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (caps == null) return false;

        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    /**
     * 是否是 WiFi 网络
     */
    public static boolean isWifi(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }
}
