package com.apposite.wifip2p;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pManager;

public class WifiP2pBroadcastReceiver extends BroadcastReceiver {

    WifiP2pManager wifi;
    WifiP2pManager.Channel channel;
    MainActivity activity;

    public WifiP2pBroadcastReceiver(WifiP2pManager wifi, WifiP2pManager.Channel channel, MainActivity activity) {
        super();
        this.wifi = wifi;
        this.channel = channel;
        this.activity = activity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)){
        }
        else if(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)){
            if (wifi != null) {
                wifi.requestPeers(channel, activity);
            }
        }
        else if(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)){
            if (wifi == null) {
                return;
            }
            NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

            if (networkInfo.isConnected())
                wifi.requestConnectionInfo(channel, activity);
            else
                activity.reset();
        }
        else if(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)){
        }
    }
}
