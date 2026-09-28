package com.hlk.hlkradartool.data;

import android.bluetooth.BluetoothDevice;
import android.util.Log;

import com.hlk.hlkradartool.activity.DemoApplication;
import com.hlk.hlkradartool.util.Utils;

import java.io.Serializable;
import java.util.HashMap;

public class SearchBLEDeviceInfo implements Serializable {

    private BluetoothDevice nowDevice;
    private int iRssi = -1;
    private String strHexBroadcastData = "";
    private HashMap<Integer,BroadcastDataInfo> bcHashMap = new HashMap<>();
    private String strCtrPwd = "";
    private String strVerInfo = "";
    private String strVerType = "";
    // 是否允许升级
    private boolean isAllowUpgrade = false;
    private String strMacByBroadcast = "";
    private String nowName = "";//自定义蓝牙名称
    private boolean isChecked;  // 标记是否被选中

    public SearchBLEDeviceInfo(BluetoothDevice bluetoothDevice,int iRssi,String strHexBroadcastData) {
        this.nowDevice = bluetoothDevice;
        this.iRssi = iRssi;
        this.strHexBroadcastData = strHexBroadcastData;
        initMap();
    }

    public boolean isAllowUpgrade() {
        return isAllowUpgrade;
    }

    public BluetoothDevice getNowDevice() {
        return nowDevice;
    }

    public String getStrVerInfo() {
        return strVerInfo;
    }

    public boolean isChecked() {
        return isChecked;
    }

    public void setChecked(boolean checked) {
        isChecked = checked;
    }

    public String getStrCtrPwd() {
        String strKey = nowDevice.getAddress()+"_CtrPwd";
        strCtrPwd = DemoApplication.getInstance().getValueBySharedPreferences(strKey);
        return strCtrPwd;
    }

    public void setStrCtrPwd(String strCtrPwd) {
        this.strCtrPwd = strCtrPwd;
        String strKey = nowDevice.getAddress()+"_CtrPwd";
        DemoApplication.getInstance().saveValueBySharedPreferences(strKey,strCtrPwd);
    }

    /** 根据广播包的内容，判断设备类型 */
    public BleDevType getNowBleDevType() {
        if (bcHashMap.get(0xFF) != null) {
            // 根据当前版本号进一步判断
            String[] strVerArray = strVerInfo.split("\\.");
            if (strVerArray.length == 3) {
                if (Integer.parseInt(strVerArray[2]) >= 22091516)
                    return BleDevType.Haved_Ver_Haved_CheckPwd_Haved_Upgrade_Haved_SPI;
                else
                    return BleDevType.Haved_Ver_No_CheckPwd_No_Upgrade_Haved_SPI;
            }
            else
                return BleDevType.Haved_Ver_No_CheckPwd_No_Upgrade_Haved_SPI;
        }
        else
            return BleDevType.No_Receive_Old;
    }

    public String getDevName() {
        return nowDevice.getName();
    }

    public String getMACAddress() {
        return nowDevice.getAddress();
    }

    public int getiRssi() {
        return iRssi;
    }

    public boolean updateData(int iNewRssi, String strNewBroadcastData) {
        boolean isUpdate = false;
        if (iRssi != iNewRssi) {
            iRssi = iNewRssi;
            isUpdate = true;
        }
        if (!strHexBroadcastData.equals(strNewBroadcastData)) {
            strHexBroadcastData = strNewBroadcastData;
            initMap();
            isUpdate = true;
        }

        return isUpdate;
    }

    private void initMap() {
        bcHashMap.clear();
        String strBcData = strHexBroadcastData;
        while (strBcData.length() > 6) {
            int iLength = Integer.parseInt(strBcData.substring(0,2),16);
            if (iLength > 0 && strBcData.length() >= (iLength+1)*2) {
                String strHexData = strBcData.substring(0,(iLength+1)*2);
                BroadcastDataInfo newBroadcastDataInfo = new BroadcastDataInfo(strHexData);
                int iType = newBroadcastDataInfo.getiType();
                // 杰理的广播包，要把0xFF改变一下，和自定义的广播包作区分
                if (newBroadcastDataInfo.isJieLiContent()) {
                    iType += 1;
                }
                bcHashMap.put(iType,newBroadcastDataInfo);
                strBcData = strBcData.substring((iLength+1)*2);
            }
            else {
                strBcData = strBcData.substring(2);
            }
        }

        // 版本号在自定义广播包中  fixme
        if (bcHashMap.get(0xFF) != null) {
            BroadcastDataInfo newBroadcastDataInfo = bcHashMap.get(0xFF);
            Log.e("SearchBLEDeviceInfo",nowDevice.getAddress()+",自定义广播包："+newBroadcastDataInfo.strValue);
            // 中间版本，广播包中只有版本号
            if (newBroadcastDataInfo.strValue.length() == 16) {
                String strBroVer = newBroadcastDataInfo.strValue;
                String strFirmwareHex = strBroVer.substring(0,4);
                String strFirstHex = strBroVer.substring(4,8);
                String strSecondHex1 = strBroVer.substring(8,12);
                String strSecondHex2 = strBroVer.substring(12);
                strFirmwareHex = Utils.reversalHex(strFirmwareHex);
                int iLeft = Integer.parseInt(strFirstHex.substring(0,2),16);
                String strMiddle = strFirstHex.substring(2,4);
                this.strVerType = strFirmwareHex;
                this.strVerInfo = iLeft+"."+strMiddle+"."+strSecondHex2+strSecondHex1;
            }
            else if (newBroadcastDataInfo.strValue.length() >= 30) {
                String strBroVer = newBroadcastDataInfo.strValue.substring(0,16);
                String strUpgradeState = newBroadcastDataInfo.strValue.substring(16,18);
                String strBroMac = newBroadcastDataInfo.strValue.substring(18,30);

                String strFirmwareHex = strBroVer.substring(0,4);
                String strFirstHex = strBroVer.substring(4,8);
                String strSecondHex = strBroVer.substring(8);
                strFirmwareHex = Utils.reversalHex(strFirmwareHex);
                strFirstHex = Utils.reversalHex(strFirstHex);
                strSecondHex = Utils.reversalHex(strSecondHex);
                int iLeft = Integer.parseInt(strFirstHex.substring(0,2),16);
                String strMiddle = strFirstHex.substring(2,4);
                this.strVerType = strFirmwareHex;
                this.strVerInfo = iLeft+"."+strMiddle+"."+strSecondHex;

                this.isAllowUpgrade = strUpgradeState.equals("01");
                this.strMacByBroadcast = strBroMac;



            }



        }


    }


    class BroadcastDataInfo {
        private String strHexData = "";
        private int iLength = 0;
        private int iType = 0x00;
        private String strValue = "";
        private final String strJieLiSign = "4A4C414953444B";// J L A I SDK

        public boolean isJieLiContent() {
            return (strValue.indexOf(strJieLiSign) >= 0);
        }

        public BroadcastDataInfo(String strHexData) {
            this.strHexData = strHexData;
            initData();
        }

        public void initData() {
            this.iLength = Integer.parseInt(strHexData.substring(0,2),16);
            this.iType = Integer.parseInt(strHexData.substring(2,4),16);
            this.strValue = strHexData.substring(4);

        }

        public String getStrHexData() {
            return strHexData;
        }

        public int getiLength() {
            return iLength;
        }

        public int getiType() {
            return iType;
        }

        public String getStrValue() {
            return strValue;
        }

    }

    public String getNowName() {
        return nowName;
    }

    public void setNowName(String nowName) {
        this.nowName = nowName;
    }
}
