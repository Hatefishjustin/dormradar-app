package com.hlk.hlkradartool.data

import android.content.Context
import android.util.Log
import com.hlk.hlkradartool.util.BaseVolume
import com.hlk.hlkradartool.util.Utils
import org.greenrobot.eventbus.EventBus

class DataAnalysisHelper{
    private val TAG = "DataAnalysisHelper"
    private lateinit var mContext:Context
    constructor(con: Context) {
        this.mContext = con
    }

    companion object {
        private lateinit var dataAnalysisHelper: DataAnalysisHelper
        // 所有的设备数据缓存
        private var deviceStateHashMap = HashMap<String, DeviceState>()

        fun getInstance(con: Context): DataAnalysisHelper {
            if (!this::dataAnalysisHelper.isInitialized) {
                dataAnalysisHelper = DataAnalysisHelper(con)
                deviceStateHashMap.clear()
            }
            return dataAnalysisHelper
        }
    }

    public fun getDeviceStateByMAC(strMac: String) : DeviceState {
        if (deviceStateHashMap[strMac] == null) {
            deviceStateHashMap[strMac] = DeviceState(strMac, mContext)
        }
        return deviceStateHashMap[strMac]!!
    }

    public fun removeDataBufferByMac(strMac: String) {
        deviceStateHashMap.remove(strMac)
    }


