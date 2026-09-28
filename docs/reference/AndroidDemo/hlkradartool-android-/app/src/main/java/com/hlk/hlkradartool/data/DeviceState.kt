package com.hlk.hlkradartool.data

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import com.hlk.hlkradartool.activity.DemoApplication
import com.hlk.hlkradartool.util.BaseVolume
import com.hlk.hlkradartool.util.Utils
import com.inuker.bluetooth.library.utils.ByteUtils
import org.json.JSONObject
import java.io.Serializable
import java.util.*
import kotlin.collections.ArrayList

class DeviceState : Serializable{
    private val TAG = DeviceState::class.java.simpleName

    lateinit var mContext:Context
    var strMAC = ""
    // 最大距离门
    var iMaxDoorNum = 0
    // 最小距离门
    var iMinDoorNum = 0
    // 当前配置的最大运动距离门
    var iSportDoorNum = 0
    // 当前配置的最大静态距离门
    var iStaticDoorNum = 0
    // 运动距离门的灵敏度
    var sportDoorSPLList = arrayListOf<Int>()
    // 静止距离门的灵敏度
    var staticDoorSPLList = arrayListOf<Int>()
    // 无人持续时间
    var iKeepTime = 0
    // 固件类型
    var strFirmwareType = ""
    // 固件版本号
    var strFirmwareVer = ""
    // 距离门分辨率
    var strDoorDPI = BaseVolume.CMD_TYPE_DOOR_DPI_075

    // 上报数据
    // 目标状态
    var iPushTargetState = 0
    // 运动目标距离
    var iPushSportTargetLength = 0
    // 运动目标能量
    var iPushSportTargetCapacity = 0
    // 静止目标距离
    var iPushStaticTargetLength = 0
    // 静止目标能量
    var iPushStaticTargetCapacity = 0
    // 探测距离
    var iPushSearchLength = 0
    // 是否处于工程模式
    var isProjectEnable = false
    // 工程模式下主动上报的最大运动距离门
    var iPushSportDoorNumByProject = 0
    // 工程模式下主动上报的最大静止距离门
    var iPushStaticDoorNumByProject = 0
    // 工程模式下运动距离门能量值
    var pushSportCapacityListByProject = arrayListOf<Int>()
    // 工程模式下静止距离门能量值
    var pushStatiCapacityListByProject = arrayListOf<Int>()
    // 额外数据
    var strPushMoreHexDataByProject = ""
    //检测到的光敏值
    var photosensitive = 0
    //光敏状态
    var photosensitiveState = 0
    //光敏阈值
    var photosensitiveThresholdValue = 0
    //检测到的电频状态
    var frequent = ""
    //上门限值
    var upperLimit = 0
    //下门限值
    var lowerLimit = 0
    //门限值状态
    var thresholdState = false
    //主动上报门限距离结果
    var initiativeThresholdState = false


    /***************************************2411s用到的属性****************************************/
    //类型
    var type2411s = 0
    //距离
    var distance2411s = 0
    //最远运动范围
    var maxRangeMotion = 0
    //最近运动范围
    var minRangeMotion = 0
    //最远微动范围
    var maxTinyMotion = 0
    //最近微动范围
    var minTinyMotion = 0
    // 光感值
    var LPValue = 0

    /***************************************2412用到的属性*****************************************/
    // 最大距离门
//    var maxDistanceDoor = 0
    // 最小距离门
//    var minDistanceDoor = 0
    // 无人持续时间
//    var nobodyContinueTime = 0
    // 有人存在输出out的极性（0 false 有人输出高电平，1 true 有人输出低电平）
    var existsPersonConfig = ""
    // 距离门
    var distanceDoor = 0.75
    var isReadDistanceDoor = false
    // 运动灵敏度
    //    var runSensitivityList = ArrayList<Int>()
    //    // 静止灵敏度
    //    var staticSensitivityList = ArrayList<Int>()

    /***************************************2450用到的属性****************************************/
    // 是否是单目标追踪
    var isMultipleTarget = false
    // 区域坐标原始数据段,只保留最新的坐标
    var filtrationStr = ""
    // 区域过滤模式  0代表不过滤，1代表仅检测矩形区域，2代表检测除了矩形区域外的所有区域
    var filtrationMode = -1
    // 日志开关
    var isrizhiopen = false
    // 日志开关
    var isrizhiclean = false
    // 日志开关
    var isrizhilengh = 0
    // 日志数据
    var isrizhidata = arrayListOf<String>()

    /***************************************LD6002用到的属性****************************************/
    var hasBodyList = arrayListOf<Boolean>()            // 记录四个区域是否有人
    var interferenceAreaList = arrayListOf<AreaCoordinateBean>()    // 记录干扰区域,有四个区域；每个区域有x,y,z的最大、最小坐标值，共6个值
    var monitoringAreaList = arrayListOf<AreaCoordinateBean>()    // 记录监控区域,有四个区域；每个区域有x,y,z的最大、最小坐标值，共6个值
    var delayTime = 0                                           // 延时时间
    var detectionSensitivityState = 0                           // 上报检测灵敏度，0：低、1：中、2：高
    var triggerSpeed = 0                                        // 触发速度状态 0：慢、1：中、2：快
    var zAxisMinRange = 0                                       // z轴最小值
    var zAxisMaxRange = 0                                       // z轴最大值
    var installation = 0                                        // 安装方式 0：顶装 1：侧装
    var lowPowerMode = 0                                        // 低功耗模式 0：关闭 1：开启
    var sleepTime = 0                                           // 无人时低功耗模式睡眠时间
    var workMode = 0                                            // 工作模式 0:无人低功耗模式 1:正常模式。
    var targetNum = 0                                           // 目标人数
    var targetLocateList = arrayListOf<PersonnelLocationBean>()    // 目标位置数据

    var projectName = ""                                        // 项目名称
    var firmwareVersion = ""                                    // 雷达固件版本号（不是蓝牙固件版本号，两者是分开的）

    /**
     * 人员位置数据类型
     */
    class PersonnelLocationBean{
        var x = 0f
        var y = 0f
        var z = 0f
        var dopIDX = 0
        var clusterID = 0
    }

    /**
     * 区域数据类型
     */
    class AreaCoordinateBean{
        var xMin = 0f
        var xMax = 0f
        var yMin = 0f
        var yMax = 0f
        var zMin = 0f
        var zMax = 0f
    }

    /********************************************************************************************/


    constructor(strMac:String,mContext:Context) {
        this.mContext = mContext
        this.strMAC = strMac

    }

