package com.hlk.hlkradartool.permissions;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.util.Timer;
import java.util.TimerTask;

import static android.content.Context.BLUETOOTH_SERVICE;
import static android.content.Context.RECEIVER_NOT_EXPORTED;
import static android.content.Context.WIFI_SERVICE;

public class GPSWIFIBLEListening implements WifiSwitch_Interface {

    private String TAG = "GPSWIFIBLEListening";
    private Context context;


    public WifiSwitch_Presenter wifiSwitch_presenter;
    public WifiManager wifiManager;
    public BluetoothAdapter mBluetoothAdapter;
//    public LocationManager mLocationManager;
    public static final String WIFI_STATE = "WIFI_STATE";
    public  static final  String BLUETOOTH = "BLUETOOTH";
    public  static final  String GPS_LOCATION = "GPS_LOCATION";
    private boolean isLastGPSEnable = false;

    public boolean isLastGPSEnable() {
        return isLastGPSEnable;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public GPSWIFIBLEListening(Context context){
        this.context = context;

        wifiSwitch_presenter = new WifiSwitch_Presenter(context, this);
        wifiManager = (WifiManager)context.getSystemService(WIFI_SERVICE);

        BluetoothManager bluetoothManager = (BluetoothManager)context.getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

//        mLocationManager = (LocationManager)context.getSystemService(LOCATION_SERVICE);
//        context.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(Settings.System.LOCATION_PROVIDERS_ALLOWED), false,mGpsMonitor);
        startGPSEnable(true);

        // 监视蓝牙关闭和打开的状态的广播
        IntentFilter intentBluetooth = new IntentFilter();
        intentBluetooth.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        context.registerReceiver(bluetoothReceiver, intentBluetooth, RECEIVER_NOT_EXPORTED);
    }

    private Timer timerCheckGPSEnable = new Timer();
    private void startGPSEnable(boolean isStart) {
        if (timerCheckGPSEnable != null) {
            timerCheckGPSEnable.cancel();
            timerCheckGPSEnable = null;
        }
        if (isStart) {
            timerCheckGPSEnable = new Timer();
            timerCheckGPSEnable.schedule(new TimerTask() {
                @Override
                public void run() {
                    boolean isGPSEnable = gpsIsOpen(context);
                    if (isLastGPSEnable != isGPSEnable) {
                        isLastGPSEnable = isGPSEnable;
                        Intent WIFIState = new Intent(TAG);
                        WIFIState.putExtra("Listening", GPS_LOCATION);
                        WIFIState.putExtra("GPS_LOCATION_OPEN",isLastGPSEnable);
                        WIFIState.setPackage(context.getPackageName());
                        context.sendBroadcast(WIFIState);
                    }
                }
            },0,500);

        }


    }

    public boolean gpsIsOpen(final Context context) {
        LocationManager locationManager
                = (LocationManager) context.getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        // 通过GPS卫星定位，定位级别可以精确到街（通过24颗卫星定位，在室外和空旷的地方定位准确、速度快）
        boolean gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        // 通过WLAN或移动网络(3G/2G)确定的位置（也称作AGPS，辅助GPS定位。主要用于在室内或遮盖物（建筑群或茂密的深林等）密集的地方定位）
        boolean network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (gps || network) {
            return true;
        }

        return false;
    }

    @Override
    public void wifiSwitchState(int state) {
        if(state == WifiSwitch_Interface.WIFI_STATE_DISABLED){
            Intent WIFIState = new Intent(TAG);
            WIFIState.putExtra("Listening", WIFI_STATE);
            WIFIState.putExtra("WIFI_STATE_OPEN",false);
            WIFIState.setPackage(context.getPackageName());
            context.sendBroadcast(WIFIState);
        }
        if(state == WifiSwitch_Interface.WIFI_STATE_ENABLED){
            Intent WIFIState = new Intent(TAG);
            WIFIState.putExtra("Listening", WIFI_STATE);
            WIFIState.putExtra("WIFI_STATE_OPEN",true);
            WIFIState.setPackage(context.getPackageName());
            context.sendBroadcast(WIFIState);
        }

    }

    private BroadcastReceiver bluetoothReceiver =new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e(TAG, "onReceive: 蓝牙监听到的蓝牙状态："+ mBluetoothAdapter.isEnabled());
            Intent bluetooth = new Intent(TAG);
            bluetooth.putExtra("Listening", BLUETOOTH);
            bluetooth.putExtra("BLUETOOTH_OPEN",mBluetoothAdapter.isEnabled());
            bluetooth.setPackage(context.getPackageName());
            context.sendBroadcast(bluetooth);
        }
    };

//    private ContentObserver mGpsMonitor = new ContentObserver(null) {
//        @Override
//        public void onChange(boolean selfChange) {
//            super.onChange(selfChange);
//            Intent WIFIState = new Intent(TAG);
//            WIFIState.putExtra("Listening", GPS_LOCATION);
//            WIFIState.putExtra("GPS_LOCATION_OPEN",mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER));
//            context.sendBroadcast(WIFIState);
//        }
//    };



    public void finishWIFI(){
        if (wifiSwitch_presenter != null){
            wifiSwitch_presenter.onDestroy();
        }
        context.unregisterReceiver(bluetoothReceiver);
//        context.getContentResolver().unregisterContentObserver(mGpsMonitor);
        startGPSEnable(false);
    }

}