    /** 开始数据解析  */
    @Synchronized // 线程锁，同一时刻只能允许一个操作。
    fun startDataAnalysis(strMac: String, strData: String,type:String) {
        if (deviceStateHashMap[strMac] == null) {
            deviceStateHashMap[strMac] = DeviceState(strMac, mContext)
        }

        val strHead = strData.substring(0, 8)
        val strEnd = strData.substring(strData.length - 8)

        // 主动上报
        if (strHead.equals(BaseVolume.CMD_TYPE_AUTO_PUSH_HEAD)) {
            // 如果是2412，主动上报的数据有不同，要分开解析
            if (type.equals("2412")){
                deviceStateHashMap[strMac]!!.analysis2412AutoData(strData)
            }else{
                deviceStateHashMap[strMac]!!.analysisAutoData(strData)
                Log.e(TAG,deviceStateHashMap[strMac]!!.autoPushToString())
            }
            Log.e(TAG,"收到主动上报数据↑↑↑↑↑↑↑↑↑↑↑↑")
            val receiveInfo = ReceiveInfo(ReceiveInfo.i_AUTO_PUSH_DATA,strMac)
            EventBus.getDefault().post(receiveInfo)
        }
        // 设置或查询的回复
        else {
            Log.e(TAG,"收到设置或查询的回复："+strData)
            val strResultType = strData.substring(12,16)
            val isACK = (strData.substring(16,20)).equals("0000")
            var receiveInfo = ReceiveInfo(ReceiveInfo.i_SET_RESULT,strMac)
            receiveInfo.blParam = isACK
            receiveInfo.strParam = strResultType
            // 读取参数的回复
            if (strResultType.equals(BaseVolume.CMD_TYPE_READ_INFO_RESULT)) {
                receiveInfo.iCode = ReceiveInfo.i_QUERY_RESULT
                if (isACK) {
                    deviceStateHashMap[strMac]!!.analysisReadParameter(strData)
                    Log.e(TAG,deviceStateHashMap[strMac]!!.toString())
                }
            }// 读取版本号的回复
            else if (strResultType.equals(BaseVolume.CMD_TYPE_READ_VER_RESULT)) {
                receiveInfo.iCode = ReceiveInfo.i_QUERY_RESULT
                if (isACK) {
                    deviceStateHashMap[strMac]!!.analysisReadFirmwareInfo(strData)
                    Log.e(TAG,deviceStateHashMap[strMac]!!.toString())
                }
            }
            // 读取分辨率的回复
            else if (strResultType.equals(BaseVolume.CMD_TYPE_READ_DOOR_DPI_RESULT)) {
                receiveInfo.iCode = ReceiveInfo.i_QUERY_RESULT
                if (isACK) {
                    deviceStateHashMap[strMac]!!.analysisReadDoorDPI(strData)
                    Log.e(TAG,deviceStateHashMap[strMac]!!.toString())
                }
            }
            // 读取光敏配置的回复
            else if (strResultType.equals(BaseVolume.CMD_TYPE_READ_PHOTOSENSITIVE_RESULT)) {
                receiveInfo.iCode = ReceiveInfo.i_QUERY_RESULT
                if (isACK) {
                    deviceStateHashMap[strMac]!!.analysisPhotosensitive(strData)
                    Log.e(TAG,deviceStateHashMap[strMac]!!.toString())
                }
            }
            // 读取门限值的回复
            else if (strResultType.equals(BaseVolume.CMD_TYPE_READ_THRESHOLD_VALUE)) {
                receiveInfo.iCode = ReceiveInfo.i_QUERY_RESULT
                deviceStateHashMap[strMac]!!.analysisThresholdData(strData)
                Log.e(TAG,deviceStateHashMap[strMac]!!.toString())
            }
            // 设置门限值的回复
            else if (strResultType.equals(BaseVolume.CMD_TYPE_SET_THRESHOLD_VALUE_REPLY)) {
                receiveInfo.iCode = ReceiveInfo.i_SET_RESULT
                deviceStateHashMap[strMac]!!.thresholdState = strData.substring(16,18).equals("01")
                Log.e(TAG,deviceStateHashMap[strMac]!!.toString())
            }
            // 主动上报门限距离结果
            else if (strResultType.equals(BaseVolume.CMD_TYPE_INITIATIVE_THRESHOLD)) {
                receiveInfo.iCode = ReceiveInfo.i_AUTO_PUSH_DATA
                deviceStateHashMap[strMac]!!.initiativeThresholdState = strData.substring(16,18).equals("01")
                Log.e(TAG,deviceStateHashMap[strMac]!!.toString())
            }
            // 收到自动底噪检测的回复
            else if (strResultType.equals(BaseVolume.CMD_GROUND_NOISE_REPLY_AUTO_2410)) {
                receiveInfo.iCode = ReceiveInfo.i_SET_RESULT
            }
            // 恢复出厂设置
            else if (strResultType.equals(BaseVolume.CMD_FULL_DEFAULT)){
                receiveInfo.iCode = ReceiveInfo.i_SET_RESULT
            }
            // 查询2450的mac地址
            else if (strResultType.equals(BaseVolume.CMD_TYPE_GET_MAC_ADDRESS_REPLY_2450)){
                receiveInfo.iCode = ReceiveInfo.i_QUERY_RESULT
                deviceStateHashMap[strMac]!!.strMAC = strData.substring(20,32)

                Log.e(TAG, "startDataAnalysis: 收到MAC地址"+strData.substring(20,32))
            }
            // 2412基础参数查询
            else if (strResultType.equals(BaseVolume.CMD_READ_BASE_DATA_REPLY_2412)){
                deviceStateHashMap[strMac]!!.analysisBaseData2412(strData.substring(20,30))
                receiveInfo.iCode = ReceiveInfo.i_QUERY_RESULT
            }
            // 2412运动灵敏度查询
            else if (strResultType.equals(BaseVolume.CMD_READ_RUN_DATA_REPLY_2412)){
                deviceStateHashMap[strMac]!!.analysisRunSensitivity(strData.substring(20,20+28))
                receiveInfo.iCode = ReceiveInfo.i_QUERY_RESULT
            }
            // 2412静态灵敏度查询
            else if (strResultType.equals(BaseVolume.CMD_READ_STATIC_DATA_REPLY_2412)){
                deviceStateHashMap[strMac]!!.analysisStaticSensitivity(strData.substring(20,20+28))
                receiveInfo.iCode = ReceiveInfo.i_QUERY_RESULT
            }
            // 2412距离门查询 FDFCFBFA 0A00 1101 0000 0100 0000000004030201
            else if(strResultType.equals(BaseVolume.CMD_READ_DISTANCE_DOOR_REPLY_2412)){
                deviceStateHashMap[strMac]!!.analysisDistanceDoorNum(strData.substring(20,24))
                receiveInfo.iCode = ReceiveInfo.i_QUERY_RESULT
            }
            // 2412光感辅助控制功能配置查询
            else if (strResultType.equals(BaseVolume.CMD_READ_PHOTOSENSITIVE_REPLY_2412)){
                deviceStateHashMap[strMac]!!.analysis2412Photosensitive(strData.substring(20,20+4))
                receiveInfo.iCode = ReceiveInfo.i_QUERY_RESULT
            }
            // 查询目标追踪模式
            else if (strResultType.equals(BaseVolume.CMD_TYPE_GET_TARGET_TRACKING_MODE_REPLY_2450, ignoreCase = true)){
                Log.e(TAG, "查询目标追踪模式: "+strData.substring(20,24))
                deviceStateHashMap[strMac]!!.isMultipleTarget = !strData.substring(20,24).equals(BaseVolume.CMD_TYPE_GET_TARGET_TRACKING_MODE_REPLY_SINGLE_2450)
                receiveInfo.iCode = ReceiveInfo.i_QUERY_RESULT
            }
            // 2450的单目标追踪
            else if (strResultType.equals(BaseVolume.CMD_TYPE_SINGLE_TARGET_TRACKING_REPLY_2450)){
                if (isACK){
                    deviceStateHashMap[strMac]!!.isMultipleTarget = false
                }
                receiveInfo.iCode = ReceiveInfo.i_SET_RESULT
            }
            // 2450的多目标追踪
            else if (strResultType.equals(BaseVolume.CMD_TYPE_MULTIPLE_TARGET_TRACKING_REPLY_2450)){
                if (isACK){
                    deviceStateHashMap[strMac]!!.isMultipleTarget = true
                }
                receiveInfo.iCode = ReceiveInfo.i_SET_RESULT
            }
            // 2450的蓝牙开关设置
            else if (strResultType.equals(BaseVolume.CMD_TYPE_BLUETOOTH_SET_REPLY_2450)){
                receiveInfo.iCode = ReceiveInfo.i_SET_RESULT
            }
            // 2450查询当前的区域过滤配置
            else if (strResultType.equals(BaseVolume.CMD_TYPE_GET_FILTRATION_REPLY_2450)){
                receiveInfo.iCode = ReceiveInfo.i_QUERY_RESULT
                deviceStateHashMap[strMac]!!.analysisFiltration(strData.substring(20,72))
            }
            // 2450设置区域过滤配置
            else if (strResultType.equals(BaseVolume.CMD_TYPE_SET_FILTRATION_REPLY_2450)){
                receiveInfo.iCode = ReceiveInfo.i_SET_RESULT
            }
            // 2450重启模块
            else if (strResultType.equals(BaseVolume.CMD_TYPE_RESET_RESULT)){
                receiveInfo.iCode = ReceiveInfo.i_SET_RESULT
            }
            // 2412基础参数配置
            else if (strResultType.equals(BaseVolume.CMD_SET_BASE_DATA_REPLY_2412)){
                receiveInfo.iCode = ReceiveInfo.i_SET_RESULT
            }
            // 2412运动灵敏度配置
            else if (strResultType.equals(BaseVolume.CMD_SET_RUN_DATA_REPLY_2412)){
                receiveInfo.iCode = ReceiveInfo.i_SET_RESULT
            }
            // 2412静态灵敏度配置
            else if (strResultType.equals(BaseVolume.CMD_SET_STATIC_DATA_REPLY_2412)){
                receiveInfo.iCode = ReceiveInfo.i_SET_RESULT
            }
            // 2412静态灵敏度配置
            else if (strResultType.equals(BaseVolume.CMD_SET_STATIC_DATA_REPLY_2412)){
                receiveInfo.iCode = ReceiveInfo.i_SET_RESULT
            }
            // 2412光感辅助控制功能配置设置
            else if (strResultType.equals(BaseVolume.CMD_SET_PHOTOSENSITIVE_REPLY_2412)){
                receiveInfo.iCode = ReceiveInfo.i_SET_RESULT
            }
            // 2412距离门设置
            else if (strResultType.equals(BaseVolume.CMD_SET_DISTANCE_DOOR_REPLY_2412)){
                receiveInfo.iCode = ReceiveInfo.i_SET_RESULT
            }
            // 2410日志开关
            else if (strResultType.equals(BaseVolume.RIZHIKAIGUAN_RESULT)){
                if(strData.substring(16,18).equals("00")){
                    deviceStateHashMap[strMac]!!.isrizhiopen=true
                }else{
                    deviceStateHashMap[strMac]!!.isrizhiopen=false
                }

                receiveInfo.iCode = ReceiveInfo.i_SET_RESULT
            }else if (strResultType.equals(BaseVolume.QINGCHURIZHI_RESULT)){
                if(strData.substring(16,18).equals("00")){
                    deviceStateHashMap[strMac]!!.isrizhiclean=true
                }else{
                    deviceStateHashMap[strMac]!!.isrizhiclean=false
                }

                receiveInfo.iCode = ReceiveInfo.i_SET_RESULT
            }else if (strResultType.equals(BaseVolume.RIZHICHANGDU_RESULT)){

                var datalengh=strData.substring(20,24)
                datalengh=datalengh.substring(2,4)+datalengh.substring(0,2)
                deviceStateHashMap[strMac]!!.isrizhilengh=datalengh.toInt(16)
                receiveInfo.iCode = ReceiveInfo.i_SET_RESULT
            }
            EventBus.getDefault().post(receiveInfo)
        }
    }