    fun getPhotosensitiveStateHttp():String{
        return if (photosensitiveState == 1){
            "低于"
        }else if (photosensitiveState == 2){
            "高于"
        }else{
            "关闭"
        }
    }

    fun getFrequentHttp():String{
        return if (frequent == "01"){
            "高电平"
        }else{
            "低电平"
        }
    }

    fun getStrDoorDPIHttp():String{
        return if (strDoorDPI == "0100"){
            "0.2"
        }else{
            "0.75"
        }
    }

    /** 解析读取参数 */
    public fun analysisReadParameter(strFullData : String) {
        val strReceiveData = strFullData.substring(22,strFullData.length - 8)
        val iMaxFromDoorNum = strReceiveData.substring(0,2).toInt(16)
        val iSportFromDoorNum = strReceiveData.substring(2,4).toInt(16)
        val iStaticFromDoorNum = strReceiveData.substring(4,6).toInt(16)
        sportDoorSPLList.clear()
        staticDoorSPLList.clear()
        // 距离门数量固定为8：实际数量0,1,2,3,4,5,6,7,8，共9个
        for (iN in 0 .. iMaxFromDoorNum) {//此处循环的的最大值
            val iSportC = strReceiveData.substring(6+(iN*2),6+((iN+1)*2)).toInt(16)
            sportDoorSPLList.add(iSportC)

            Log.e("TAG", "analysisReadParameter: dhjlkasmzx"+ iMaxFromDoorNum+ "   " + iSportC)
        }
        val iStaticSLPPosition = 6+((iMaxFromDoorNum+1)*2)
        for (iN in 0 .. iMaxFromDoorNum) {
            val iStaticC = strReceiveData.substring(iStaticSLPPosition+(iN*2),iStaticSLPPosition+((iN+1)*2)).toInt(16)
            staticDoorSPLList.add(iStaticC)
        }

        val strKeepTimeHex = strReceiveData.substring(6+(iMaxFromDoorNum+1)*4)
        val iKeepT = Utils.reversalHex(strKeepTimeHex).toInt(16)
        this.iMaxDoorNum = iMaxFromDoorNum
        this.iSportDoorNum = iSportFromDoorNum
        this.iStaticDoorNum = iStaticFromDoorNum
        this.iKeepTime = iKeepT

    }

    /** 解析读取固件信息 */
    public fun analysisReadFirmwareInfo(strFullData : String) {
        val strReceiveData = strFullData.substring(20,strFullData.length - 8)
        var strFirmwareHex = strReceiveData.substring(0,4)
        var strFirstHex = strReceiveData.substring(4,8)
        var strSecondHex = strReceiveData.substring(8)
        strFirmwareHex = Utils.reversalHex(strFirmwareHex)
        strFirstHex = Utils.reversalHex(strFirstHex)
        strSecondHex = Utils.reversalHex(strSecondHex)
        val strLeft = strFirstHex.substring(0,2).toInt()
        val strMiddle = strFirstHex.substring(2,4)
        this.strFirmwareType = strFirmwareHex
        this.strFirmwareVer = "${strLeft}.${strMiddle}.${strSecondHex}"

    }

    /** 解析距离门分辨率 */
    public fun analysisReadDoorDPI(strFullData : String) {
        val strReceiveData = strFullData.substring(20,strFullData.length - 8)
        this.strDoorDPI = strReceiveData
    }

    /** 解析光敏配置 */
    public fun analysisPhotosensitive(strFullData : String) {
        val strPhotosensitiveState = strFullData.substring(20,22).toInt(16)
        val strPhotosensitiveThresholdValue = strFullData.substring(22,24).toInt(16)
        this.photosensitiveState = strPhotosensitiveState
        this.photosensitiveThresholdValue = strPhotosensitiveThresholdValue;
        if (Utils.contrastVersion("2.01.23021610", DemoApplication.getInstance().nowSelectDevice.strVerInfo)) {
            frequent = strFullData.substring(24,26)
            Log.e("TAG", "analysisAutoData: 主动上报的电频状态"+ frequent)
        }
    }

    /** 判断是否支持设置分辨率 */
//    public fun isSupportSetDPI() : Boolean {
//        // 1.02.22073015
//        val fFirmwareVer = strFirmwareVer.substring(0,strFirmwareVer.lastIndexOf(".")).toFloat()
//        return (fFirmwareVer > 1.02) // 主版本1.02以后的版本才支持分辨率调整
//    }

    /** 解析主动上报的数据 */
    public fun analysisAutoData(strFullData : String) {
        val strReceiveData = strFullData.substring(12,strFullData.length - 8)
        val strType = strReceiveData.substring(0,2)
        val strValid = strReceiveData.substring(4,strReceiveData.length - 4)
        iPushTargetState = strValid.substring(0,2).toInt(16)
        // 考虑到大小端，2个字节及以上，需要反转
        iPushSportTargetLength = Utils.reversalHex(strValid.substring(2,6)).toInt(16)
        iPushSportTargetCapacity = strValid.substring(6,8).toInt(16)
        iPushStaticTargetLength = Utils.reversalHex(strValid.substring(8,12)).toInt(16)
        iPushStaticTargetCapacity = strValid.substring(12,14).toInt(16)
        iPushSearchLength = Utils.reversalHex(strValid.substring(14,18)).toInt(16)
        isProjectEnable = false
        // 工程模式
        if (strType.equals("01")) {
            isProjectEnable = true
            iPushSportDoorNumByProject = strValid.substring(18,20).toInt(16)
            iPushStaticDoorNumByProject = strValid.substring(20,22).toInt(16)

            Log.e("TAG", "analysisAutoData: 主动上报的hex:"+strFullData)
            if (Utils.contrastVersion("2.01.23020208", DemoApplication.getInstance().nowSelectDevice.strVerInfo)) {
                val photosensitiveHex = strValid.substring(58,60)
//                val photosensitiveHex = strValid.substring(strValid.length - 4,strValid.length-2)
                photosensitive = photosensitiveHex.toInt(16)
                Log.e("TAG", "analysisAutoData: 主动上报的光敏值"+ photosensitive+",hex:"+photosensitiveHex)
            }


            pushSportCapacityListByProject.clear()
            pushStatiCapacityListByProject.clear()

            // 距离门数量固定为8：实际数量0,1,2,3,4,5,6,7,8，共9个
            for (iN in 0 .. iPushSportDoorNumByProject) {
                val iSportC = strReceiveData.substring(26+(iN*2),26+((iN+1)*2)).toInt(16)
                pushSportCapacityListByProject.add(iSportC)
            }
            val iStaticSLPPosition = 26+((iPushSportDoorNumByProject+1)*2)
            for (iN in 0 .. iPushSportDoorNumByProject) {
                val iStaticC = strReceiveData.substring(iStaticSLPPosition+(iN*2),iStaticSLPPosition+((iN+1)*2)).toInt(16)
                pushStatiCapacityListByProject.add(iStaticC)
            }
            strPushMoreHexDataByProject = strValid.substring(26+(iPushSportDoorNumByProject+1)*4)
            Log.e("TAG", "analysisAutoData: 工程模式，完整数据"+ strFullData)
        }else{
            if (pushSportCapacityListByProject.size != 0){
                pushSportCapacityListByProject.clear()
            }
            if (pushStatiCapacityListByProject.size != 0){
                pushStatiCapacityListByProject.clear()
            }
        }

    }

