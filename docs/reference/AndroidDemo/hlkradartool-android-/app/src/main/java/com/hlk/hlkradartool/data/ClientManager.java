package com.hlk.hlkradartool.data;


import com.hlk.hlkradartool.activity.DemoApplication;
import com.inuker.bluetooth.library.BluetoothClient;

/**
 * Created by dingjikerbo on 2016/8/27.
 */
public class ClientManager {

    private static BluetoothClient mClient;

    public static BluetoothClient getClient() {
        if (mClient == null) {
            synchronized (ClientManager.class) {
                if (mClient == null) {
                    mClient = new BluetoothClient(DemoApplication.getInstance());
                }
            }
        }
        return mClient;
    }
}