    /**
     * LD6002的数据解析
     * @param strData 原始数据
     * @param cmdType 命令类型
     **/
    fun startDataAnalysis6002(strMac: String, strData: String,cmdType:String) {
        if (deviceStateHashMap[strMac] == null) {
            deviceStateHashMap[strMac] = DeviceState(strMac, mContext)
        }
        val receiveInfo = ReceiveInfo(ReceiveInfo.i_QUERY_RESULT, strMac)
        receiveInfo.strParam = cmdType
//        Log.e(TAG, "startDataAnalysis6002: " + cmdType )
        when (cmdType) {
            // 报告人员位置 0x0A04
            BaseVolume.LD6002_CMD_TYPE_0A04,BaseVolume.LD6002_CMD_TYPE_0A08 ->{
                deviceStateHashMap[strMac]!!.analysisBodyLocate(strData)
            }
            // 上报区域中是否有人 0x0A0A
            BaseVolume.LD6002_CMD_TYPE_0A0A -> {
                deviceStateHashMap[strMac]!!.analysisHasBody(strData)
            }
            // 上报干扰区域 0x0A0B
            BaseVolume.LD6002_CMD_TYPE_0A0B -> {
                Log.e(TAG, "上报干扰区域 startDataAnalysis6002: " + strData)
                deviceStateHashMap[strMac]!!.analysisInterferenceArea(strData)
            }
            // 上报监控区域 0x0A0C
            BaseVolume.LD6002_CMD_TYPE_0A0C -> {
                Log.e(TAG, "上报监控区域 startDataAnalysis6002: " + strData)
                deviceStateHashMap[strMac]!!.analysisMonitoringArea(strData)
            }
            // 上报保持延时时间 0x0A0D
            BaseVolume.LD6002_CMD_TYPE_0A0D -> {
                Log.e(TAG, "上报保持延时时间 startDataAnalysis6002: " + strData)
                deviceStateHashMap[strMac]!!.delayTime = Utils.reversalHex(strData).toInt(16)
            }
            // 上报检测灵敏度状态 0x0A0E
            BaseVolume.LD6002_CMD_TYPE_0A0E -> {
                Log.e(TAG, "上报检测灵敏度状态 startDataAnalysis6002: " + strData)
                deviceStateHashMap[strMac]!!.detectionSensitivityState = Utils.reversalHex(strData).toInt(16)
            }
            // 上报触发速度状态 0x0A0F
            BaseVolume.LD6002_CMD_TYPE_0A0F -> {
                Log.e(TAG, "上报触发速度状态 startDataAnalysis6002: " + strData)
                deviceStateHashMap[strMac]!!.triggerSpeed = Utils.reversalHex(strData).toInt(16)
            }
            // 上报Z轴范围 0x0A10
            BaseVolume.LD6002_CMD_TYPE_0A10 -> {
                Log.e(TAG, "上报Z轴范围 startDataAnalysis6002: " + strData)
                deviceStateHashMap[strMac]!!.zAxisMinRange = Utils.reversalHex(strData.substring(0,8)).toInt(16)
                deviceStateHashMap[strMac]!!.zAxisMaxRange = Utils.reversalHex(strData.substring(8,16)).toInt(16)
            }
            // 上报安装方式 0x0A11
            BaseVolume.LD6002_CMD_TYPE_0A11 -> {
                Log.e(TAG, "上报安装方式 startDataAnalysis6002: " + strData)
                deviceStateHashMap[strMac]!!.installation = Utils.reversalHex(strData).toInt(16)
            }
            // 上报无人时低功耗模式 0x0A12
            BaseVolume.LD6002_CMD_TYPE_0A12 -> {
                Log.e(TAG, "上报无人时低功耗模式 startDataAnalysis6002: " + strData)
//                deviceStateHashMap[strMac]!!.lowPowerMode = Utils.reversalHex(strData).toInt(16)
                deviceStateHashMap[strMac]!!.lowPowerMode = Utils.reversalHex(strData).toInt(16)
            }
            // 上报无人时低功耗模式睡眠时间 0x0A13
            BaseVolume.LD6002_CMD_TYPE_0A13 -> {
                Log.e(TAG, "上报无人时低功耗模式睡眠时间 startDataAnalysis6002: " + strData)
                deviceStateHashMap[strMac]!!.sleepTime = Utils.reversalHex(strData).toInt(16)
            }
            // 上报工作模式 0x0A14
            BaseVolume.LD6002_CMD_TYPE_0A14 -> {
                Log.e(TAG, "上报工作模式 startDataAnalysis6002: " + strData)
                deviceStateHashMap[strMac]!!.workMode = Utils.reversalHex(strData).toInt(16)
            }
            // 上报雷达固件版本
            // 01 4A4D 0004 FFFF FD 06 03 07 00 FD
            BaseVolume.LD6002_CMD_VERSION_REPLY -> {
                Log.e(TAG, "上报雷达固件版本 startDataAnalysis6002: " + strData)
                if (!strData.equals("")){
                    deviceStateHashMap[strMac]!!.projectName = strData.substring(0,2).toInt(16).toString()
                    deviceStateHashMap[strMac]!!.firmwareVersion = strData.substring(2,4).toInt(16).toString()+ "."+
                            strData.substring(4,6).toInt(16).toString()+"."+
                            strData.substring(6,8).toInt(16).toString()
                }
            }
            // 上报请求固件升级 0x3000
            BaseVolume.LD6002_CMD_UPGRADE_REPLY ->{
                Log.e(TAG, "上报请求固件升级 startDataAnalysis6002: " + strData)
            }
        }
        EventBus.getDefault().post(receiveInfo)
    }