    /** 解析2412主动上报的数据 */
    public fun analysis2412AutoData(strFullData : String) {
        val strReceiveData = strFullData.substring(12,strFullData.length - 12)       // 去掉前边12个（帧头，数据长度）和12个（校验位，帧尾），得到帧内数据
        Log.e(TAG, "解析2412主动上报的数据000: "+ strReceiveData )
        val strType = strReceiveData.substring(0,2)                                  // 拿到该条数据的类型
        val strValid = strReceiveData.substring(4)                          // 拿到目标数据（这个才是真正能用的数据）
        Log.e(TAG, "解析2412主动上报的数据111: "+ strValid )
        iPushTargetState = strValid.substring(0,2).toInt(16)                                // 目标状态
        // 考虑到大小端，2个字节及以上，需要反转
        iPushSportTargetLength = Utils.reversalHex(strValid.substring(2,6)).toInt(16)       // 运动目标距离
        iPushSportTargetCapacity = strValid.substring(6,8).toInt(16)                        // 运动目标能量值
        iPushStaticTargetLength = Utils.reversalHex(strValid.substring(8,12)).toInt(16)     // 静止目标距离
        iPushStaticTargetCapacity = strValid.substring(12,14).toInt(16)                     // 静止目标能量值
        isProjectEnable = false
        // 工程模式
        if (strType.equals("01")) {
            isProjectEnable = true
//            iPushSearchLength = Utils.reversalHex(strValid.substring(14,18)).toInt(16)
            iPushSportDoorNumByProject = strValid.substring(14,16).toInt(16)
            iPushStaticDoorNumByProject = strValid.substring(16,18).toInt(16)
            Log.e(TAG, "解析2412主动上报的数据222: "+ strValid.substring(18) )
            pushSportCapacityListByProject.clear()
            pushStatiCapacityListByProject.clear()

            // 2412距离门数量固定为14：实际数量0,1,2,3,4,5,6,7,8,9,10,11,12,13共14个
            // 这里的18是排除掉前边已经解析的数据
            // for循环这里从1开始是因为下标第0个距离门数据不要了，所以从1开始
            val stringBuilder1 = StringBuilder()
            for (iN in 0 until  14) {
                val iSportC = strValid.substring(18+(iN*2),18+((iN+1)*2)).toInt(16)
                pushSportCapacityListByProject.add(iSportC)
                stringBuilder1.append(iSportC)
            }
            // 这里加个0是为了让x轴能够显示出14，看得更直观
//            pushSportCapacityListByProject.add(0)

            // 18(前边已经解析过的数据） + 14*2 （前边已经解析过的运动能量值）
            val iStaticSLPPosition = 18 + (14 * 2)
            val stringBuilder2 = StringBuilder()
            for (iN in 0 until 14) {
                val iStaticC = strValid.substring(iStaticSLPPosition+(iN*2),iStaticSLPPosition+((iN+1)*2)).toInt(16)
                pushStatiCapacityListByProject.add(iStaticC)
                stringBuilder2.append(iStaticC)
            }
            // 这里加个0是为了让x轴能够显示出14，看得更直观
//            pushStatiCapacityListByProject.add(0)
            // 如果是大于这个版本，就多了一个光感值
            if (Utils.contrastVersion("1.18.24052016", DemoApplication.getInstance().nowSelectDevice.strVerInfo)){
                val guanggan = iStaticSLPPosition + (14 * 2)
                LPValue = strValid.substring(guanggan,guanggan + 2).toInt(16)
            }
        }else{
            if (pushSportCapacityListByProject.size != 0){
                pushSportCapacityListByProject.clear()
            }
            if (pushStatiCapacityListByProject.size != 0){
                pushStatiCapacityListByProject.clear()
            }
        }


    }

    public fun autoPushToString(): String {
        return "DeviceState(strMAC='$strMAC', iPushTargetState=$iPushTargetState, iPushSportTargetLength=$iPushSportTargetLength, iPushSportTargetCapacity=$iPushSportTargetCapacity, iPushStaticTargetLength=$iPushStaticTargetLength, iPushStaticTargetCapacity=$iPushStaticTargetCapacity, iPushSearchLength=$iPushSearchLength, iPushSportDoorNumByProject=$iPushSportDoorNumByProject, iPushStaticDoorNumByProject=$iPushStaticDoorNumByProject, pushSportCapacityListByProject=${eachPushSportSPLList()}, pushStatiCapacityListByProject=${eachPushStaticSPLList()}, strPushMoreHexDataByProject='$strPushMoreHexDataByProject')"
    }

    /** 遍历上报的运动信号值列表 */
    private fun eachPushSportSPLList() : String {
        var iSportN = 0
        var jsObjSportSPL = JSONObject()
        pushSportCapacityListByProject.forEach {
            jsObjSportSPL.put("$iSportN","$it")
            ++iSportN
        }
        return jsObjSportSPL.toString()
    }
    /** 遍历设置的静止信号值列表 */
    private fun eachPushStaticSPLList() : String {
        var iStaticN = 0
        var jsObjStaticSPL = JSONObject()
        pushStatiCapacityListByProject.forEach {
            jsObjStaticSPL.put("$iStaticN","$it")
            ++iStaticN
        }
        return jsObjStaticSPL.toString()
    }

