package com.hlk.hlkradartool.activity;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hlk.hlkradartool.R;
import com.hlk.hlkradartool.data.BleDevType;
import com.hlk.hlkradartool.data.ClientManager;
import com.hlk.hlkradartool.data.CreateControlData;
import com.hlk.hlkradartool.data.DataAnalysisHelper;
import com.hlk.hlkradartool.data.DataBean;
import com.hlk.hlkradartool.data.ReceiveInfo;
import com.hlk.hlkradartool.data.SearchBLEDeviceInfo;
import com.hlk.hlkradartool.permissions.GPSWIFIBLEListening;
import com.hlk.hlkradartool.util.BaseVolume;
import com.hlk.hlkradartool.util.PermissionsChecker;
import com.hlk.hlkradartool.util.Utils;
import com.hlk.hlkradartool.view.AreaAddWindowAddDevice;
import com.hlk.hlkradartool.view.AreaAddWindowHint;
import com.hlk.hlkradartool.view.AreaGetPermissionWindow;
import com.hlk.hlkradartool.view.CheckCtrPwdWindowDialog;
import com.inuker.bluetooth.library.Code;
import com.inuker.bluetooth.library.Constants;
import com.inuker.bluetooth.library.connect.listener.BleConnectStatusListener;
import com.inuker.bluetooth.library.connect.options.BleConnectOptions;
import com.inuker.bluetooth.library.connect.response.BleConnectResponse;
import com.inuker.bluetooth.library.connect.response.BleMtuResponse;
import com.inuker.bluetooth.library.connect.response.BleNotifyResponse;
import com.inuker.bluetooth.library.connect.response.BleWriteResponse;
import com.inuker.bluetooth.library.model.BleGattCharacter;
import com.inuker.bluetooth.library.model.BleGattProfile;
import com.inuker.bluetooth.library.model.BleGattService;
import com.inuker.bluetooth.library.utils.BluetoothLog;
import com.inuker.bluetooth.library.utils.ByteUtils;
import com.smart.hlk_b50.adapter.BLEDeviceListAdapter;
import com.smartIPandeInfo.data.MessageInfo;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.inuker.bluetooth.library.Constants.REQUEST_SUCCESS;
public class BLEListActivity extends BaseActivity {

    private String TAG ="BLEListActivity";

    private RecyclerView recyclerView;


    private BluetoothAdapter mBluetoothAdapter;
    private BLEDeviceListAdapter bleDeviceListAdapter;
    private ArrayList<SearchBLEDeviceInfo> bleDevList = new ArrayList<>();
    private static BLEListActivity bleListActivity;

    private GPSWIFIBLEListening gpswifibleListening;
    private AreaAddWindowAddDevice bleEnableOpenHint;
    private AreaAddWindowAddDevice gpsEnableOpenHint;
    private TextView tvVersion,tvTitle = null;

    private boolean isNeedStart = false;

    // �?�?的全部权�?
    @SuppressLint("InlinedApi")
    static String[] PERMISSIONS ;

