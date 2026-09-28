package com.hlk.hlkradartool.activity;

import android.Manifest;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;


import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.hlk.hlkradartool.R;
import com.hlk.hlkradartool.data.BluetoothLeClass;
import com.hlk.hlkradartool.data.DataAnalysisHelper;
import com.hlk.hlkradartool.data.ReceiveInfo;
import com.hlk.hlkradartool.data.SearchBLEDeviceInfo;
import com.hlk.hlkradartool.data.SendDataHelper;
import com.hlk.hlkradartool.util.BaseVolume;
import com.hlk.hlkradartool.util.Utils;
import com.inuker.bluetooth.library.Constants;
import com.smartIPandeInfo.data.MessageInfo;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class DemoApplication extends Application implements BluetoothLeClass.OnBLEConnectStateListener {
	/**
	 * Application实例
	 */
	private static DemoApplication demoApplication;
	private static String TAG = "DemoApplication";
	public String strNowDevMac = "";

	public SearchBLEDeviceInfo nowSelectDevice;

	@Override
	public void onCreate() {
		super.onCreate();

		if (null == demoApplication) {
			demoApplication = this;
		}
		Constants.PACKAGE_NAME = getPackageName();
		pref = getSharedPreferences("SharedPreferencesInfo", 0);
	}

	/**
	 * 获取Application实例
	 * @return
	 */
	public static DemoApplication getInstance() {
		return demoApplication;
	}

	private SharedPreferences pref = null;

	public void saveValueBySharedPreferences(String strKey, String strValue) {
		SharedPreferences.Editor editor = pref.edit();
		editor.putString(strKey, strValue);
		editor.commit();
	}

	public String getValueBySharedPreferences(String strKey) {
		String string = pref.getString(strKey, "");
		return string;
	}

	public void saveBooleanBySharedPreferences(String strKey, Boolean blValue) {
		SharedPreferences.Editor editor = pref.edit();
		editor.putBoolean(strKey, blValue);
		editor.commit();
	}

	public Boolean getBooleanBySharedPreferences(String strKey) {
		return pref.getBoolean(strKey, false);
	}

	private BluetoothAdapter bluetoothAdapter;
	private HashMap<String, BluetoothLeClass> bluetoothLeClassHashMap = new HashMap<>();

	/** 初始化BLE的工具类 */
	public void initBLEClass(BluetoothAdapter bluetoothAdapter) {
		this.bluetoothAdapter = bluetoothAdapter;
		bluetoothLeClassHashMap.clear();
	}

	public BluetoothLeClass getBleDevLeClass(String strMac) {
		return bluetoothLeClassHashMap.get(strMac);
	}

	public void clearAllBLEDev() {
		for (String key : bluetoothLeClassHashMap.keySet()) {
			bluetoothLeClassHashMap.get(key).manualDisconnect();
			bluetoothLeClassHashMap.remove(key);
		}
	}

	/** GATT 连接中 */
	public void onConnectting(String strMac) {
		MessageInfo msg = new MessageInfo(MessageInfo.i_GATT_CONNECT_STATE, strMac);
		msg.setIParam(MessageInfo.i_GATT_CONNECT_STATE_CONNECTING);
		EventBus.getDefault().post(msg);
	}

	/** GATT 连接成功 */
	public void onConnected(String strMac) {
		// 连接后先不抛上层，自动查询并绑定服务
		bluetoothLeClassHashMap.get(strMac).startGatService();
	}

	/** GATT 断开连接 */
	public void onDisconnect(String strMac, String strErrorInfo) {
		bluetoothLeClassHashMap.remove(strMac);
		MessageInfo msg = new MessageInfo(MessageInfo.i_GATT_CONNECT_STATE, strMac);
		msg.setIParam(MessageInfo.i_GATT_CONNECT_STATE_DISCONNECT);
		msg.setStrParam(strErrorInfo);
		EventBus.getDefault().post(msg);
	}

	/** GATT 搜索到终端服务 */
	public void onServiceDiscover(String strMac) {
//		MessageInfo msg = new MessageInfo(MessageInfo.i_GATT_SERVICE_DISCOVER,gatt);
		// 搜索到服务后，才意味着真正建立了连接
		MessageInfo msg = new MessageInfo(MessageInfo.i_GATT_CONNECT_STATE, strMac);
		msg.setIParam(MessageInfo.i_GATT_CONNECT_STATE_CONNECTED);
		EventBus.getDefault().post(msg);

	}

	/** GATT 接收硬件端发来的数据 */
	public void OnCharacteristicRecv(String strMac, String strHexData) {
		bluetoothLeClassHashMap.get(strMac).sendDataHelper.StopSend();
		MessageInfo msg = new MessageInfo(MessageInfo.i_GATT_RECEIVE_DATA, strMac);
		msg.setStrParam(strHexData);
		EventBus.getDefault().post(msg);

		//接受到数据后开始解析
//		parseByData(strMac, strHexData);
	}

	/** GATT 读操作的回调 */
	public void onCharacteristicRead(String strMac, BluetoothGattCharacteristic characteristic, int status) {
		MessageInfo msg = new MessageInfo(MessageInfo.i_GATT_READ_RESULT, strMac);
		EventBus.getDefault().post(msg);
	}

	/** GATT 写操作的回调 */
	public void OnCharacteristicWrite(String strMac, BluetoothGattCharacteristic characteristic, int status, String strMsg) {
		MessageInfo msg = new MessageInfo(MessageInfo.i_GATT_WRITE_RESULT, strMac);
		msg.setBlParam((status == BluetoothGatt.GATT_SUCCESS));
		msg.setStrParam(strMsg);
		EventBus.getDefault().post(msg);
	}

	/** GATT MTU改变监听 */
	public void onChangeMTUListener(String strMac, Boolean isResult, int iMTU) {
		MessageInfo msg = new MessageInfo(MessageInfo.i_GATT_MTU_CHANGE, strMac);
		msg.setBlParam(isResult);
		msg.setIParam(iMTU);
		EventBus.getDefault().post(msg);
	}

	/** 通过蓝牙发送数据 */
	public void startSendDataByBLE(String strMac, String strSendHexData, boolean isTimeOut) {
		if (bluetoothLeClassHashMap.get(strMac) != null && bluetoothLeClassHashMap.get(strMac).sendDataHelper != null) {
			bluetoothLeClassHashMap.get(strMac).sendDataHelper.StartSendData(strSendHexData, isTimeOut);
		} else {
			MessageInfo msg = new MessageInfo(MessageInfo.i_GATT_WRITE_RESULT, strMac);
			msg.setBlParam(false);
			msg.setStrParam(getString(R.string.shebei_wei_lianjie));
			EventBus.getDefault().post(msg);
		}
	}

	private boolean isScanning = false;

	public boolean isScanning() {
		return isScanning;
	}

	// 权限检查,当系统版本大于等于安卓12的时候
	public boolean checkBluetoothConnectPermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			if (ActivityCompat.checkSelfPermission(DemoApplication.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
				Toast.makeText(DemoApplication.this, getString(R.string.queshao_quanxian3), Toast.LENGTH_LONG).show();
				return false;
			}else {
				return true;
			}
		}else {
			return true;
		}
	}

	/**
	 * 开始搜索蓝牙设备
	 * 这里加个倒计时，原因是停止扫描后立马开始扫描，频繁的进行此操作很容易导致后续开启扫描扫不到设备
	 * chatgpt的反馈说频繁的进行此操作很可能让蓝牙功能处理不过来或者蓝牙模块过载，最好延时几秒
	 */
	Timer timer = new Timer();

	public void startScanBLEDev(BluetoothAdapter.LeScanCallback leScanCallback) {
		if (bluetoothAdapter != null) {
			isScanning = true;
//			if (!checkBluetoothConnectPermission())return;
			Log.e(TAG, "startScanBLEDev: 停止扫描后倒计时3秒后开始搜索...");
			if (!bluetoothAdapter.isEnabled()){
				Toast.makeText(DemoApplication.this, getString(R.string.dakai_lanya_gongneng), Toast.LENGTH_LONG).show();
				return;
			}
			bleDevList.clear();
			bluetoothAdapter.stopLeScan(leScanCallback);
			// 倒计时
			timer = new Timer();
			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					bluetoothAdapter.startLeScan(leScanCallback);
					this.cancel();
				}
			}, 3000);
		} else {
			Toast.makeText(this, getString(R.string.bu_zhichi_lanya), Toast.LENGTH_LONG).show();
		}
	}

	/** 停止搜索 */
	public void stopScanBLEDev() {
		timer.cancel();
		if (bluetoothAdapter != null) {
			isScanning = false;
//			if (!checkBluetoothConnectPermission())return;

			if (!bluetoothAdapter.isEnabled()){
				Toast.makeText(DemoApplication.this, getString(R.string.dakai_lanya_gongneng), Toast.LENGTH_LONG).show();
				return;
			}
			bluetoothAdapter.stopLeScan(leScanCallback);
			Log.e(TAG, "stopScanBLEDev: 停止搜索...");
		}
	}

	ArrayList<String> bleDevList = new ArrayList<>();
	private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
		@Override
		public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
//			if (!checkBluetoothConnectPermission())return;
			if (device.getName() == null || device.getName().length() == 0)
				return;
//			Log.e(TAG, "leScanCallback: "+device.getName()+",type:"+device.getType()+",rssi:"+rssi);
			MessageInfo msg = new MessageInfo(MessageInfo.i_GATT_SCAN_BLE_DEV,device.getAddress());
			msg.setObjects(device);
			msg.setIParam(rssi);
			msg.setByteArray(scanRecord);
			EventBus.getDefault().post(msg);
		}
	};
}