    /** 遍历设置的运动灵敏度列表 */
    private fun eachSetSportSPLList() : String {
        var iSportN = 0
        var jsObjSportSPL = JSONObject()
        sportDoorSPLList.forEach {
            jsObjSportSPL.put("$iSportN","$it")
            ++iSportN
        }
        return jsObjSportSPL.toString()
    }
    /** 遍历设置的静止灵敏度列表 */
    private fun eachSetStaticSPLList() : String {
        var iStaticN = 0
        var jsObjStaticSPL = JSONObject()
        staticDoorSPLList.forEach {
            jsObjStaticSPL.put("$iStaticN","$it")
            ++iStaticN
        }
        return jsObjStaticSPL.toString()
    }

//    override fun toString(): String {
//        return "DeviceState(strMAC='$strMAC', iMaxDoorNum=$iMaxDoorNum, iSportDoorNum=$iSportDoorNum, iStaticDoorNum=$iStaticDoorNum, sportDoorSPLList=${eachSetSportSPLList()}, staticDoorSPLList=${eachSetStaticSPLList()}, iKeepTime=$iKeepTime, strFirmwareType='$strFirmwareType', strFirmwareVer='$strFirmwareVer', iPushTargetState=$iPushTargetState, iPushSportTargetLength=$iPushSportTargetLength, iPushSportTargetCapacity=$iPushSportTargetCapacity, iPushStaticTargetLength=$iPushStaticTargetLength, iPushStaticTargetCapacity=$iPushStaticTargetCapacity, iPushSearchLength=$iPushSearchLength, iPushSportDoorNumByProject=$iPushSportDoorNumByProject, iPushStaticDoorNumByProject=$iPushStaticDoorNumByProject, pushSportCapacityListByProject=$pushSportCapacityListByProject, pushStatiCapacityListByProject=$pushStatiCapacityListByProject, strPushMoreHexDataByProject='$strPushMoreHexDataByProject')"
//    }



    /** 解析查询门限的数据的数据 */
    public fun analysisThresholdData(strFullData : String) {
        upperLimit = Math.round((strFullData.substring(16,18).toInt(16)/10f)).toInt()
        lowerLimit = Math.round((strFullData.substring(20,22).toInt(16)/10f)).toInt()
    }


    /** 解析2411读取参数的数据 */
    public fun analysisReadData2411s(strData : String){
        if(strData.length<40){
            return
        }
        var strMaxRangeMotion = strData.substring(22,24) + strData.substring(20,22)
        maxRangeMotion = strMaxRangeMotion.toInt(16)
        var strMinRangeMotion = strData.substring(26,28) + strData.substring(24,26)
        minRangeMotion = strMinRangeMotion.toInt(16)
        var strMaxTinyMotion = strData.substring(30,32) + strData.substring(28,30)
        maxTinyMotion = strMaxTinyMotion.toInt(16)
        var strMinTinyMotion = strData.substring(34,36) + strData.substring(32,34)
        minTinyMotion = strMinTinyMotion.toInt(16)
        var strKeepTime = strData.substring(38,40) + strData.substring(36,38)
        iKeepTime = strKeepTime.toInt(16)


//        maxRangeMotion = strData.substring(20,24).toInt(16)
//        minRangeMotion = strData.substring(24,28).toInt(16)
//        maxTinyMotion = strData.substring(28,32).toInt(16)
//        minTinyMotion = strData.substring(32,36).toInt(16)
//        iKeepTime = strData.substring(36,40).toInt(16)
    }

    /**
     * 解析2412基础参数
     */
    fun analysisBaseData2412(strData : String){
        iSportDoorNum = strData.substring(0,2).toInt(16)
//        if (strData.substring(2,4).toInt(16) <= 13){
//            iStaticDoorNum = strData.substring(2,4).toInt(16)
//        }else {
//            iStaticDoorNum = 13
//        }
        // 这里减1是因为实际最大距离门是14，但是显示的是要13，0号距离门是不算，所以这里需要减去0号距离门
        iStaticDoorNum = strData.substring(2,4).toInt(16) - 1
        iKeepTime = ByteUtils.reverseHex(strData.substring(4,8)).toInt(16)
        existsPersonConfig = strData.substring(8,10)
    }

    /**
     * 解析运动灵敏度
     */
    fun analysisRunSensitivity(strData : String){
        sportDoorSPLList.clear()
        for (i in 0 until strData.length/2){
            sportDoorSPLList.add(strData.substring(i*2,(i+1)*2).toInt(16))
        }
        Log.e(TAG, "analysisRunSensitivity: " + Gson().toJson(sportDoorSPLList) )
    }

    /**
     * 解析静止灵敏度
     */
    fun analysisStaticSensitivity(strData : String){
        staticDoorSPLList.clear()
        for (i in 0 until strData.length/2){
            staticDoorSPLList.add(strData.substring(i*2,(i+1)*2).toInt(16))
        }
    }

    /**
     * 解析2412距离门
     */
    fun analysisDistanceDoorNum(strData : String){
        // 0100 是小端格式，实际上此功能只有前边两个字符才有用，因为值最大直到4
        val num = strData.substring(0,2).toInt()
        Log.e(TAG, "analysisDistanceDoorNum: " + strData )
        if (num == 0){
            distanceDoor = 0.75
        }else if (num == 1){
            distanceDoor = 0.5
        }else if (num == 2){
            distanceDoor = 0.3
        }else if (num == 3){
            distanceDoor = 0.2
        }else if (num == 4){
            distanceDoor = 0.1
        }
        isReadDistanceDoor = true
    }

    /**
     * 2412解析光感辅助控制功能配置查询
     */
    fun analysis2412Photosensitive(strData : String){
        photosensitiveState = strData.substring(0,2).toInt(16)
        photosensitiveThresholdValue = strData.substring(2,4).toInt(16)
    }

    // 解析区域检测模式
    fun analysisFiltration(strData:String){
        // 保存区域坐标点的原始数据，方便在切换检测模式的时候使用
        filtrationStr = strData.substring(4,strData.length)
        Log.e(TAG, "analysisFiltration: "+filtrationStr )

        val filtrationStr = strData.substring(0,4)

        filtrationMode = when (filtrationStr) {
            // 关闭区域过滤功能
            "0000" -> {
                0
            }
            // 仅检测设置的区域
            "0100" -> {
                1
            }
            // 不检测设置的区域
            "0200" -> {
                2
            }
            else -> {0}
        }
    }

    // 拼接设置
    fun analysisCoordinateStr(mode:String):String{
        return BaseVolume.CMD_TYPE_SET_FILTRATION_HEAD_2450 + mode + filtrationStr + BaseVolume.CMD_TYPE_SET_FILTRATION_FOOT_2450
    }