    private PermissionsChecker mPermissionsChecker; // 权限�?测器



    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blelist);
        bleListActivity = this;
        mPermissionsChecker = new PermissionsChecker(this);

        init();
        EventBus.getDefault().register(this);

        checkCtrPwdWindowDialog = new CheckCtrPwdWindowDialog(mContext,R.style.dialog_style,new CheckCtrPwdWindowDialog.PeriodListener(){
            @Override
            public void refreshListener(String string) {
                checkCtrPwd(string);
            }
            @Override
            public void cancelListener() {
                disconnectBluetooth(DemoApplication.getInstance().nowSelectDevice.getMACAddress());
                checkCtrPwdWindowDialog.dismiss();
            }
        });

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            PERMISSIONS = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
            };
        }else {
            PERMISSIONS = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.BLUETOOTH_SCAN,//安卓12，sdk31新出的权限，用于使用蓝牙扫描附件其他的蓝牙设备
                    Manifest.permission.BLUETOOTH_CONNECT,//安卓12，sdk31新出的权限，用于连接之前已经配对过的蓝牙设备
            };
        }
    }

    private void init(){
        tvTitle = findViewById(R.id.tvTitle);
        tvVersion = findViewById(R.id.tvVersion);
        tvVersion.setText("(V "+Utils.getVersion(mContext)+")");

        gpswifibleListening = new GPSWIFIBLEListening(this);

        recyclerView = findViewById(R.id.recyclerView);

        bleDeviceListAdapter = new BLEDeviceListAdapter(bleDevList,this);
        bleDeviceListAdapter.onItemClickListener = new BLEDeviceListAdapter.OnItemClickListener() {

            @Override
            public void onItemClick(int position) {
                if (bleDevList.size()>=position){
                    Log.e(TAG, "onItemClick: "+bleDevList.size()+","+position );
                    willConnectType(bleDevList.get(position));
                }
            }

            @Override
            public void onItemEditNameClick(int position, @NotNull String mac , String name) {

            }

            @Override
            public void onItemLongClick(@NonNull String keyName, @NonNull String name) {

            }
        };

        recyclerView.setLayoutManager(new LinearLayoutManager(this,LinearLayoutManager.VERTICAL,false));
        recyclerView.setAdapter(bleDeviceListAdapter);
        recyclerView.setItemAnimator(new DefaultItemAnimator());

        IntentFilter mFilter = new IntentFilter();
        mFilter.addAction("GPSWIFIBLEListening");
        registerReceiver(mReceiver, mFilter, RECEIVER_NOT_EXPORTED);

        bleEnableOpenHint = new AreaAddWindowAddDevice(this, R.style.dialog_style, getString(R.string.dakai_lanya_gongneng),new AreaAddWindowAddDevice.PeriodListener() {
            @Override
            public void cancelListener() { }
            @Override
            public void refreshListener() {
                if (gpswifibleListening.mBluetoothAdapter!=null){
                    if (!gpswifibleListening.mBluetoothAdapter.isEnabled()){
                        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivity(enableBtIntent);
                    }
                }
            }
        },true,"","");
        gpsEnableOpenHint = new AreaAddWindowAddDevice(this, R.style.dialog_style, getString(R.string.dakai_dingwei_gongneng),new AreaAddWindowAddDevice.PeriodListener() {
            @Override
            public void cancelListener() { }
            @Override
            public void refreshListener() {
                Intent intent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        },true,"","");
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (!isTaskShow)
                return;
            // 连接成功！
            if(action.equals("GPSWIFIBLEListening")){
                String permissions = intent.getStringExtra("Listening");
                if(havePermissions){
                    changeUI();
                }
            }
        }
    };

    //根据WIFI提示页面还是蓝牙提示页面修改UI界面
    private void changeUI(){
        if (gpswifibleListening.mBluetoothAdapter == null){
            showToast(getString(R.string.bu_zhichi_lanya));
            return;
        }

        if (gpswifibleListening.mBluetoothAdapter.isEnabled() && gpswifibleListening.isLastGPSEnable()) {
            bleEnableOpenHint.dismiss();
            gpsEnableOpenHint.dismiss();
            scanBLEEnable(true);
        }
        else {
            scanBLEEnable(false);
            if(!gpswifibleListening.mBluetoothAdapter.isEnabled()){
                if (!bleEnableOpenHint.isShowing()) {
                    bleEnableOpenHint.show();
                }
            }else {
                bleEnableOpenHint.dismiss();
            }
            if (!gpswifibleListening.isLastGPSEnable()) {
                if (!gpsEnableOpenHint.isShowing()) {
                    gpsEnableOpenHint.show();
                }
            }else {
                gpsEnableOpenHint.dismiss();
            }
        }

    }

    private boolean havePermissions = false;//是否获取了权限

    /**
     * 控制本页面的蓝牙扫描开关
     * @param isEnable
     */
    private void scanBLEEnable(boolean isEnable) {
        // 缺少权限�?, 进入权限配置页面
        if (mPermissionsChecker.lacksPermissions(PERMISSIONS)) {
            if(isEnable){
                getPermissionsWindow();
            }
            havePermissions = false;
            return;
        }else {
            havePermissions = true;
            if(areaGetPermissionWindow.isShowing()){
                areaGetPermissionWindow.dismiss();
            }
        }

        if (isEnable) {
            if (mBluetoothAdapter != null){
                if(!mBluetoothAdapter.isEnabled()){
                    bleEnableOpenHint.show();
                    return;
                }
                DemoApplication.getInstance().startScanBLEDev(leScanCallback);
                bleDevList.clear();
                bleDeviceListAdapter.notifyDataSetChanged();
            }else {
                showToast(getString(R.string.bu_zhichi_lanya));
            }
        }
        else {
            if (mBluetoothAdapter == null) {
                showToast(getString(R.string.bu_zhichi_lanya));
                return;
            }
            DemoApplication.getInstance().stopScanBLEDev();
        }
    }

    private static final int REQUEST_CODE = 0; // 请求�?
    private void startPermissionsActivity() {
        PermissionsActivity.startActivityForResult(this, REQUEST_CODE, PERMISSIONS);
    }

    private void getPermissionsWindow(){
        if(!mPermissionsChecker.lacksPermissions(PERMISSIONS)){
            DemoApplication.getInstance().startScanBLEDev(leScanCallback);
        }else {
            if(!areaGetPermissionWindow.isShowing()){
                areaGetPermissionWindow.setMsgAndShow(getString(R.string.fujinshebei_dingwei_quanxian), new AreaGetPermissionWindow.PeriodListener() {
                    @Override
                    public void cancelListener() {

                    }
                    @Override
                    public void refreshListener() {
                        startPermissionsActivity();
                        DemoApplication.getInstance().saveValueBySharedPreferences(BaseVolume.INIT_UMENG_SDK,"1");
                    }
                },false,getString(R.string.qu_xiao),getString(R.string.que_ding));
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onReceiveMessageInfo(MessageInfo msg){
        if(msg.getICode() == MessageInfo.i_GATT_CONNECT_STATE){
            switch (msg.getIParam()){
                case MessageInfo.i_GATT_CONNECT_STATE_CONNECTING:
                    break;
                case MessageInfo.i_GATT_CONNECT_STATE_CONNECTED:
                    break;
                case MessageInfo.i_GATT_CONNECT_STATE_DISCONNECT:
                    break;
            }
        }
        if(msg.getICode() == MessageInfo.i_GATT_SCAN_BLE_DEV){
            BluetoothDevice bluetoothDevice = (BluetoothDevice)msg.getObjects();
            if(bluetoothDevice == null || bluetoothDevice.getName() == null){
                return;
            }
            int iRssi = msg.getIParam();
            byte[] receByteArray = msg.getByteArray();
            String strReceHexData = Utils.bytesToHexString(receByteArray);
//                Log.e(TAG, "onReceiveMessageInfo: 广播内容："+strReceHexData );
            boolean newDevice = true;
            for(int i=0;i<bleDevList.size();i++){
                if(bleDevList.get(i).getMACAddress().equals(bluetoothDevice.getAddress())){
                    newDevice = false;
                    break;
                }
            }
            if(newDevice){
                bleDevList.add(new SearchBLEDeviceInfo(bluetoothDevice,iRssi,strReceHexData));
                bleDeviceListAdapter.notifyDataSetChanged();
            }
        }
    }

    //蓝牙连接
    private void willConnectType(SearchBLEDeviceInfo searchBLEDeviceInfo) {
        if (DemoApplication.getInstance().getBleDevLeClass(searchBLEDeviceInfo.getMACAddress()) != null) {
            return;
        }
        scanBLEEnable(false);
        DemoApplication.getInstance().nowSelectDevice = searchBLEDeviceInfo;
        loadingDialog.showAndMsg(getString(R.string.qing_shaohou));
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                DataAnalysisHelper.Companion.getInstance(mContext).removeDataBufferByMac(DemoApplication.getInstance().nowSelectDevice.getMACAddress());
                connectDevice();
            }
        },500);

    }

    /**
     * 连接蓝牙
     */
    public void connectDevice() {
        BleConnectOptions options = new BleConnectOptions.Builder()
                .setConnectRetry(1)// 连接如果失败重试1次
                .setConnectTimeout(10*1000)// 连接超时10s
                .setServiceDiscoverRetry(1)// 发现服务如果失败重试1次
                .setServiceDiscoverTimeout(10*1000)// 发现服务超时10s
                .build();
        Log.e(TAG, DemoApplication.getInstance().nowSelectDevice.getMACAddress()+",开始连接...");
        ClientManager.getClient().connect(DemoApplication.getInstance().nowSelectDevice.getMACAddress(), options, new BleConnectResponse() {
            @Override
            public void onResponse(int code, BleGattProfile profile) {
                BluetoothLog.v(String.format("profile:\n%s", profile));
                if (code == Constants.REQUEST_SUCCESS) {
                    ClientManager.getClient().registerConnectStatusListener(DemoApplication.getInstance().nowSelectDevice.getMACAddress(), mConnectStatusListener);
                    Log.e(TAG, "连接成功，获取并绑定通道！");
                    getProFileInfo(profile);

                }
                else {
                    Log.e(TAG,"连接失败"+"，code："+ Code.toString(code));
                    if(loadingDialog.isShowing()){
                        loadingDialog.dismiss();
                    }
                    areaAddWindowHint.setMsgAndShow(getString(R.string.lianjie_shibai_zaishi_yichi), new AreaAddWindowHint.PeriodListener() {
                        @Override
                        public void cancelListener() {
                            scanBLEEnable(true);
                        }
                        @Override
                        public void refreshListener() {
                            willConnectType(DemoApplication.getInstance().nowSelectDevice);
                        }
                    },false,getString(R.string.qu_xiao),getString(R.string.chongshi));
                }
            }
        });
    }

    // 3432，名称是 HLK-B40时， 通讯 服务 和 UUID
    private final String UUID_BY_SERVER_3432 = "0000fff0-0000-1000-8000-00805f9b34fb";
    private final String UUID_BY_READ_3432 = "0000fff1-0000-1000-8000-00805f9b34fb";
    private final String UUID_BY_WRITE_3432 = "0000fff2-0000-1000-8000-00805f9b34fb";

    // 2450，名称是 HLK-LD2450时， 通讯 服务 和 UUID
    private final String UUID_BY_SERVER_2450 = "0000ae00-0000-1000-8000-00805f9b34fb";
    private final String UUID_BY_READ_2450 = "0000ae02-0000-1000-8000-00805f9b34fb";
    private final String UUID_BY_WRITE_2450 = "0000ae01-0000-1000-8000-00805f9b34fb";

    // HMD02雷达模块，感应灯
    private final String UUID_BY_SERVER_HMD02 = "00010203-0405-0607-0809-0a0b0c0d1910";
    private final String UUID_BY_READ_HMD02 = "00010203-0405-0607-0809-0a0b0c0d2b10";
    private final String UUID_BY_WRITE_HMD02 = "00010203-0405-0607-0809-0a0b0c0d2b11";

    private UUID mService;
    private UUID mCharacterRead;
    private UUID mCharacterWrite;
    // 获取所有的Server 和 UDID
    private void getProFileInfo(BleGattProfile profile) {
        List<BleGattService> services = profile.getServices();
        boolean isServer = false;
        boolean isReadUUID = false;
        boolean isWriteUUID = false;
        if(DemoApplication.getInstance().nowSelectDevice.getDevName().contains("HLK-LD2450")){
            for (BleGattService service : services) {
                // 检测2450是否包含有版本号，如果为空就用UUID_BY_SERVER_2450，反之用UUID_BY_WRITE_2450
                if (DemoApplication.getInstance().nowSelectDevice.getStrVerInfo().equals("")){
                    // 3432的透传Server B10/B20/B30设备
                    if (service.getUUID().toString().equalsIgnoreCase(UUID_BY_SERVER_2450)) {
                        isServer = true;
                        mService = service.getUUID();
                        List<BleGattCharacter> characters = service.getCharacters();
                        for (BleGattCharacter character : characters) {
                            if (character.getUuid().toString().equalsIgnoreCase(UUID_BY_READ_2450)) {
                                isReadUUID = true;
                                mCharacterRead = character.getUuid();
                                ClientManager.getClient().notify(DemoApplication.getInstance().nowSelectDevice.getMACAddress(), mService, mCharacterRead, mNotifyRsp);
                            }
                            else if (character.getUuid().toString().equalsIgnoreCase(UUID_BY_WRITE_2450)) {
                                isWriteUUID = true;
                                mCharacterWrite = character.getUuid();
                            }
                        }
                        break;
                    }
                }else {
                    if (service.getUUID().toString().equalsIgnoreCase(UUID_BY_SERVER_3432)) {
                        isServer = true;
                        mService = service.getUUID();
                        List<BleGattCharacter> characters = service.getCharacters();
                        for (BleGattCharacter character : characters) {
                            if (character.getUuid().toString().equalsIgnoreCase(UUID_BY_READ_3432)) {
                                isReadUUID = true;
                                mCharacterRead = character.getUuid();
                                ClientManager.getClient().notify(DemoApplication.getInstance().nowSelectDevice.getMACAddress(), mService, mCharacterRead, mNotifyRsp);
                            } else if (character.getUuid().toString().equalsIgnoreCase(UUID_BY_WRITE_3432)) {
                                isWriteUUID = true;
                                mCharacterWrite = character.getUuid();
                            }
                        }
                        break;
                    }
                }
            }
        }else if(DemoApplication.getInstance().nowSelectDevice.getDevName().contains("HLK-LD2410")) {
            if (DemoApplication.getInstance().nowSelectDevice.isAllowUpgrade()) {
                for (BleGattService service : services) {
                    if (service.getUUID().toString().equalsIgnoreCase(UUID_BY_SERVER_2450) ) {
                        isServer = true;
                        mService = service.getUUID();
                        List<BleGattCharacter> characters = service.getCharacters();
                        for (BleGattCharacter character : characters) {
                            if (character.getUuid().toString().equalsIgnoreCase(UUID_BY_READ_2450)) {
                                isReadUUID = true;
                                mCharacterRead = character.getUuid();
                                ClientManager.getClient().notify(DemoApplication.getInstance().nowSelectDevice.getMACAddress(), mService, mCharacterRead, mNotifyRsp);
                            } else if (character.getUuid().toString().equalsIgnoreCase(UUID_BY_WRITE_2450)) {
                                isWriteUUID = true;
                                mCharacterWrite = character.getUuid();
                            }
                        }
                        break;
                    }
                }
            } else {
                for (BleGattService service : services) {
                    if (service.getUUID().toString().equalsIgnoreCase(UUID_BY_SERVER_3432)) {
                        isServer = true;
                        mService = service.getUUID();
                        List<BleGattCharacter> characters = service.getCharacters();
                        for (BleGattCharacter character : characters) {
                            if (character.getUuid().toString().equalsIgnoreCase(UUID_BY_READ_3432)) {
                                isReadUUID = true;
                                mCharacterRead = character.getUuid();
                                ClientManager.getClient().notify(DemoApplication.getInstance().nowSelectDevice.getMACAddress(), mService, mCharacterRead, mNotifyRsp);
                            } else if (character.getUuid().toString().equalsIgnoreCase(UUID_BY_WRITE_3432)) {
                                isWriteUUID = true;
                                mCharacterWrite = character.getUuid();
                            }
                        }
                        break;
                    }
                }
            }
        }

        else if (DemoApplication.getInstance().nowSelectDevice.getDevName().contains("WST-") || DemoApplication.getInstance().nowSelectDevice.getDevName().contains("HMD02")) {
            for (BleGattService service : services) {
                // 3432的透传Server B10/B20/B30设备
                Log.e(TAG, "HMD02的透传Server: UUID_BY_SERVER_HMD02" );
                if (service.getUUID().toString().equalsIgnoreCase(UUID_BY_SERVER_HMD02)) {
                    isServer = true;
                    mService = service.getUUID();
                    List<BleGattCharacter> characters = service.getCharacters();
                    for (BleGattCharacter character : characters) {
                        if (character.getUuid().toString().equalsIgnoreCase(UUID_BY_READ_HMD02)) {
                            isReadUUID = true;
                            mCharacterRead = character.getUuid();
                            ClientManager.getClient().notify(DemoApplication.getInstance().nowSelectDevice.getMACAddress(), mService, mCharacterRead, mNotifyRsp);
                        }
                        else if (character.getUuid().toString().equalsIgnoreCase(UUID_BY_WRITE_HMD02)) {
                            isWriteUUID = true;
                            mCharacterWrite = character.getUuid();
                        }
                    }
                    break;
                }
            }
        } else {
            for (BleGattService service : services) {
                // 3432的透传Server B10/B20/B30设备
                Log.e(TAG, "getProFileInfo3432的透传Server: UUID_BY_SERVER_3432" );
                if (service.getUUID().toString().equalsIgnoreCase(UUID_BY_SERVER_3432)) {
                    isServer = true;
                    mService = service.getUUID();
                    List<BleGattCharacter> characters = service.getCharacters();
                    for (BleGattCharacter character : characters) {
                        if (character.getUuid().toString().equalsIgnoreCase(UUID_BY_READ_3432)) {
                            isReadUUID = true;
                            mCharacterRead = character.getUuid();
                            ClientManager.getClient().notify(DemoApplication.getInstance().nowSelectDevice.getMACAddress(), mService, mCharacterRead, mNotifyRsp);
                        }
                        else if (character.getUuid().toString().equalsIgnoreCase(UUID_BY_WRITE_3432)) {
                            isWriteUUID = true;
                            mCharacterWrite = character.getUuid();
                        }
                    }
                    break;
                }
            }
        }

        // 未找到透传Server
        if (!isServer) {
            ClientManager.getClient().disconnect(DemoApplication.getInstance().nowSelectDevice.getMACAddress());
            if(loadingDialog.isShowing()){
                loadingDialog.dismiss();
            }
        }
        // 未找到读取通道
        if (!isReadUUID) {
            ClientManager.getClient().disconnect(DemoApplication.getInstance().nowSelectDevice.getMACAddress());
            if(loadingDialog.isShowing()){
                loadingDialog.dismiss();
            }
        }
        // 未找到写入通道
        if (!isWriteUUID) {
            ClientManager.getClient().disconnect(DemoApplication.getInstance().nowSelectDevice.getMACAddress());
            if(loadingDialog.isShowing()){
                loadingDialog.dismiss();
            }
        }

    }

    private final BleNotifyResponse mNotifyRsp = new BleNotifyResponse() {
        @Override
        public void onNotify(UUID service, UUID character,final byte[] value) {
            if (service.equals(mService) && character.equals(mCharacterRead)) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String strNewData = ByteUtils.byteToString(value);
                        Log.e(TAG,"接收蓝牙数据:"+ strNewData+"，Length："+strNewData.length());
                        String str = strNewData.substring(12,16);
                        // 接收到的是密码
                        if (str.equalsIgnoreCase(BaseVolume.CMD_TYPE_CHECK_KEY_RESULT)){
                            loadingDialog.dismiss();
                            DemoApplication.getInstance().nowSelectDevice.setStrCtrPwd(strNowCheckPwd);
                            startActivity(new Intent(BLEListActivity.this, NewActivity.class)
                                    .putExtra("deviceMac",DemoApplication.getInstance().nowSelectDevice.getMACAddress()));
                        }
                        // 其他数据
                        else {
                            EventBus.getDefault().post(new DataBean(strNewData));
                        }
                    }
                });
            }
        }
        @Override
        public void onResponse(int code) {
            if (code == Constants.REQUEST_SUCCESS) {
                Log.e(TAG,"接收广播已开启！开始同步时间！");
                setMTUBySize(512);
            } else {
                Log.e(TAG,"接收广播开启失败！code："+ Code.toString(code));
                ClientManager.getClient().disconnect(DemoApplication.getInstance().nowSelectDevice.getMACAddress());
                scanBLEEnable(true);
                if(loadingDialog.isShowing()){
                    loadingDialog.dismiss();
                }
            }
        }
    };

    //设置自定义MTU
    private int iMTU = 23;
    public void setMTUBySize(int iMtu) {
        ClientManager.getClient().requestMtu(DemoApplication.getInstance().nowSelectDevice.getMACAddress(), iMtu, mMtuResponse);
    }

    private final BleMtuResponse mMtuResponse = new BleMtuResponse() {
        @Override
        public void onResponse(int code, Integer data) {
            if (code == REQUEST_SUCCESS) {
                Log.e(TAG, "request mtu success,!!!!!!!!! MTU = " + data+"!!!!!!!!");
                iMTU = data;
                if(loadingDialog.isShowing()){
                    loadingDialog.dismiss();
                }

                //如果是HLK-LD2411-S
                if(DemoApplication.getInstance().nowSelectDevice.getDevName().contains("HLK-LD2411-S")){
                    // 等待升级的，直接进入升级页面
                    if (DemoApplication.getInstance().nowSelectDevice.isAllowUpgrade()) {
                        showToast("HLK-LD2411-S: 等待升级");
                    }else {
                        showToast("HLK-LD2411-S: 进入页面");
                        startActivity(new Intent(mContext, NewActivity.class)
                                .putExtra("deviceMac",DemoApplication.getInstance().nowSelectDevice.getMACAddress()));
                    }
                }
                //如果是2411就直接进入升级界面
                else if(DemoApplication.getInstance().nowSelectDevice.getDevName().contains("HLK-LD2411")){
                    //有门限值版本是从2.00.23031415版本开始的
                    if(Utils.contrastVersion("2.00.23031414",DemoApplication.getInstance().nowSelectDevice.getStrVerInfo())){
                        if (DemoApplication.getInstance().nowSelectDevice.isAllowUpgrade()) {
                            startActivity(new Intent(mContext,NewActivity.class)
                                    .putExtra("deviceMac",DemoApplication.getInstance().nowSelectDevice.getMACAddress()));
                        }else {
                            startActivity(new Intent(mContext,NewActivity.class)
                                    .putExtra("deviceMac",DemoApplication.getInstance().nowSelectDevice.getMACAddress()));
                        }
                    }else {
                        showToast("HLK-LD2411: 等待升级");
                    }
                }
                //如果是2450就直接进入升级界面
                else if(DemoApplication.getInstance().nowSelectDevice.getDevName().contains("HLK-LD2450")){
                    if (!isNeedStart){
                        if (Utils.contrastVersion("2.02.23090616",DemoApplication.getInstance().nowSelectDevice.getStrVerInfo())){
                            startActivity(new Intent(mContext,NewActivity.class)
                                    .putExtra("deviceMac",DemoApplication.getInstance().nowSelectDevice.getMACAddress()));
                        }else {
                            showToast("HLK-LD2450: 等待升级");
                        }
                    }
                }
                //如果是2451就直接进入升级界面
                else if(DemoApplication.getInstance().nowSelectDevice.getDevName().contains("HLK-LD2451")){
                    startActivity(new Intent(mContext,NewActivity.class)
                            .putExtra("deviceMac",DemoApplication.getInstance().nowSelectDevice.getMACAddress()));
                }
                // 如果是2412
                else if (DemoApplication.getInstance().nowSelectDevice.getDevName().contains("LD2412")){
                    startActivity(new Intent(mContext,NewActivity.class)
                            .putExtra("deviceMac",DemoApplication.getInstance().nowSelectDevice.getMACAddress()));
                }
                else if (DemoApplication.getInstance().nowSelectDevice.getDevName().contains("HLK-6002B")){
                    startActivity(new Intent(mContext,NewActivity.class)
                            .putExtra("deviceMac",DemoApplication.getInstance().nowSelectDevice.getMACAddress()));
                }
                // HMD02
                else if (DemoApplication.getInstance().nowSelectDevice.getDevName().contains("HMD02")){
                    startActivity(new Intent(mContext,NewActivity.class)
                            .putExtra("deviceMac",DemoApplication.getInstance().nowSelectDevice.getMACAddress()));
                }
                //如果是2410
                else {
                    // 等待升级的，直接进入升级页面
                    if (DemoApplication.getInstance().nowSelectDevice.isAllowUpgrade()) {
                        showToast("HLK-LD: 等待升级");
                    }
                    else {
                        // 支持密码验证
                        if (DemoApplication.getInstance().nowSelectDevice.getNowBleDevType() == BleDevType.Haved_Ver_Haved_CheckPwd_Haved_Upgrade_Haved_SPI) {
                            String strOldPwd = DemoApplication.getInstance().nowSelectDevice.getStrCtrPwd();
                            if (!strOldPwd.equals("")) {
                                checkCtrPwd(strOldPwd);
                            }
                            else {
                                checkCtrPwd("HiLink");
                            }
                        }
                        // 不支持密码验证，直接进入下一页
                        else {
                            startActivity(new Intent(mContext, NewActivity.class)
                                    .putExtra("deviceMac",DemoApplication.getInstance().nowSelectDevice.getMACAddress()));
                        }
                    }
                }

            } else {
                showToast("request mtu failed,MTU = " + data);
                iMTU = data;
                if(loadingDialog.isShowing()){
                    loadingDialog.dismiss();
                }
            }

        }
    };

    private String strNowCheckPwd = "";
    private void checkCtrPwd(String strPwd) {
        strNowCheckPwd = strPwd;
        String strCmdData = CreateControlData.Companion.checkKeyByPwd(strPwd);
        Log.e(TAG,"校验密码："+strNowCheckPwd+",Cmd:"+strCmdData);
        loadingDialog.showAndMsg(getString(R.string.yanzheng_mima));
        sendDataByMAC(DemoApplication.getInstance().nowSelectDevice.getMACAddress(),strCmdData);
    }

    //断开蓝牙连接
    private void disconnectBluetooth(String strMac){
        ClientManager.getClient().unregisterConnectStatusListener(strMac, mConnectStatusListener);
        ClientManager.getClient().disconnect(strMac);
        DemoApplication.getInstance().nowSelectDevice = null;
        scanBLEEnable(true);
        if(loadingDialog.isShowing()){
            loadingDialog.dismiss();
        }
    }

    private BleConnectStatusListener mConnectStatusListener = new BleConnectStatusListener() {
        @Override
        public void onConnectStatusChanged(String mac, int status) {
            Log.e(TAG,"设备："+ mac+"，连接状态发生改变，status："+status+"   MAC="+mac);
            if(status==16){
            }else {
                Intent bluetooth = new Intent("BLEDisconnect");
                bluetooth.putExtra("mac",mac);
                bluetooth.setPackage(getPackageName());
                sendBroadcast(bluetooth);
            }
        }
    };

    /** 发送数据 */
    public void sendDataByMAC(String strMac,String strData) {
        Log.e(TAG,"发送蓝牙数据:"+ strMac+"，Data："+strData.toUpperCase());
        byte[] sendByte = Utils.hexStringToBytes(strData);
        ClientManager.getClient().write(strMac, mService, mCharacterWrite, sendByte, new BleWriteResponse() {
            @Override
            public void onResponse(int code) {
                if (code == Constants.REQUEST_SUCCESS) {
                    Log.e(TAG,"数据发送成功！");
//
                } else {
                    Log.e(TAG,"数据发送失败！code："+Code.toString(code));
                }
            }
        });
    }

    /** 发送字节数据 */
    public void sendByteDataByMAC(String strMac,byte[] strData) {
        ClientManager.getClient().write(strMac, mService, mCharacterWrite, strData, new BleWriteResponse() {
            @Override
            public void onResponse(int code) {
                if (code == Constants.REQUEST_SUCCESS) {
                    Log.e(TAG,"字节数据发送成功！");
//
                } else {
                    Log.e(TAG,"字节数据发送失败！code："+Code.toString(code));
                }
            }
        });
    }

    private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