        /** 开始数据解析  */
        @Synchronized // 线程锁，同一时刻只能允许一个操作。
        fun startDataLOGAnalysis(strMac: String, strData: ArrayList<String>, type: String) {
            if (deviceStateHashMap[strMac] == null) {
                deviceStateHashMap[strMac] = DeviceState(strMac, mContext)
            }
            deviceStateHashMap[strMac]!!.isrizhidata = strData
            // Log.e(TAG,"收到设置或查询的回复："+strData)
            // val strResultType = strData.substring(12,16)
            // val isACK = (strData.substring(16,20)).equals("0000")
            var receiveInfo = ReceiveInfo(ReceiveInfo.i_SET_RESULT, strMac)
            receiveInfo.blParam = false
            receiveInfo.strParam = "AA55"
            // 读取参数的回复

            EventBus.getDefault().post(receiveInfo)


        }

        /** 开始数据解析  */
        @Synchronized // 线程锁，同一时刻只能允许一个操作。
        fun startDataAnalysis2411s(strMac: String, strData: String) {

            if (deviceStateHashMap[strMac] == null) {
                deviceStateHashMap[strMac] = DeviceState(strMac, mContext)
            }

            val strHead = strData.substring(0, 8)

            // 主动上报
            if (strHead.equals(BaseVolume.CMD_TYPE_AUTO_PUSH_HEAD_2412)) {
                Log.e(TAG, "收到主动上报数据↑↑↑↑↑↑↑↑↑↑↑↑")
                deviceStateHashMap[strMac]!!.type2411s = strData.substring(4, 6).toInt(16)
                val distance = strData.substring(8, 10) + strData.substring(6, 8)
                deviceStateHashMap[strMac]!!.distance2411s = distance.toInt(16)
                val receiveInfo = ReceiveInfo(ReceiveInfo.i_AUTO_PUSH_DATA, strMac)
                EventBus.getDefault().post(receiveInfo)
            }
            // 设置或查询的回复
            else {
                Log.e(TAG, "收到设置或查询的回复↑↑↑↑↑↑↑↑↑↑↑↑")
                val strResultType = strData.substring(12, 16)
                val isACK = (strData.substring(16, 20)).equals("0000")
                val receiveInfo = ReceiveInfo(ReceiveInfo.i_QUERY_RESULT, strMac)
                receiveInfo.blParam = isACK
                receiveInfo.strParam = strResultType
                //读参数的回复
                if (strResultType.equals(BaseVolume.CMD_READ_DATA_REPLY_2411s, true)) {
                    receiveInfo.iCode = ReceiveInfo.i_QUERY_RESULT
                    deviceStateHashMap[strMac]!!.analysisReadData2411s(strData);
                }
                //收到使能打开的回复
                else if (strResultType.equals(BaseVolume.CMD_TYPE_ENABLE_ON_RESULT, true)) {
                    receiveInfo.iCode =
                        ReceiveInfo.i_SET_RESULT
                }
                //收到使能关闭的回复
                else if (strResultType.equals(BaseVolume.CMD_TYPE_ENABLE_OFF_RESULT, true)) {
                    receiveInfo.iCode = ReceiveInfo.i_SET_RESULT
                }
                //收到模块重启的回复
                else if (strResultType.equals(BaseVolume.CMD_TYPE_RESET_RESULT, true)) {
                    receiveInfo.iCode = ReceiveInfo.i_SET_RESULT
                }
                //收到恢复出厂的回复
                else if (strResultType.equals(BaseVolume.CMD_TYPE_DEFAULT_RESULT, true)) {
                    receiveInfo.iCode = ReceiveInfo.i_SET_RESULT
                }
                //收到设置运动范围、微动范围、持续时间的回复
                else if (strResultType.equals(
                        BaseVolume.CMD_WRITE_SCOPE_TIME_DATA_REPLY_2411s,
                        true
                    )
                ) {
                    receiveInfo.iCode = ReceiveInfo.i_SET_RESULT
                }
                //收到设置波特率的回复
                else if (strResultType.equals(BaseVolume.CMD_TYPE_SET_BAUD_RESULT, true)) {
                    receiveInfo.iCode = ReceiveInfo.i_SET_RESULT
                }

                EventBus.getDefault().post(receiveInfo)

            }

        }

//    /** 根据类型解析数据 */
//    fun analysisByType(strIotID:String,strB6 : String,strContent:String) {
//
//        // 0x00 恢复出厂设置
//        if (strB6.equals(BaseVolume.COMMAND_TYPE_DEFAULT)) {
//        }
//        // 0x01 主机信息
//        else if (strB6.equals(BaseVolume.COMMAND_TYPE_DEVICE_INFO)) {
//            deviceInfoAnalysis(strIotID,strContent)
//        }
//        // 0x02 主机时间
//        else if (strB6.equals(BaseVolume.COMMAND_TYPE_DEVICE_TIME)) {
//            timeInfoAnalysis(strIotID,strContent)
//        }
//        // 0x03 风扇开关状态
//        else if (strB6.equals(BaseVolume.COMMAND_TYPE_FENG_SHAN)) {
//            fengShanAnalysis(strIotID,strContent)
//        }
//        // 0x04 可用天数
//        else if (strB6.equals(BaseVolume.COMMAND_TYPE_SURPLUS_DAYS)) {
//            surpDaysAnalysis(strIotID,strContent)
//        }
//        // 0x05 精油配置参数
//        else if (strB6.equals(BaseVolume.COMMAND_TYPE_JING_YOU)) {
//            jingYouInfoAnalysis(strIotID,strContent)
//        }
//        // 0x06 工作模式设置信息
//        else if (strB6.equals(BaseVolume.COMMAND_TYPE_WORK_MODE_INFO)) {
//            workModeInfoAnalysis(strIotID,strContent)
//        }
//        // 0x07 工作档位
//        else if (strB6.equals(BaseVolume.COMMAND_TYPE_LEVEL)) {
//            levelAnalysis(strIotID,strContent)
//        }
//        // 0x08 开关机信息
//        else if (strB6.equals(BaseVolume.COMMAND_TYPE_WORK_ON_OFF)) {
//            switchAnalysis(strIotID,strContent)
//        }
//        // 0x09 设备锁状态
//        else if (strB6.equals(BaseVolume.COMMAND_TYPE_LOCK_STATE)) {
//            lockAnalysis(strIotID,strContent)
//        }
//        // 0x0a 工作状态
//        else if (strB6.equals(BaseVolume.COMMAND_TYPE_WORK_STATE)) {
//            workStateAnalysis(strIotID,strContent)
//        }
//        // 0x0b 设备报警状态
//        else if (strB6.equals(BaseVolume.COMMAND_TYPE_DEVICE_ALARM_STATE)) {
//            alarmInfoAnalysis(strIotID,strContent)
//        }
//        // 0x0c 超级指令 设备运行状态及模式信息
//        else if (strB6.equals(BaseVolume.COMMAND_TYPE_DEVICE_SUPER_STATE)) {
//            superRunStateAnalysis(strIotID,strContent)
//        }
//
//        // 广播发送给上层
//        con?.sendBroadcast(Intent(strB6).putExtra(BaseVolume.DEVICE_IOT_ID,strIotID))
//
//    }
//
//
//    // 0x00 恢复出厂设置
//
//    /** 0x01 主机信息 */
//    fun deviceInfoAnalysis(strIotID:String,strContent:String) {
//
//        val iType = Integer.parseInt(strContent.substring(0,2), 16)
//        var name = strContent.substring(2)
//        if (name.equals("")) {
//            name = "GRS_"+String.format("%02d", iType)
//        }
//        else {
//            try {
//                name = String(NetworkUtils.hexStringToBytes(name))
//            } catch (e: UnsupportedEncodingException) {
//                e.printStackTrace()
//            }
//        }
//
//        deviceStateHashMap[strIotID]?.iType = iType
//        deviceStateHashMap[strIotID]?.strName = name
//    }
//
//    /** 0x02 主机时间 */
//    fun timeInfoAnalysis(strIotID: String,strContent: String) {
//        val strYear1 = strContent.substring(0, 4)
//        var strMonth = strContent.substring(4, 6)
//        var strDay = strContent.substring(6, 8)
//        var strHour = strContent.substring(8, 10)
//        var strMinute = strContent.substring(10, 12)
//        var strSecond = strContent.substring(12, 14)
//        var strWeek = strContent.substring(14, 16)
//
//        val strYear = String.format("%02d", Integer.parseInt(strYear1, 16))
//        strMonth = String.format("%02d", Integer.parseInt(strMonth, 16))
//        strDay = String.format("%02d", Integer.parseInt(strDay, 16))
//        strHour = String.format("%02d", Integer.parseInt(strHour, 16))
//        strMinute = String.format("%02d", Integer.parseInt(strMinute, 16))
//        strSecond = String.format("%02d", Integer.parseInt(strSecond, 16))
//        strWeek = getStrWeekByNumber(strWeek)
//
//        val strTimeInfo = "$strYear-$strMonth-$strDay $strHour:$strMinute:$strSecond"
//        deviceStateHashMap[strIotID]?.iSecond = strSecond.toInt()
//        deviceStateHashMap[strIotID]?.strTimeInfo = strTimeInfo
//        deviceStateHashMap[strIotID]?.strTime = "$strHour:$strMinute"
//        deviceStateHashMap[strIotID]?.strWeek = strWeek
//
//
//    }
//
//    /** 0x03 风扇开关状态 */
//    fun fengShanAnalysis(strIotID: String,strContent: String) {
//        val iState = Integer.parseInt(strContent, 16)
//        val isOpen = (iState == 1)
//        deviceStateHashMap[strIotID]?.isFengShan = isOpen
//    }
//
//    /** 0x04 可用天数 */
//    fun surpDaysAnalysis(strIotID: String,strContent: String) {
//        val iDays = Integer.parseInt(strContent, 16)
//        deviceStateHashMap[strIotID]?.iSurpDays = iDays
//    }
//
//    /** 0x05 精油配置参数 */
//    fun jingYouInfoAnalysis(strIotID: String,strContent: String) {
//        val strMax = strContent.substring(0,4)
//        val iMaxValue = Integer.parseInt(strMax, 16)
//        val strNow = strContent.substring(4,8)
//        val iNowValue = Integer.parseInt(strNow, 16)
//        val strSpeed = strContent.substring(8,10)
//        val iSpeedValue = Integer.parseInt(strSpeed, 16)
//
//        deviceStateHashMap[strIotID]?.iJingYouMaxValue = iMaxValue
//        deviceStateHashMap[strIotID]?.iJingYouNowValue = iNowValue
//        deviceStateHashMap[strIotID]?.iJingYouSpeed = iSpeedValue/10.0
//
//    }
//
//    /** 0x06 工作模式设置信息 */
//    fun workModeInfoAnalysis(strIotID: String,strContent: String) {
//        val iCount = strContent.length / 2 / 11
//        for (i in 0 until iCount) {
//            val iBaseN = i * 22
//            val iModeNumber = Integer.parseInt(strContent.substring(iBaseN, iBaseN + 2), 16)
//            val strWeek = NetworkUtils.hexString2binaryString(strContent.substring(iBaseN + 2, iBaseN + 4))
//            val iStartH = Integer.parseInt(strContent.substring(iBaseN + 4, iBaseN + 6), 16)
//            val iStartM = Integer.parseInt(strContent.substring(iBaseN + 6, iBaseN + 8), 16)
//            val iEndH = Integer.parseInt(strContent.substring(iBaseN + 8, iBaseN + 10), 16)
//            val iEndM = Integer.parseInt(strContent.substring(iBaseN + 10, iBaseN + 12), 16)
//            val iAction = Integer.parseInt(strContent.substring(iBaseN + 12, iBaseN + 14), 16)
//            val iRunTime = Integer.parseInt(strContent.substring(iBaseN + 14, iBaseN + 18), 16)
//            val iPauseTime = Integer.parseInt(strContent.substring(iBaseN + 18, iBaseN + 22), 16)
//            val isEnable = iAction != 0
//
//            val workModeInfo = WorkModeInfoCache(iModeNumber-1)
//            workModeInfo.strWeek = strWeek
//            workModeInfo.strStartHour = String.format("%02d", iStartH)
//            workModeInfo.strStartMinute = String.format("%02d", iStartM)
//            workModeInfo.strStopHour = String.format("%02d", iEndH)
//            workModeInfo.strStopMinute = String.format("%02d", iEndM)
//            workModeInfo.isEnable = isEnable
//            workModeInfo.strRunTime = String.format("%02d", iRunTime)
//            workModeInfo.strPauseTime = String.format("%02d", iPauseTime)
//            workModeInfo.setmContext(con)
//
//            deviceStateHashMap[strIotID]?.workModeList!![iModeNumber-1] = workModeInfo
//
//        }
//
//    }
//
//    /** 0x07 工作档位 */
//    fun levelAnalysis(strIotID: String,strContent: String) {
//        val iLevel = Integer.parseInt(strContent, 16)
//        deviceStateHashMap[strIotID]?.isLevel = iLevel
//    }
//
//    /** 0x08 开关机信息 */
//    fun switchAnalysis(strIotID: String,strContent: String) {
//        val iState = Integer.parseInt(strContent, 16)
//        val isOpen = (iState == 1)
//        deviceStateHashMap[strIotID]?.isSwitch = isOpen
//    }
//
//    /** 0x09 设备锁状态 */
//    fun lockAnalysis(strIotID: String,strContent: String) {
//        val iState = Integer.parseInt(strContent, 16)
//        val isOpen = (iState == 1)
//        deviceStateHashMap[strIotID]?.isLock = isOpen
//    }
//
//    /** 0x0a 工作状态 */
//    fun workStateAnalysis(strIotID: String,strContent: String) {
//        var strMode = strContent.substring(0,2)
//        val iMode = Integer.parseInt(strMode, 16)
//        var strWeek = strContent.substring(2,4)
//        strWeek = getStrWeekByNumber(strWeek)
//        val iStartH = Integer.parseInt(strContent.substring(4,6), 16)
//        val iStartM = Integer.parseInt(strContent.substring(6,8), 16)
//        val iEndH = Integer.parseInt(strContent.substring(8,10), 16)
//        val iEndM = Integer.parseInt(strContent.substring(10,12), 16)
//        val iRunTime = Integer.parseInt(strContent.substring(12,16), 16)
//        val iPauseTime = Integer.parseInt(strContent.substring(16,20), 16)
//
//        var strModeNumber = ""
//        if (iMode == 0) {
//            strModeNumber = con?.getString(R.string.wei_kaiqi)!!
//        }else if (iMode == 1) {
//            strModeNumber = con?.getString(R.string.moshi_1)!!
//        } else if (iMode == 2) {
//            strModeNumber = con?.getString(R.string.moshi_2)!!
//        } else if (iMode == 3) {
//            strModeNumber = con?.getString(R.string.moshi_3)!!
//        } else if (iMode == 4) {
//            strModeNumber = con?.getString(R.string.moshi_4)!!
//        } else if (iMode == 5) {
//            strModeNumber = con?.getString(R.string.moshi_5)!!
//        }
//
//        deviceStateHashMap[strIotID]?.strNowWorkModel = strModeNumber
//        deviceStateHashMap[strIotID]?.strNowWorkWeek = strWeek
//        if (iMode == 0) {
//            deviceStateHashMap[strIotID]?.strNowWorkStartTime = "--"
//            deviceStateHashMap[strIotID]?.strNowWorkStopTime = "--"
//            deviceStateHashMap[strIotID]?.strRunTime = "--"
//            deviceStateHashMap[strIotID]?.strPauseTime = "--"
//        }
//        else {
//            deviceStateHashMap[strIotID]?.strNowWorkStartTime = String.format("%02d", iStartH)+":"+String.format("%02d", iStartM)
//            deviceStateHashMap[strIotID]?.strNowWorkStopTime = String.format("%02d", iEndH)+":"+String.format("%02d", iEndM)
//            deviceStateHashMap[strIotID]?.strRunTime = String.format("%02d", iRunTime)
//            deviceStateHashMap[strIotID]?.strPauseTime = String.format("%02d", iPauseTime)
//        }
//
//
//    }
//
//    /** 0x0b 设备报警状态 */
//    fun alarmInfoAnalysis(strIotID: String,strContent: String) {
//        val iWuHua = Integer.parseInt(strContent.substring(0,2), 16)
//        val iQiBeng = Integer.parseInt(strContent.substring(2,4), 16)
//        val iFengJi = Integer.parseInt(strContent.substring(4,6), 16)
//        val iDianYa = Integer.parseInt(strContent.substring(6,8), 16)
//
//        deviceStateHashMap[strIotID]?.isAlarmWuHua = (iWuHua == 1)
//        deviceStateHashMap[strIotID]?.isAlarmQiBeng = (iQiBeng == 1)
//        deviceStateHashMap[strIotID]?.isAlarmFengJi = (iFengJi == 1)
//        deviceStateHashMap[strIotID]?.isAlarmDianYa = (iDianYa == 1)
//
//    }
//
//    /**
//     * 根据code解析报警描述信息
//     */
//    fun getAlarmInfoByCode(strErrorCode:String):String{
//        val iWuHua = Integer.parseInt(strErrorCode.substring(0,2), 16)
//        val iQiBeng = Integer.parseInt(strErrorCode.substring(2,4), 16)
//        val iFengJi = Integer.parseInt(strErrorCode.substring(4,6), 16)
//        val iDianYa = Integer.parseInt(strErrorCode.substring(6,8), 16)
//        var strAlarmInfo = ""
//        if (iWuHua == 1) {
//            strAlarmInfo += con?.getString(R.string.wu_hua)+","
//        }
//        if (iQiBeng == 1) {
//            strAlarmInfo += con?.getString(R.string.qi_beng)+","
//        }
//        if (iFengJi == 1) {
//            strAlarmInfo += con?.getString(R.string.feng_ji)+","
//        }
//        if (iDianYa == 1) {
//            strAlarmInfo += con?.getString(R.string.dian_ya)+","
//        }
//        strAlarmInfo = strAlarmInfo.substring(0,strAlarmInfo.length - 1)
//        strAlarmInfo += con?.getString(R.string.yi_chang_)
//
//        return strAlarmInfo
//    }
//
//    /** 0x0c 超级指令 设备运行状态及模式信息 */
//    fun superRunStateAnalysis(strIotID: String,strContent: String) {
//        // 设备时间
//        val strTimeInfo = strContent.substring(0,16)
//        timeInfoAnalysis(strIotID,strTimeInfo)
//        // 风扇状态
//        val strFengShan = strContent.substring(16,18)
//        fengShanAnalysis(strIotID,strFengShan)
//        // 剩余天数
//        val strDays = strContent.substring(18,22)
//        surpDaysAnalysis(strIotID,strDays)
//        // 精油状态
//        val strJingYou = strContent.substring(22,32)
//        jingYouInfoAnalysis(strIotID,strJingYou)
//        // 工作状态
//        val strWorkInfo = strContent.substring(32,52)
//        workStateAnalysis(strIotID,strWorkInfo)
//        // 档位
//        val strLevel = strContent.substring(52,54)
//        levelAnalysis(strIotID,strLevel)
//        // 开关机状态
//        val strSwitch = strContent.substring(54,56)
//        switchAnalysis(strIotID,strSwitch)
//        // 设备锁状态
//        val strLock = strContent.substring(56,58)
//        lockAnalysis(strIotID,strLock)
//        // 报警状态
//        val strAlarm = strContent.substring(58,66)
//        alarmInfoAnalysis(strIotID,strAlarm)
//    }
//
//
//
//
//    fun getStrWeekByNumber(strNumber:String) : String {
//        var strWeek = ""
//        when(strNumber.toInt()) {
//            1 -> strWeek = con?.getString(R.string.zhou_1)!!
//            2 -> strWeek = con?.getString(R.string.zhou_2)!!
//            3 -> strWeek = con?.getString(R.string.zhou_3)!!
//            4 -> strWeek = con?.getString(R.string.zhou_4)!!
//            5 -> strWeek = con?.getString(R.string.zhou_5)!!
//            6 -> strWeek = con?.getString(R.string.zhou_6)!!
//            7 -> strWeek = con?.getString(R.string.zhou_7)!!
//        }
//
//
//        return strWeek
//    }

}