    // 获取解析过后的坐标点
    fun getCoordinateFiltrationMap(): MutableMap<Int, IntArray> {
        val filtrationArray :MutableMap<Int,IntArray> = HashMap()
        if (filtrationStr != "" && filtrationStr.length >= 48){
            // 三个矩形的坐标位置,每个矩形由两个互为对角的坐标构成，即（x1,y1）(x2,y2)四个参数
            val list1 = intArrayOf(
                ByteUtils.littleEndianStringToInt(filtrationStr.substring(0,4)),
                ByteUtils.littleEndianStringToInt(filtrationStr.substring(4,8)),
                ByteUtils.littleEndianStringToInt(filtrationStr.substring(8,12)),
                ByteUtils.littleEndianStringToInt(filtrationStr.substring(12,16)))
            val list2 = intArrayOf(
                ByteUtils.littleEndianStringToInt(filtrationStr.substring(16,20)),
                ByteUtils.littleEndianStringToInt(filtrationStr.substring(20,24)),
                ByteUtils.littleEndianStringToInt(filtrationStr.substring(24,28)),
                ByteUtils.littleEndianStringToInt(filtrationStr.substring(28,32)))
            val list3 = intArrayOf(
                ByteUtils.littleEndianStringToInt(filtrationStr.substring(32,36)),
                ByteUtils.littleEndianStringToInt(filtrationStr.substring(36,40)),
                ByteUtils.littleEndianStringToInt(filtrationStr.substring(40,44)),
                ByteUtils.littleEndianStringToInt(filtrationStr.substring(44,48)))

            var intString=""
            for (i in list1){
                intString+=i
            }
            Log.e(TAG, "获取解析过后的坐标点: "+intString)

            // 区域过滤坐标集合
            filtrationArray[1] = list1
            filtrationArray[2] = list2
            filtrationArray[3] = list3
        }

        return filtrationArray
    }

    fun getCoordinateFiltration(str: String): IntArray {
        // 三个矩形的坐标位置,每个矩形由两个互为对角的坐标构成，即（x1,y1）(x2,y2)四个参数
        return intArrayOf(
            ByteUtils.littleEndianStringToInt(str.substring(0, 4)),
            ByteUtils.littleEndianStringToInt(str.substring(4, 8)),
            ByteUtils.littleEndianStringToInt(str.substring(8, 12)),
            ByteUtils.littleEndianStringToInt(str.substring(12, 16))
        )
    }

    /**
     * 解析单个要设置的坐标点然后拼成指令返回，用于发给模块
     * @param array 视图的两对点的坐标数组
     * @param proportion 视图坐标与模块真实环境坐标比例
     * @param origin 半径
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun getSingleCoordinateFiltrationStringToModule(array: IntArray,proportion: Int,origin: Int):String{
        // 视图坐标转换模块真实环境坐标数据，逆推回模块的数据
        val array1 = if (array[0]<origin){ //400,/600
            0-(origin-array[0])
        }else{
            array[0]-origin
        }
        val array2 = if (array[2]<origin){
            0-(origin-array[2])
        }else{
            array[2]-origin
        }
        val intArray = intArrayOf(array1*proportion,array[1]*proportion,array2*proportion,array[3]*proportion)
        val str = ByteUtils.reverseIntsToLittleEndianString(intArray)
        return str
    }

    /**
     * 解析单个要设置的坐标点然后拼成指令返回，用于视图显示
     * @param array 视图的两对点的坐标数组
     * @param proportion 视图坐标与模块真实环境坐标比例
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun getSingleCoordinateFiltrationStringToView(array: IntArray,proportion: Int,origin: Int):String{
        val array1 = if (array[0]<origin && array[0]!=0){ //400,/600
            Log.e(TAG, "getSingleCoordinateFiltrationStringToView1: "+ (0-(origin-array[0])) )
            0-(origin-array[0])
        }else{
            Log.e(TAG, "getSingleCoordinateFiltrationStringToView2: "+array[0] )
            array[0]
        }

        val array2 = if (array[2]<origin && array[0]!=0){
            0-(origin-array[2])
        }else{
            array[2]
        }
        val intArray = intArrayOf(array1*proportion,array[1]*proportion,array2*proportion,array[3]*proportion)
        val str = ByteUtils.reverseIntsToLittleEndianString(intArray)
        return str
    }

    /**
     * 解析2450一直上报的区域检测信息
     * @param proportion 8000mm与视图总高度的比例
     * @param origin 原点的x坐标
     * @param strData 上报的完整的原始数据
     */
    fun analysisAreaCoordinate(proportion:Double,origin:Int,strData:String):MutableMap<Int,List<Double>>{
        return strData.let{
            val strArray = listOf<String>(strData.substring(8,24),strData.substring(24,40),strData.substring(40,56))
            val map = mutableMapOf<Int,List<Double>>()
            // 0000000000000000代表不检测某个目标区域或不存在这个区域
            for (i in strArray.indices) {
                if (strArray[i] != "0000000000000000") {
                    val list1 = mutableListOf<Double>()
                    // 原始数据中算出来需要在视图显示的x坐标
                    val areaCoordinateX = calculateXValue(strArray[i], proportion)
                    // 原始数据中算出来需要在视图显示的y坐标
                    val areaCoordinateY = calculateYValue(strArray[i], proportion)
                    // 原始数据中算出来的真实x坐标
                    val areaCoordinateXReal = calculateXValue(strArray[i])
                    // 原始数据中算出来的真实y坐标
                    val areaCoordinateYReal = calculateYValue(strArray[i])
                    // 原始数据中的速度值
                    val areaCoordinateSpeed = calculateSpeedValue(strArray[i])
                    // 原始数据中的分辨率（暂时用不到这个参数，先注释）
                    //                    val areaCoordinateResolutionRatio = calculateResolutionRatio(strArray[i])
                    // 计算视图需要的距离和角度
                    val areaCoordinate = calculateDistance(
                        origin.toDouble(), 0.toDouble(),
                        areaCoordinateX,
                        areaCoordinateY
                    )
                    val areaAngle = calculateAngle(
                        origin.toDouble(), 0.toDouble(),
                        areaCoordinateX,
                        areaCoordinateY
                    )
                    // 计算实际的距离和角度
                    val areaCoordinateReal = calculateDistance(
                        areaCoordinateXReal,
                        areaCoordinateYReal
                    )
                    val areaAngleReal = calculateAngle(
                        areaCoordinateXReal,
                        areaCoordinateYReal
                    )
                    // 根据视图需要的距离，角度和原点坐标计算视图上显示的坐标
                    val finalCoordinateArray = finalCoordinate(areaCoordinate, areaAngle, origin)
                    // 填充列表：1.实际距离，因为计算出来的距离单位是mm，但实际需要显示的是m，所以需要/1000
                    //         2.实际角度
                    //         3.实际速度，因为计算出来的速度单位是cm/s，而实际需要显示的是m/a，所以需要/100
                    //         4.视图x坐标
                    //         5.视图y坐标
                    list1.add(areaCoordinateReal / 1000.0)
                    list1.add(areaAngleReal)
                    list1.add(areaCoordinateSpeed / 100.0)
//                    list1.add(areaCoordinateResolutionRatio)
                    list1.add(finalCoordinateArray[0])
                    list1.add(finalCoordinateArray[1])
                    map[i + 1] = list1
                }
            }
            map
        }
    }