//			if (!checkBluetoothConnectPermission())return;
            if (device.getName() == null || device.getName().length() == 0)
                return;
			if(device.getName().contains("HLK-LD") ||
					device.getName().contains("LD2451") ||
					device.getName().contains("HLK-6002B") || device.getName().contains("WST-") ||
					device.getName().contains("HMD02")){

                String strReceHexData = Utils.bytesToHexString(scanRecord);
                boolean newDevice = true;
                for(int i=0;i<bleDevList.size();i++){
                    if(bleDevList.get(i).getMACAddress().equals(device.getAddress())){
                        newDevice = false;
                    }
                }
                if(newDevice){
                    bleDevList.add(new SearchBLEDeviceInfo(device,rssi,strReceHexData));
                    bleDeviceListAdapter.notifyDataSetChanged();
                }
			}
        }
    };

    private boolean isTaskShow = false;
    // 用于标记是否是和扫描或者展示二维码有关的跳转操作
    private boolean isScanOrShowQRCode = false;
    @Override
    protected void onResume() {
        super.onResume();
        String time = getStringBySharedPreferences("systemTime");
        if (time==null || time==""){
            DemoApplication.getInstance().saveValueBySharedPreferences("systemTime",String.valueOf(System.currentTimeMillis()));
        }
        BluetoothManager bluetoothManager = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        DemoApplication.getInstance().initBLEClass(mBluetoothAdapter);
        scanBLEEnable(true);
//        DemoApplication.getInstance().startScanBLEDev();
        isScanOrShowQRCode = false;
        isTaskShow = true;
        isNeedStart = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        isTaskShow = false;
    }

    private long LastBackTime = 0;
    @Override
    public void onBackPressed() {
        if (System.currentTimeMillis() - LastBackTime > 1500) {
            // 如果当前处于选择设备的状态，就重置，不提示点击退出消息
            if (bleDeviceListAdapter.getCheckDevice()){
                bleDeviceListAdapter.setCheckDevice(false);
            }else {
                LastBackTime = System.currentTimeMillis();
                showToast(getString(R.string.zaici_dianji_tuichu_yingyong));
            }
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        DemoApplication.getInstance().stopScanBLEDev();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        DemoApplication.getInstance().stopScanBLEDev();
        DemoApplication.getInstance().clearAllBLEDev();
        unregisterReceiver(mReceiver);
    }
}
