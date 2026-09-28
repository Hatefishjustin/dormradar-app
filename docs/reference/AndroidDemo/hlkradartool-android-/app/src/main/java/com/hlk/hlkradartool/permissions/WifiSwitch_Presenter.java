package com.hlk.hlkradartool.permissions;

import static android.content.Context.RECEIVER_NOT_EXPORTED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;

public class WifiSwitch_Presenter {
    private Context mContext ;
    private Receiver receiver ;
    private WifiSwitch_Interface mInterface ;


    public WifiSwitch_Presenter( Context context , WifiSwitch_Interface mInterface ){
        this.mContext = context ;
        this.mInterface = mInterface ;

        observeWifiSwitch();
    }

    private void observeWifiSwitch(){
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        receiver = new Receiver() ;
        mContext.registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED);
    }

    /**
     * 释放资源
     */
    public void onDestroy(){
        if ( receiver != null ){
            mContext.unregisterReceiver( receiver );
        }
        if (mContext!=null){
            mContext = null;
        }
    }

    class Receiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0);
            switch (wifiState) {
                case WifiManager.WIFI_STATE_DISABLED:
                    if (mInterface != null){
                        mInterface.wifiSwitchState(WifiSwitch_Interface.WIFI_STATE_DISABLED);
                    }
                    break;
                case WifiManager.WIFI_STATE_DISABLING:
                    if (mInterface != null){
                        mInterface.wifiSwitchState(WifiSwitch_Interface.WIFI_STATE_DISABLING);
                    }
                    break;
                case WifiManager.WIFI_STATE_ENABLED:
                    if (mInterface != null){
                        mInterface.wifiSwitchState(WifiSwitch_Interface.WIFI_STATE_ENABLED);
                    }
                    break;
                case WifiManager.WIFI_STATE_ENABLING:
                    if ( mInterface != null ) {
                        mInterface.wifiSwitchState(WifiSwitch_Interface.WIFI_STATE_ENABLING);
                    }
                    break;
                case WifiManager.WIFI_STATE_UNKNOWN:
                    if ( mInterface != null ){
                        mInterface.wifiSwitchState( WifiSwitch_Interface.WIFI_STATE_UNKNOWN );
                    }
                    break;
            }
        }
    }
}