    /**
     * 解析2451一直上报的区域检测信息
     * @param proportion 8000mm与视图总高度的比例
     * @param origin 原点的x坐标
     * @param byteArray 上报的字节数据
     * AA AA 11 03 01 8A 28 00 3C 15 8A IE 01 3C 0F 76 5F 00 3C 0F 55 55
     * AA AA 包头
     * 11 总字节
     * 03 目标数量
     * 01  是否告警
     * 8A 28 00 3C 15
     * 8A IE 01 3C 0F
     * 76 5F 00 3C 0F
     * 55 55包尾
     */
    fun analysisAreaCoordinate2451(proportion:Double,origin:Int,strData:String,wh:Double,hg:Double):MutableMap<Int,List<Double>>{
        return strData.let{
            val number=strData.substring(6,8).toInt(16)
            var strArray= mutableListOf<String>()
            val map = mutableMapOf<Int,List<Double>>()
            if(number>0){

                for(a in 0 until  number){
                    strArray.add(strData.substring(10+a*10,10+(a+1)*10))
                }
            }else{
                return  map
            }
            //val strArray = listOf<String>(strData.substring(8,24),strData.substring(24,40),strData.substring(40,56))

            // 0000000000000000代表不检测某个目标区域或不存在这个区域
            for (i in strArray.indices) {
                if (strArray[i] != "0000000000") {
                    val list1 = mutableListOf<Double>()

                    val jiaodu=strArray[i].substring(0,2).toInt(16)-128//0x76-0x80
                    val  juli=strArray[i].substring(2,4).toInt(16)
                    val  sudu=strArray[i].substring(6,8).toInt(16)//4-6 速度方向   6-8速度值
                    val xinzao=strArray[i].substring(8,10).toInt(16)
                    val targetx=getxtovalue(jiaodu,juli, wh, hg)
                    val targety=getytovalue(jiaodu, juli, wh, hg)


                    list1.add(jiaodu.toDouble())
                    list1.add(juli.toDouble())
                    list1.add(sudu.toDouble())
                    list1.add(targetx)
                    list1.add(targety)
                    map[i + 1] = list1
                }
            }
            map
        }
    }
    /**
     * 解析2451一直上报的区域检测信息
     * @param proportion 8000mm与视图总高度的比例
     * @param origin 原点的x坐标
     * @param byteArray 上报的字节数据
     * FF FF FF FF 11 03 01 8A 28 00 3C 15 8A IE 01 3C 0F 76 5F 00 3C 0F 55 55 FF FF FF FF
     * AA AA 包头
     * 11 总字节
     * 03 目标数量
     * 01  是否告警
     * 8A 28 00 3C 15
     * 8A IE 01 3C 0F
     * 76 5F 00 3C 0F
     * 55 55包尾
     */
    fun analysisAreaCoordinate2451new(proportion:Double,origin:Int,strData:String,wh:Double,hg:Double):ArrayList<MubiaoInfo>{
        val number=strData.substring(12,14).toInt(16)
        var strArray= mutableListOf<String>()
        val map = ArrayList<MubiaoInfo>()
        if(number>0){

            for(a in 0 until  number){
                if(strData.length<16+(a+1)*10){
                    break;
                }
                strArray.add(strData.substring(16+a*10,16+(a+1)*10))
            }
        }else{
            return  map
        }
        //val strArray = listOf<String>(strData.substring(8,24),strData.substring(24,40),strData.substring(40,56))

        // 0000000000000000代表不检测某个目标区域或不存在这个区域
        for (i in strArray.indices) {
            if (strArray[i] != "0000000000") {
                val list1 = mutableListOf<Double>()

                val jiaodu=strArray[i].substring(0,2).toInt(16)-128//0x76-0x80
                val  juli=strArray[i].substring(2,4).toInt(16)
                val  sudu=strArray[i].substring(6,8).toInt(16)//4-6 速度方向   6-8速度值
                val xinzao=strArray[i].substring(8,10).toInt(16)
                val targetx=getxtovalue(jiaodu,juli, wh, hg)
                val targety=getytovalue(jiaodu, juli, wh, hg)

                var mubiaoInfo=MubiaoInfo()
                mubiaoInfo.jiaodu=jiaodu.toDouble()
                mubiaoInfo.juli=juli.toDouble()
                mubiaoInfo.sudu=sudu.toDouble()
                mubiaoInfo.targetx=targetx
                mubiaoInfo.targety=targety
                map.add(mubiaoInfo)
            }
        }
        return map


    }


    /**
     * x/w=y/h
     * 注意角度的正负
     */
    public fun  getxtovalue(jiaodu:Int,juli:Int,wh:Double,hg:Double):Double{
        var pingnuduiyingx=wh*100/hg
        var xtr=0.0
        var x=0.00

        x=juli*Math.sin(Math.PI/180 * Math.abs(jiaodu))//实际x距离
        //根据屏幕宽度算hg
        //hg=wh*100.0/x
        xtr=x/pingnuduiyingx*wh //在屏幕上的宽度

        //根据角度判断是在左还是有
        if(jiaodu>0){
            xtr=wh/2+xtr
        }else{
            xtr=wh/2-xtr
        }

        return xtr-60
    }
    /**
     * x/w=y/h
     * 注意角度的正负
     */
    public fun  getytovalue(jiaodu:Int,juli:Int,wh:Double,hg:Double):Double{
        var ytr=0.0
        var y=0.00
        y=juli*Math.cos(Math.PI/180 * jiaodu)
        ytr=y/100*hg

        return ytr
    }

