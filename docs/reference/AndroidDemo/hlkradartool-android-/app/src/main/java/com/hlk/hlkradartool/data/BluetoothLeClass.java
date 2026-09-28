/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hlk.hlkradartool.data;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.util.Log;


import com.hlk.hlkradartool.util.Utils;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Service for managing connection and data communication with a GATT server
 * hosted on a given Bluetooth LE device.
 */
public class BluetoothLeClass {

	// 透传数据的相关服务与特征
	protected String strSerial_Server = "0000fff0-0000-1000-8000-00805f9b34fb";
	protected String strSerial_Read = "0000fff1-0000-1000-8000-00805f9b34fb";
	protected String strSerial_Write = "0000fff2-0000-1000-8000-00805f9b34fb";

	private final static String TAG = BluetoothLeClass.class.getSimpleName();
	/** 搜索BLE终端 */
	private BluetoothAdapter mBluetoothAdapter;
	public String strBleDevMac = "";
	private BluetoothDevice bluetoothDevice = null;
	private BluetoothGatt bluetoothGatt = null;
	private BluetoothGattCharacteristic bluetoothNotifyCharacter = null;
	private BluetoothGattCharacteristic bluetoothWriteCharacter = null;
	// 做数据缓存
	private String strReceiveDataBuffer = "";

	public int mtuSize  = 247;
	private OnBLEConnectStateListener onBLEConnectStateListener;
	// 发送帮助类
	public SendDataHelper sendDataHelper;

	public interface OnBLEConnectStateListener {
		public void onConnectting(String strMac);
		public void onConnected(String strMac);
		public void onDisconnect(String strMac,String strErrorInfo);
		public void onServiceDiscover(String strMac);
		public void OnCharacteristicRecv(String strMac,String strHexData);
		public void onCharacteristicRead(String strMac,BluetoothGattCharacteristic characteristic, int status);
		public void OnCharacteristicWrite(String strMac,BluetoothGattCharacteristic characteristic, int status,String strMsg);
		public void onChangeMTUListener(String strMac,Boolean isResult, int iMTU);
	}

	/** 返回发送异常的结果 */
	public void returnSendErrorResult(int iStatus,String strMsg) {
		onBLEConnectStateListener.OnCharacteristicWrite(strBleDevMac, bluetoothWriteCharacter, iStatus,strMsg);
	}

	private Context mContext;
	public BluetoothLeClass(Context c, OnBLEConnectStateListener onBLEConnectStateListener, BluetoothAdapter mBluetoothAdapter) {
		mContext = c;
		this.mBluetoothAdapter = mBluetoothAdapter;
		this.onBLEConnectStateListener = onBLEConnectStateListener;
	}

	public boolean requestMtu(int size) {
		if (bluetoothGatt != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			return bluetoothGatt.requestMtu(size);
		}
		return false;
	}

