package com.hlk.hlkradartool.permissions;

public interface WifiSwitch_Interface {

    int WIFI_STATE_ENABLING = 0 ;
    int WIFI_STATE_ENABLED = 1 ;
    int WIFI_STATE_DISABLING = 2 ;
    int WIFI_STATE_DISABLED = 3 ;
    int WIFI_STATE_UNKNOWN = 4 ;

    void wifiSwitchState( int state );
}