    /**
     * 以下方法用于解析模块上报的区域检测数据，str传递的数据为aabb（以下数据说明均以aabb为例），即两个字节
     * 1.判断条件中比如str.substring(2, 4).toInt(16) >= 128，是用于判断一个字节坐标数据中的第二位字节，即bb是否大于128，即是否大于0x80
     *  一个坐标数据两个字节，用字符串表示为aabb，由于是小端序列，在计算过程中第一和第二个字节需要互换位置，表现为bbaa
     * 2.当bb大于128时，意味着该数值为正数，计算格式为：((aa+bb*256)-32768)/视图与实际的比例
     * 3.当bb小于128时，意味着该数值为负数，计算格式为：(0-(aa+bb*256))/视图与实际的比例
     **/
    // 解析原始数据中某个区域的需要在视图显示使用的x值
    private fun calculateXValue(str: String, proportion: Double): Double {
        return if (str.substring(2, 4).toInt(16) >= 128) {
            //X坐标
            ((str.substring(0, 2).toInt(16) + str.substring(2, 4).toInt(16) * 256) - 32768) / proportion
        } else {
            (0 - (str.substring(0, 2).toInt(16) + str.substring(2, 4).toInt(16) * 256)) / proportion
        }
    }

    // 解析原始数据中某个区域的实际的x值
    private fun calculateXValue(str: String): Double {
        return if (str.substring(2, 4).toInt(16) >= 128) {
            ((str.substring(0, 2).toInt(16) + str.substring(2, 4).toInt(16) * 256) - 32768).toDouble()
        } else {
            (0 - (str.substring(0, 2).toInt(16) + str.substring(2, 4).toInt(16) * 256)).toDouble()
        }
    }

    // 解析原始数据中某个区域的需要在视图显示使用的y值
    private fun calculateYValue(str: String, proportion: Double): Double {
        return if (str.substring(6, 8).toInt(16) >= 128) {
            ((str.substring(4, 6).toInt(16) + str.substring(6, 8).toInt(16) * 256) - 32768) / proportion
        } else {
            0 - (str.substring(4, 6).toInt(16) + str.substring(6, 8).toInt(16) * 256) / proportion
        }
    }

    // 解析原始数据中某个区域的实际的y值
    private fun calculateYValue(str: String): Double {
        return if (str.substring(6, 8).toInt(16) >= 128) {
            ((str.substring(4, 6).toInt(16) + str.substring(6, 8).toInt(16) * 256) - 32768).toDouble()
        } else {
            0 - (str.substring(4, 6).toInt(16) + str.substring(6, 8).toInt(16) * 256).toDouble()
        }
    }

    // 解析原始数据中某个区域的速度值
    private fun calculateSpeedValue(str: String): Int {
        return if (str.substring(10, 12).toInt(16) >= 128) {
            ((str.substring(8, 10).toInt(16) + str.substring(10, 12).toInt(16) * 256) - 32768)
        } else {
            0 - (str.substring(8, 10).toInt(16) + str.substring(10, 12).toInt(16) * 256)
        }
    }

    // 解析原始数据中某个区域的分辨率
    fun calculateResolutionRatio(str: String): Int {
        return if (str.substring(14, 16).toInt(16) >= 128) {
            (str.substring(12, 14).toInt(16) + str.substring(14, 16).toInt(16) * 256) - 32768
        } else {
            0 - (str.substring(12, 14).toInt(16) + str.substring(14, 16).toInt(16) * 256)
        }
    }

    // 计算两个坐标点之间的需要在视图显示使用的角度
    private fun calculateAngle(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        // 计算两点之间的角度（逆时针方向）
        val angle = Math.atan2(y2 - y1, x2 - x1)
        // 将弧度转换为角度
        return Math.toDegrees(angle)
    }

    // 计算两个坐标点之间的实际角度
    private fun calculateAngle(x: Double, y: Double): Double {
        // 计算与纵向下半轴的夹角（以度为单位）
        val angle = Math.atan2(y, x)
        // 因为这种计算方式是按照极坐标与x正轴之间的夹角算的，而实际需求是要极坐标与y正轴的之间的夹角算，所以90°减去与x正轴之间的夹角，剩下的夹角就是与y正轴的之间的夹角
        // 至于用0减去角度是因为视图和实际是旋转180°的，即视图的-x为实际的x 视图的x为实际的-x
        return 0-(90.0-Math.toDegrees(angle))
    }