	private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
		/**
		 * 断开或连接 状态发生变化时调用
		 * */
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
			String strMac = gatt.getDevice().getAddress();
			Log.e(TAG, "BLEDev_MAC："+strMac+",onConnectionStateChange:status: "+status+",newState:"+newState);
			bluetoothGatt = gatt;
			if (newState == BluetoothProfile.STATE_CONNECTED) {
				if (onBLEConnectStateListener != null)
					onBLEConnectStateListener.onConnected(strMac);
				Log.e(TAG, "BLEDev_MAC："+strMac+",Connected to GATT server.");

			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				Log.e(TAG, "BLEDev_MAC："+strMac+",Disconnected from GATT server.");
				close();
			} else if (newState == BluetoothProfile.STATE_CONNECTING) {
				Log.e(TAG, "BLEDev_MAC："+strMac+",Connecting from GATT server....");
				if (onBLEConnectStateListener != null) {
					onBLEConnectStateListener.onConnectting(strMac);
				}
			}
		}

		/**
		 * 发现指定设备的服务信息（真正建立连接）
		 * */
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			Log.e(TAG, "BLEDev_MAC："+gatt.getDevice().getAddress()+",onServicesDiscovered:status: "+status);
			if (status == BluetoothGatt.GATT_SUCCESS) {
				boolean isFindReadWriteCharacter = getReadWriteCharacter(gatt);
				if (isFindReadWriteCharacter) {
					boolean isSubNotify = bindServerSubNotify();
					if (isSubNotify) {
						// 真正的连接成功啦！
						iAutoReConnectCount = 0;
						strReceiveDataBuffer = "";
						isManualDisc = false;
						sendDataHelper = new SendDataHelper(mContext,BluetoothLeClass.this);
						onBLEConnectStateListener.onServiceDiscover(strBleDevMac);
					}
					else {
						Log.e(TAG, strBleDevMac+",onServicesDiscovered subNotify false,BluetoothGatt disconnect....");
						bluetoothGatt.disconnect();
					}
				}
				else {
					Log.e(TAG, strBleDevMac+",onServicesDiscovered getReadWrite false,BluetoothGatt disconnect....");
					bluetoothGatt.disconnect();
				}
			}
			else {
				Log.e(TAG, strBleDevMac+",onServicesDiscovered false ,BluetoothGatt disconnect....");
				bluetoothGatt.disconnect();
			}
		}

		/**
		 * 读操作的回调
		 * */
		public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status) {
			Log.e(TAG, "BLEDev_MAC："+gatt.getDevice().getAddress()+",onCharacteristicRead:status:"+status);
			if (onBLEConnectStateListener != null)
				onBLEConnectStateListener.onCharacteristicRead(strBleDevMac,characteristic, status);
		}
		/**
		 * 写操作的回调
		 * */
	    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

	    	if(onBLEConnectStateListener != null){
	    		String strMsg = "";
	    		if (status != BluetoothGatt.GATT_SUCCESS) {
					Log.e(TAG, "BLEDev_MAC："+gatt.getDevice().getAddress()+",onCharacteristicWrite:status:"+status);
					strMsg = "Gatt,写入失败！";
				}
				onBLEConnectStateListener.OnCharacteristicWrite(strBleDevMac, characteristic, status,strMsg);
	    	}
	    }
		/**
		 * 接收到硬件返回的数据
		 * */
		public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
			byte[] resultByte = characteristic.getValue();
			if (resultByte.length > 0) {
				String strHexData = Utils.bytesToHexString(resultByte).toUpperCase();
				Log.e(TAG, "BLEDev_MAC："+gatt.getDevice().getAddress()+",onCharacteristicChanged,value:"+strHexData);
				onBLEConnectStateListener.OnCharacteristicRecv(strBleDevMac, strHexData);

			}

		}
		@Override
		public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
			super.onMtuChanged(gatt, mtu, status);
			Log.e(TAG, "BLEDev_MAC："+gatt.getDevice().getAddress()+",onMtuChanged mtu="+mtu + ",status:" + status);

			if (onBLEConnectStateListener != null) {
				onBLEConnectStateListener.onChangeMTUListener(gatt.getDevice().getAddress(),(status == BluetoothGatt.GATT_SUCCESS),mtuSize);
			}

		}
	};

	/** 获取对应的读写特征 */
	private boolean getReadWriteCharacter(BluetoothGatt gatt) {
		bluetoothNotifyCharacter = null;
		bluetoothWriteCharacter = null;
		boolean isRead = false;
		boolean isWrite = false;
		List<BluetoothGattService> serviceList = gatt.getServices();
		for (BluetoothGattService bluetoothGattService : serviceList) {
			if (bluetoothGattService.getUuid().toString().equalsIgnoreCase(strSerial_Server)) {
				List<BluetoothGattCharacteristic> bluetoothGattCharacteristicList = bluetoothGattService.getCharacteristics();
				for (BluetoothGattCharacteristic bluetoothGattCharacteristic : bluetoothGattCharacteristicList) {
					if (bluetoothGattCharacteristic.getUuid().toString().equalsIgnoreCase(strSerial_Read)) {
						isRead = true;
						bluetoothNotifyCharacter = bluetoothGattCharacteristic;
					}
					else if (bluetoothGattCharacteristic.getUuid().toString().equalsIgnoreCase(strSerial_Write)){
						isWrite = true;
						bluetoothWriteCharacter = bluetoothGattCharacteristic;
					}
				}
				break;
			}
		}
		return isRead && isWrite;
	}

	/** 订阅指定设备的广播UUID */
	private boolean bindServerSubNotify() {
		boolean isSubNotify = false;
		isSubNotify = bluetoothGatt.setCharacteristicNotification(bluetoothNotifyCharacter, true);
		Log.e(TAG, "BLEDev_MAC："+strBleDevMac+",bindServerSubNotify:"+bluetoothNotifyCharacter.getUuid()+",state:"+isSubNotify);
		if (isSubNotify) {
			List<BluetoothGattDescriptor> bluetoothGattDescriptorList = bluetoothNotifyCharacter.getDescriptors();
			for (BluetoothGattDescriptor descriptor : bluetoothGattDescriptorList) {
				// 读写开关操作，writeDescriptor 否则可能读取不到数据。
				boolean isB1 = descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
				if (isB1) {
					boolean isB2 = bluetoothGatt.writeDescriptor(descriptor);
					Log.e(TAG, "BLEDev_MAC："+strBleDevMac+",writeDescriptor: 监听收数据!");
				}
			}
		}
		return isSubNotify;
	}


	/**
	 * Connects to the GATT server hosted on the Bluetooth LE device.
	 *
	 *            The device address of the destination device.
	 * 
	 * @return Return true if the connection is initiated successfully. The
	 *         connection result is reported asynchronously through the
	 *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
	 *         callback.
	 */ public int iAutoReConnectCount = 0; // 当前重连的次数
	public boolean connect(BluetoothDevice device) {
		++iAutoReConnectCount;
		this.strBleDevMac = device.getAddress();
		this.bluetoothDevice = device;
		isManualDisc = false;
		bluetoothGatt = bluetoothDevice.connectGatt(mContext,false, mGattCallback);
		return true;
	}

	/**
	 * Disconnects an existing connection or cancel a pending connection. The
	 * disconnection result is reported asynchronously through the
	 * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
	 * callback.
	 */
	private boolean isManualDisc = false;
	public void manualDisconnect() {
		if (bluetoothGatt != null) {
			isManualDisc = true;
			sendDataHelper = null;
			Log.e(TAG, strBleDevMac+",BluetoothGatt disconnect....");
			bluetoothGatt.disconnect();
		}
	}

	/**
	 * After using a given BLE device, the app must call this method to ensure
	 * resources are released properly.
	 */
	private void close() {
		if (bluetoothGatt != null) {
			bluetoothGatt.close();
			bluetoothGatt = null;
			bluetoothNotifyCharacter = null;
			bluetoothWriteCharacter = null;
			Log.e(TAG, "BLEDev_MAC："+strBleDevMac+",connect closed!");
			// 手动断开的，则直接跑到上层
			if (isManualDisc) {
				if (onBLEConnectStateListener != null)
					onBLEConnectStateListener.onDisconnect(strBleDevMac,"");
			}
			// 非手动断开，则需要判断是否需要重连
			else {
				// 超过重连次数，则自动抛到上层
				if(iAutoReConnectCount > 1) {
					onBLEConnectStateListener.onDisconnect(strBleDevMac,"reconnect over count 2 !");
				}
				else {
					connect(bluetoothDevice);
				}
			}
		}
	}



	private int iGetServiceCount = 0;
	/** 获取服务！！！重中之重 */
	public void startGatService() {
		iGetServiceCount = 0;
		getServiceByGatt();
	}

	private void getServiceByGatt() {
		boolean isDiscover = bluetoothGatt.discoverServices();
		Log.e(TAG, "BLEDev_MAC："+strBleDevMac+",Attempting to start service discovery:" + isDiscover);
		if (!isDiscover) {
			new Timer().schedule(new TimerTask() {
				@Override
				public void run() {
					++iGetServiceCount;
					if (iGetServiceCount > 5) {
						this.cancel();
						Log.e(TAG, strBleDevMac+",getServiceByGatt over count,BluetoothGatt disconnect....");
						bluetoothGatt.disconnect();
					}
					else {
						getServiceByGatt();
					}
				}
			},500);
		}
	}

	/** 发送数据 */
	public void startSendData(String strSendHexData) {
		Log.e(TAG, "BLEDev_MAC："+strBleDevMac+",writeCharacteristic,value:"+strSendHexData);
		bluetoothWriteCharacter.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
		bluetoothWriteCharacter.setValue(Utils.hexStringToBytes(strSendHexData));
		writeCharacteristic(bluetoothWriteCharacter);
	}


	/**
	 * Request a read on a given {@code BluetoothGattCharacteristic}. The read
	 * result is reported asynchronously through the
	 * {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
	 * callback.
	 *
	 * @param characteristic
	 *            The characteristic to read from.
	 */
	public Boolean readCharacteristic(String strMac,BluetoothGattCharacteristic characteristic) {
		if (mBluetoothAdapter == null || bluetoothGatt == null) {
			Log.e(TAG, "BLEDev_MAC："+strMac+",BluetoothAdapter or BluetoothGatt not initialized");
			return false;
		}
		return bluetoothGatt.readCharacteristic(characteristic);
	}

	/**
	 * Enables or disables notification on a give characteristic.
	 * 
	 * @param characteristic
	 *            Characteristic to act on.
	 * @param enabled
	 *            If true, enable notification. False otherwise.
	 */
	public Boolean setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {
		if (mBluetoothAdapter == null || bluetoothGatt == null) {
			Log.e(TAG, "BLEDev_MAC："+strBleDevMac+",BluetoothAdapter or bluetoothGattHashMap not initialized");
			return false;
		}
		boolean isNotification = bluetoothGatt.setCharacteristicNotification(characteristic, enabled);
		return isNotification;
	}

	public boolean writeCharacteristic(BluetoothGattCharacteristic characteristic) {
		boolean isResult = bluetoothGatt.writeCharacteristic(characteristic);

		return isResult;
	}

	public boolean writeDescriptor(BluetoothGattDescriptor gattDescriptor) {
		return bluetoothGatt.writeDescriptor(gattDescriptor);
	}

}
