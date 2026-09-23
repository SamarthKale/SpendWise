package com.mj.spendwise.backend;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * Watches the device network with ConnectivityManager.NetworkCallback and exposes it as LiveData:
 * isOnline (true/false) and networkType ("Wi-Fi", "Mobile data", "Offline").
 * Call stop() when done (the ViewModel does this in onCleared) so the callback isn't leaked.
 */
public class ConnectivityMonitor {
    private static final String TAG = "ConnectivityMonitor";

    private final ConnectivityManager manager;
    private final MutableLiveData<Boolean> online = new MutableLiveData<>(false);
    private final MutableLiveData<String> networkType = new MutableLiveData<>("Offline");
    private boolean registered = false;

    // Callbacks arrive on a background thread, so we use postValue (not setValue).
    private final ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
            publish(caps);
        }

        @Override
        public void onLost(Network network) {
            publish(null); // the default network went away, e.g. airplane mode
        }
    };

    public ConnectivityMonitor(Context context) {
        manager = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return;
        try {
            // Initial state, because the callback only fires on changes (and slightly later).
            Network active = manager.getActiveNetwork();
            publish(active == null ? null : manager.getNetworkCapabilities(active));
            manager.registerDefaultNetworkCallback(callback);
            registered = true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not register network callback", e);
        }
    }

    public LiveData<Boolean> isOnline() { return online; }

    public LiveData<String> getNetworkType() { return networkType; }

    public void stop() {
        if (manager != null && registered) {
            try {
                manager.unregisterNetworkCallback(callback);
            } catch (RuntimeException e) {
                Log.w(TAG, "unregister failed", e);
            }
            registered = false;
        }
    }

    private void publish(NetworkCapabilities caps) {
        boolean hasInternet = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        String type = "Offline";
        if (hasInternet) {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) type = "Wi-Fi";
            else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) type = "Mobile data";
            else type = "Online";
        }
        online.postValue(hasInternet);
        networkType.postValue(type);
    }
}