    // 计算两个坐标点之间的需要在视图显示使用的距离
    private fun calculateDistance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        return Math.sqrt(Math.pow((x1 - x2), 2.0) + Math.pow((y1 - y2), 2.0));
    }

    // 计算两个坐标点之间的实际距离
    private fun calculateDistance(x: Double, y: Double): Double {
        return Math.sqrt(Math.pow(x - 0.0, 2.0) + Math.pow(y - 0.0, 2.0));
    }

    // 计算视图的最终的坐标
    private fun finalCoordinate(distance: Double, angle: Double, origin: Int): DoubleArray {
        // 将角度转换为弧度
        val angleRadians = Math.toRadians(angle)

        // 计算新点的坐标,x坐标这里需要原点坐标加上现有的坐标
        val x = origin + distance * Math.cos(angleRadians)
        val y = 0 + distance * Math.sin(angleRadians)
        println("新点的坐标为：($x, $y)");
        return doubleArrayOf(x, y)
    }

    /*********************************************下方为LD6002解析方法区***************************************************/
    /**
     * 解析四个区域分别是否有人
     */
    fun analysisHasBody(strData:String){
        hasBodyList.clear()
        for (i in 0 until strData.length / 4) {
            val strData2 = strData.substring(i * 4, (i + 1) * 4)
            if (strData2 == "0000") {
                // 代表无人
                hasBodyList.add(false)
            } else {
                hasBodyList.add(true)
            }
        }
    }

    /**
     * 解析干扰区域的坐标
     */
    fun analysisInterferenceArea(strData:String){
        interferenceAreaList.clear()
        val areaList = arrayListOf<String>()
        for (i in 0 until strData.length / 48) {
            areaList.add(strData.substring(i * 48, (i + 1) * 48))
        }
        for (i in 0 until areaList.size) {
            val bean = AreaCoordinateBean()
//            for (j in 0 until 6) {
//                val strData2 = areaList1[i].substring(j * 8, (j + 1) * 8)
//                areaList1.add((Utils.reversalHex(strData2.substring(0,4)) + Utils.reversalHex(strData2.substring(4,8))).toInt(16).toString())
//            }
            bean.xMin = Utils.hexToFloat(Utils.reversalHex(areaList[i].substring(0,8)))
            bean.xMax = Utils.hexToFloat(Utils.reversalHex(areaList[i].substring(8,16)))
            bean.yMin = Utils.hexToFloat(Utils.reversalHex(areaList[i].substring(16,24)))
            bean.yMax = Utils.hexToFloat(Utils.reversalHex(areaList[i].substring(24,32)))
            bean.zMin = Utils.hexToFloat(Utils.reversalHex(areaList[i].substring(32,40)))
            bean.zMax = Utils.hexToFloat(Utils.reversalHex(areaList[i].substring(40,48)))
            interferenceAreaList.add(bean)
        }
    }

    /**
     * 解析监控区域的坐标
     * 01 683F 0060 0A0B C8 F45B8FBF 40BFF5BD 93B63ABF DA928A3E 0000C0C0 0000C040 0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000B
     *
     * 01 0C3C 0060 0A0C A8 000040C0 00004040 00000000 00008040 0000C0C0 0000C040 000000000000000000000000000000000000C0C00000C040000000000000000000000000000000000000C0C00000C040000000000000000000000000000000000000C0C00000C040BF
     */
    fun analysisMonitoringArea(strData:String){
        monitoringAreaList.clear()
        val areaList = arrayListOf<String>()
        for (i in 0 until strData.length / 48) {
            areaList.add(strData.substring(i * 48, (i + 1) * 48))
        }
        for (i in 0 until areaList.size) {
//            val areaList1 = arrayListOf<String>()
//            for (j in 0 until 6) {
//                val strData2 = areaList1[i].substring(j * 8, (j + 1) * 8)
//                areaList1.add((Utils.reversalHex(strData2.substring(0,4)) + Utils.reversalHex(strData2.substring(4,8))).toInt(16).toString())
//            }
            val bean = AreaCoordinateBean()
            bean.xMin = Utils.hexToFloat(Utils.reversalHex(areaList[i].substring(0,8)))
            bean.xMax = Utils.hexToFloat(Utils.reversalHex(areaList[i].substring(8,16)))
            bean.yMin = Utils.hexToFloat(Utils.reversalHex(areaList[i].substring(16,24)))
            bean.yMax = Utils.hexToFloat(Utils.reversalHex(areaList[i].substring(24,32)))
            bean.zMin = Utils.hexToFloat(Utils.reversalHex(areaList[i].substring(32,40)))
            bean.zMax = Utils.hexToFloat(Utils.reversalHex(areaList[i].substring(40,48)))
            monitoringAreaList.add(bean)
        }
    }

    val gson = Gson()

    /**
     * 解析目标位置
     */
    fun analysisBodyLocate(strData:String){
        targetNum = Utils.reversalHex(strData.substring(0,8)).toInt(16)
        val locateStr = strData.substring(8)
        if (targetNum != 0){
            if (locateStr.length >= targetNum * 4 * 5 * 2){
                targetLocateList.clear()
                var subStringIndex = 0
                for (i in 0 until targetNum) {
                    val bean = PersonnelLocationBean()

                    bean.x = Utils.hexToFloat(Utils.reversalHex(locateStr.substring(subStringIndex,subStringIndex + 8)))
                    subStringIndex += 8
                    bean.y = Utils.hexToFloat(Utils.reversalHex(locateStr.substring(subStringIndex,subStringIndex + 8)))
                    subStringIndex += 8
                    bean.z = Utils.hexToFloat(Utils.reversalHex(locateStr.substring(subStringIndex,subStringIndex + 8)))
                    subStringIndex += 8
                    bean.dopIDX = Utils.reversalHex(locateStr.substring(subStringIndex, subStringIndex + 8)).toBigInteger(16).toInt()
                    subStringIndex += 8
                    bean.clusterID = Utils.reversalHex(locateStr.substring(subStringIndex, subStringIndex + 8)).toBigInteger(16).toInt()
                    subStringIndex += 8
                    targetLocateList.add(bean)
                }
            }
        }
//        Log.e(TAG, "analysisBodyLocate: " + gson.toJson(targetLocateList))
    }

//    fun analysisBodyLocate(strData:String){
//        photosensitive = Utils.hexToFloat(Utils.reversalHex(strData))
//    }

    // 01 5D6B 0010 0A0A D8 00000000000000000000000000000000 FF
    // 01 5D6C 002C 0A04 ED 02000000FA0066BDAE85F5BEECE22C3F0000000001000000CDC79C3EC7A8E23E19341D3F0200000002000000B4015D6D00000201CD015D6E00600A0BAC000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000FF
    //
    //
    //
    // String str = "BECCCCCD";
//     BigInteger b = new BigInteger(str, 16);
//     float value = Float.intBitsToFloat(b.intValue());
    // System.out.println(value);
    // Log.e(TAG, "LD6002 onCreate: " + value);
    //
    //
    // 01 5D6F 0060 0A0C AA CDCCCCBE CDCCCC3E 000080BE 9A99193E 0000C0C0 0000C03F 000000000000000000000000000000000000C0C00000C040000000000000000000000000000000000000C0C00000C040000000000000000000000000000000000000C0C00000C0401A

    /*********************************************上方为LD6002解析方法区*******************************************************/

    override fun toString(): String {
        return "DeviceState(TAG='$TAG', mContext=$mContext, strMAC='$strMAC', iMaxDoorNum=$iMaxDoorNum, iSportDoorNum=$iSportDoorNum, iStaticDoorNum=$iStaticDoorNum, sportDoorSPLList=$sportDoorSPLList, staticDoorSPLList=$staticDoorSPLList, iKeepTime=$iKeepTime, strFirmwareType='$strFirmwareType', strFirmwareVer='$strFirmwareVer', strDoorDPI='$strDoorDPI', iPushTargetState=$iPushTargetState, iPushSportTargetLength=$iPushSportTargetLength, iPushSportTargetCapacity=$iPushSportTargetCapacity, iPushStaticTargetLength=$iPushStaticTargetLength, iPushStaticTargetCapacity=$iPushStaticTargetCapacity, iPushSearchLength=$iPushSearchLength, isProjectEnable=$isProjectEnable, iPushSportDoorNumByProject=$iPushSportDoorNumByProject, iPushStaticDoorNumByProject=$iPushStaticDoorNumByProject, pushSportCapacityListByProject=$pushSportCapacityListByProject, pushStatiCapacityListByProject=$pushStatiCapacityListByProject, strPushMoreHexDataByProject='$strPushMoreHexDataByProject', photosensitive=$photosensitive, photosensitiveState=$photosensitiveState, photosensitiveThresholdValue=$photosensitiveThresholdValue, frequent='$frequent', upperLimit=$upperLimit, lowerLimit=$lowerLimit, thresholdState=$thresholdState, initiativeThresholdState=$initiativeThresholdState, type2411s=$type2411s, distance2411s=$distance2411s, maxRangeMotion=$maxRangeMotion, minRangeMotion=$minRangeMotion, maxTinyMotion=$maxTinyMotion, minTinyMotion=$minTinyMotion, isMultipleTarget=$isMultipleTarget, filtrationStr='$filtrationStr', filtrationMode=$filtrationMode)"
    }
}
