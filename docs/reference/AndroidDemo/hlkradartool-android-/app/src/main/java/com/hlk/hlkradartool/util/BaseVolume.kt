package com.hlk.hlkradartool.util

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.text.TextUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object BaseVolume {

    // 主动上报
    const val CMD_TYPE_AUTO_PUSH_HEAD = "F4F3F2F1"
    const val CMD_TYPE_AUTO_PUSH_END = "F8F7F6F5"

    // 设置参数
    const val CMD_TYPE_SET_HEAD = "FDFCFBFA"
    const val CMD_TYPE_SET_END = "04030201"
    const val CMD_TYPE_SET_ACK = "0000"
    const val CMD_TYPE_SET_NACK = "0100"

    // 开始配置
    const val CMD_TYPE_ENABLE_ON = "FF00"
    const val CMD_TYPE_ENABLE_ON_RESULT = "FF01"
    const val CMD_FULL_ENABLE_ON = "FDFCFBFA0400FF00010004030201"

    const val CMD_FULL_ENABLE_ON_2411 = "FDFCFBFA0200FF0004030201"

    // 结束配置
    const val CMD_TYPE_ENABLE_OFF = "FE00"
    const val CMD_TYPE_ENABLE_OFF_RESULT = "FE01"
    const val CMD_FULL_ENABLE_OFF = "FDFCFBFA0200FE0004030201"

    // 距离门&无人持续时间
    const val CMD_TYPE_FROM_DOOR_KEEP_TIME = "6000"
    const val CMD_TYPE_FROM_DOOR_KEEP_TIME_RESULT = "6001"

    // 读取参数
    const val CMD_TYPE_READ_INFO = "6100"
    const val CMD_TYPE_READ_INFO_RESULT = "6101"
    const val CMD_FULL_READ_INFO = "FDFCFBFA0200610004030201"

    // 使能工程模式开启
    const val CMD_TYPE_ENABLE_PROJECT_ON = "6200"
    const val CMD_TYPE_ENABLE_PROJECT_ON_RESULT = "6201"
    const val CMD_FULL_ENABLE_PROJECT_ON = "FDFCFBFA0200620004030201"

    // 使能工程模式关闭
    const val CMD_TYPE_ENABLE_PROJECT_OFF = "6300"
    const val CMD_TYPE_ENABLE_PROJECT_OFF_RESULT = "6301"
    const val CMD_FULL_ENABLE_PROJECT_OFF = "FDFCFBFA0200630004030201"

    // 距离门灵敏度配置
    const val CMD_TYPE_FROM_DOOR_SPL = "6400"
    const val CMD_TYPE_FROM_DOOR_SPL_RESULT = "6401"

    // 读取版本号
    const val CMD_TYPE_READ_VER = "A000"
    const val CMD_TYPE_READ_VER_RESULT = "A001"
    const val CMD_FULL_READ_VER = "FDFCFBFA0200A00004030201"

    // 读取距离门分辨率
    const val CMD_TYPE_READ_DOOR_DPI = "AB00"
    const val CMD_TYPE_READ_DOOR_DPI_RESULT = "AB01"
    const val CMD_FULL_READ_DOOR_DPI = "FDFCFBFA0200AB0004030201"

    // 设置分辨率
    const val CMD_TYPE_SET_DOOR_DPI = "AA00"
    const val CMD_TYPE_SET_DOOR_DPI_RESULT = "AA01"
    const val CMD_FULL_SET_DOOR_DPI_02 = "FDFCFBFA0400AA00010004030201"
    const val CMD_FULL_SET_DOOR_DPI_075 = "FDFCFBFA0400AA00000004030201"

    const val CMD_TYPE_DOOR_DPI_02 = "0100"
    const val CMD_TYPE_DOOR_DPI_075 = ""

    // 设置波特率
    const val CMD_TYPE_SET_BAUD = "A100"
    const val CMD_TYPE_SET_BAUD_RESULT = "A101"
    const val CMD_FULL_SET_BAUD_9600 = "FDFCFBFA0400A100010004030201"
    const val CMD_FULL_SET_BAUD_19200 = "FDFCFBFA0400A100020004030201"
    const val CMD_FULL_SET_BAUD_38400 = "FDFCFBFA0400A100030004030201"
    const val CMD_FULL_SET_BAUD_57600 = "FDFCFBFA0400A100040004030201"
    const val CMD_FULL_SET_BAUD_115200 = "FDFCFBFA0400A100050004030201"
    const val CMD_FULL_SET_BAUD_230400 = "FDFCFBFA0400A100060004030201"
    const val CMD_FULL_SET_BAUD_256000 = "FDFCFBFA0400A100070004030201"
    const val CMD_FULL_SET_BAUD_460800 = "FDFCFBFA0400A100080004030201"

    // 恢复出厂设置
    const val CMD_TYPE_DEFAULT = "A200"
    const val CMD_TYPE_DEFAULT_RESULT = "A201"
    const val CMD_FULL_DEFAULT = "FDFCFBFA0200A20004030201"

    // 重启模块
    const val CMD_TYPE_RESET = "A300"
    const val CMD_TYPE_RESET_RESULT = "A301"
    const val CMD_FULL_RESET = "FDFCFBFA0200A30004030201"

    // 读取配置成功的回复
    const val CMD_TYPE_READ_SUCCEED_CODE = 200
    const val CMD_TYPE_READ_DATA_SUCCEED = "FE01"
    const val CMD_READ_DATA_SUCCEED = "FDFCFBFA0400FE01000004030201"

    // 校验密码
    const val CMD_TYPE_CHECK_KEY = "A800"
    const val CMD_TYPE_CHECK_KEY_RESULT = "A801"

    // 设置控制密码
    const val CMD_TYPE_SET_CTR_KEY = "A900"
    const val CMD_TYPE_SET_CTR_RESULT = "A901"

    // OTA升级(使能)
    const val CMD_FULL_OTA_ENABLE = "FDFCFBFA0400C000010004030201"
    // OTA升级(关闭使能)
    const val CMD_FULL_OTA_DISABLE = "FDFCFBFA0400C000000004030201"

    //自定义蓝牙名称
    const val EDIT_BLE_MAC = "EDIT_BLE_MAC"
    const val EDIT_BLE_NAME = "EDIT_BLE_NAME"


    // 读取设置光敏配置
    const val CMD_TYPE_SET_PHOTOSENSITIVE = "AD00"
    const val CMD_TYPE_SET_PHOTOSENSITIVE_RESULT = "AD01"
    const val CMD_TYPE_READ_PHOTOSENSITIVE_RESULT = "AE01"
    const val CMD_FULL_READ_PHOTOSENSITIVE = "FDFCFBFA0200AE0004030201"

    const val FROM_VERSION_LIST_ACTIVITY = 2

    //初始化友盟SDK
    const val INIT_UMENG_SDK = "INIT_UMENG_SDK"

    //查询门限值
    const val CMD_FULL_THRESHOLD_VALUE = "FDFCFBFA0200c50004030201"
    const val CMD_TYPE_READ_THRESHOLD_VALUE = "C501"
    const val CMD_TYPE_SET_THRESHOLD_VALUE = "C400"
    const val CMD_TYPE_SET_THRESHOLD_VALUE_REPLY = "C401"
    const val CMD_TYPE_INITIATIVE_THRESHOLD = "C600"

    // 自动检测底噪 这里的3C00是约定好的固定60秒的底噪检测时长
    const val CMD_GROUND_NOISE_AUTO_2410 = "FDFCFBFA04000B003C0004030201"
    const val CMD_GROUND_NOISE_REPLY_AUTO_2410 = "0B01"


    /****************************************雷达2411s用到的指令***************************************/
    // 主动上报
    const val CMD_TYPE_AUTO_PUSH_HEAD_2411s = "AAAA"
    const val CMD_TYPE_AUTO_PUSH_END_2411s = "5555"
    //读取参数命令
    const val CMD_READ_DATA_2411s = "FDFCFBFA0200730004030201"
    const val CMD_READ_DATA_REPLY_2411s = "7301"
    //恢复出厂
    const val CMD_WRITE_SCOPE_TIME_DATA_2411s = "6700"
    const val CMD_WRITE_SCOPE_TIME_DATA_REPLY_2411s = "6701"

    /************************************************************************************************/

    /****************************************雷达2412用到的指令***************************************/

    // 基础参数查询
    const val CMD_READ_BASE_DATA_2412 = "FDFCFBFA0200120004030201"
    const val CMD_READ_BASE_DATA_REPLY_2412 = "1201"
    // 基础参数配置
    const val CMD_SET_BASE_DATA_2412_HEAD = "FDFCFBFA07000200"
    const val CMD_SET_BASE_DATA_REPLY_2412 = "0201"

    // 运动灵敏度配置
    const val CMD_SET_RUN_DATA_2412_HEAD = "FDFCFBFA10000300"
    const val CMD_SET_RUN_DATA_REPLY_2412 = "0301"
    // 运动灵敏度查询
    const val CMD_READ_RUN_DATA_2412 = "FDFCFBFA0200130004030201"
    const val CMD_READ_RUN_DATA_REPLY_2412 = "1301"

    // 静止灵敏度配置
    const val CMD_SET_STATIC_DATA_2412_HEAD = "FDFCFBFA10000400"
    const val CMD_SET_STATIC_DATA_REPLY_2412 = "0401"
    // 静止灵敏度查询
    const val CMD_READ_STATIC_DATA_2412 = "FDFCFBFA0200140004030201"
    const val CMD_READ_STATIC_DATA_REPLY_2412 = "1401"

    const val CMD_GROUND_NOISE_2412 = "FDFCFBFA02000B0004030201"
    const val CMD_GROUND_NOISE_REPLY_2412 = "0B01"

    // 距离门
    const val CMD_READ_DISTANCE_DOOR_2412 = "FDFCFBFA0200110004030201"
    const val CMD_READ_DISTANCE_DOOR_REPLY_2412 = "1101"
    const val CMD_SET_DISTANCE_DOOR_02_2412 = "FDFCFBFA0800010003000000000004030201"
    const val CMD_SET_DISTANCE_DOOR_05_2412 = "FDFCFBFA0800010001000000000004030201"
    const val CMD_SET_DISTANCE_DOOR_075_2412 = "FDFCFBFA0800010000000000000004030201"
    const val CMD_SET_DISTANCE_DOOR_REPLY_2412 = "0101"

    // 光感辅助控制功能配置设置
    const val CMD_SET_PHOTOSENSITIVE_HEAD_2412 = "FDFCFBFA04000C00"
    const val CMD_SET_PHOTOSENSITIVE_REPLY_2412 = "0C01"

    // 光感辅助控制功能配置查询
    const val CMD_READ_PHOTOSENSITIVE_2412 = "FDFCFBFA02001C0004030201"
    const val CMD_READ_PHOTOSENSITIVE_REPLY_2412 = "1C01"

    // 主动上报
    const val CMD_TYPE_AUTO_PUSH_HEAD_2412 = "F4F3F2F1"
    const val CMD_TYPE_AUTO_PUSH_END_2412 = "F8F7F6F5"
    const val CMD_TYPE_AUTO_PUSH_NORMALCY_2412 = "0B00"
    const val CMD_TYPE_AUTO_PUSH_CONFIG_2412 = "2900"

    /************************************************************************************************/

    /****************************************雷达2450用到的指令***************************************/

    const val ALL_HEADER = "FDFCFBFA"
    const val RIZHIKAIGUAN = "B800"
    const val RIZHIKAIGUAN_RESULT = "B801"
    const val QINGCHURIZHI = "B700"
    const val QINGCHURIZHI_RESULT = "B701"
    const val RIZHIDUQU = "B600"
    const val RIZHIDUQU_RESULT = "B601"
    const val RIZHICHANGDU = "B500"
    const val RIZHICHANGDU_RESULT = "B501"
    // 单目标追踪
    const val CMD_TYPE_SINGLE_TARGET_TRACKING_2450 = "FDFCFBFA0200800004030201"
    const val CMD_TYPE_SINGLE_TARGET_TRACKING_REPLY_2450 = "8001"
    // 多目标追踪
    const val CMD_TYPE_MULTIPLE_TARGET_TRACKING_2450 = "FDFCFBFA0200900004030201"
    const val CMD_TYPE_MULTIPLE_TARGET_TRACKING_REPLY_2450 = "9001"
    //      // 读取固件版本命令
        const val CMD_TYPE_READ_FIRMWARE_VERSION_2450 = "FDFCFBFA0200A00004030201"
        const val CMD_TYPE_READ_FIRMWARE_VERSION_REPLY_2450 = "A001"

        const val CMD_TYPE_READ_FIRMWARE_SETPARAMETER_2451 = "FDFCFBFA0200120004030201"//2451查询参数配置

        const val CMD_TYPE_READ_LINGMINDU_2451 = "FDFCFBFA0200130004030201"//2451查询灵敏度02 F3 55 55
        const val CMD_TYPE_SETVALUEOPEN_2451 = "FDFCFBFA0400FF00010004030201"//2451使能开
        const val CMD_TYPE_SETVALUECLOSE_2451 = "FDFCFBFA0200FE0004030201"//2451使能关

    //    // 设置串口波特率
//    const val CMD_TYPE_BAUD_2450 = "FDFCFBFA0400A100"
//    const val CMD_BAUD_9600 = "0100"
//    const val CMD_BAUD_19200 = "0200"
//    const val CMD_BAUD_38400 = "0300"
//    const val CMD_BAUD_57600 = "0400"
//    const val CMD_BAUD_115200 = "0500"
//    const val CMD_BAUD_230400 = "0600"
//    const val CMD_BAUD_256000 = "0700"
//    const val CMD_BAUD_460800 = "0800"
//    const val CMD_TYPE_BAUD_END_2450 = "04030201"
//    const val CMD_TYPE_BAUD_REPLY_2450 = "A101"
//    // 恢复出厂设置
//    const val CMD_TYPE_FACTORY_RESET_2450 = "FDFCFBFA0200A20004030201"
//    const val CMD_TYPE_FACTORY_RESET_REPLY_2450 = "A201"
//    // 重启模块
//    const val CMD_TYPE_RESTART_2450 = "FDFCFBFA0200A30004030201"
//    const val CMD_TYPE_RESTART_REPLY_2450 = "A301"
    // 蓝牙设置
    const val CMD_TYPE_BLUETOOTH_OPEN_2450 = "FDFCFBFA0400A400010004030201"
    const val CMD_TYPE_BLUETOOTH_CLOSE_2450 = "FDFCFBFA0400A400000004030201"
    const val CMD_TYPE_BLUETOOTH_SET_REPLY_2450 = "A301"
    // 获取mac地址
    const val CMD_TYPE_GET_MAC_ADDRESS_2450 = "FDFCFBFA0400A500010004030201"
    const val CMD_TYPE_GET_MAC_ADDRESS_REPLY_2450 = "A501"
    // 查询当前的区域过滤配置
    const val CMD_TYPE_GET_FILTRATION_2450 = "FDFCFBFA0200C10004030201"
    const val CMD_TYPE_GET_FILTRATION_REPLY_2450 = "C101"
    // 设置区域过滤配置
    const val CMD_TYPE_SET_FILTRATION_HEAD_2450 = "FDFCFBFA1C00C200"
    const val CMD_TYPE_SET_FILTRATION_NO_MODE_2450 = "0000"     // 关闭区域过滤
    const val CMD_TYPE_SET_FILTRATION_ONLY_MODE_2450 = "0100"   // 仅检测设置的区域
    const val CMD_TYPE_SET_FILTRATION_EXCEPT_MODE_2450 = "0200" // 不检测设置的区域
    const val CMD_TYPE_SET_FILTRATION_FOOT_2450 = "04030201"
    const val CMD_TYPE_SET_FILTRATION_REPLY_2450 = "C201"

    // 查询目标追踪模式
    const val CMD_TYPE_GET_TARGET_TRACKING_MODE_2450 = "FDFCFBFA0200910004030201"
    const val CMD_TYPE_GET_TARGET_TRACKING_MODE_REPLY_2450 = "9101"
    const val CMD_TYPE_GET_TARGET_TRACKING_MODE_REPLY_SINGLE_2450 = "0100"
    const val CMD_TYPE_GET_TARGET_TRACKING_MODE_REPLY_MULTI_2450 = "0200"

    const val NO_COORDINATES = "(0,0,0,0)"
    const val NO_COORDINATESSet = "(x:0.1m,y:0.0m,w:0.0m,h:0.0m)"
    const val NO_COORDINATESSet1 = "(x:0m,y:0m,w:0m,h:0m)"

    /************************************************LD6002*****************************************************/
    const val LD6002_CMD_TYPE_0A04 = "0A04"                                         // 报告人员位置 目标数据
    const val LD6002_CMD_TYPE_0A08 = "0A08"                                         // 报告人员位置 点云数据
    const val LD6002_CMD_TYPE_0A0A = "0A0A"                                         // 上报区域中是否有人
    const val LD6002_CMD_TYPE_0A0B = "0A0B"                                         // 上报干扰区域
    const val LD6002_CMD_TYPE_0A0C = "0A0C"                                         // 上报监控区域
    const val LD6002_CMD_TYPE_0A0D = "0A0D"                                         // 上报保持延时时间
    const val LD6002_CMD_TYPE_0A0E = "0A0E"                                         // 上报检测灵敏度状态
    const val LD6002_CMD_TYPE_0A0F = "0A0F"                                         // 上报触发速度状态
    const val LD6002_CMD_TYPE_0A10 = "0A10"                                         // 上报 Z 轴范围
    const val LD6002_CMD_TYPE_0A11 = "0A11"                                         // 上报安装方式
    const val LD6002_CMD_TYPE_0A12 = "0A12"                                         // 上报无人时低功耗模式
    const val LD6002_CMD_TYPE_0A13 = "0A13"                                         // 上报无人时低功耗模式睡眠时间
    const val LD6002_CMD_TYPE_0A14 = "0A14"                                         // 上报工作模式

    const val LD6002_CMD_0201 = "0201"                                              // 控制指令
    const val LD6002_CMD_0201_COMMAND_0x1 = "01000000"                              // 自动生成干扰区。
    const val LD6002_CMD_0201_COMMAND_0x2 = "02000000"                              // 获取干扰区域和检测区域
    const val LD6002_CMD_0201_COMMAND_0x3 = "03000000"                              // 清除干扰区域
    const val LD6002_CMD_0201_COMMAND_0x4 = "04000000"                              // 重置检测区域。
    const val LD6002_CMD_0201_COMMAND_0x5 = "05000000"                              // 获取保持延时时间
    const val LD6002_CMD_0201_COMMAND_0x6 = "06000000"                              // 打开点云显示
    const val LD6002_CMD_0201_COMMAND_0x7 = "07000000"                              // 关闭点云显示
    const val LD6002_CMD_0201_COMMAND_0x8 = "08000000"                              // 打开目标显示
    const val LD6002_CMD_0201_COMMAND_0x9 = "09000000"                              // 关闭目标显示
    const val LD6002_CMD_0201_COMMAND_0xA = "0A000000"                              // 设置检测灵敏度低
    const val LD6002_CMD_0201_COMMAND_0xB = "0B000000"                              // 设置检测灵敏度中
    const val LD6002_CMD_0201_COMMAND_0xC = "0C000000"                              // 设置检测灵敏度高
    const val LD6002_CMD_0201_COMMAND_0xD = "0D000000"                              // 获取检测灵敏度状态
    const val LD6002_CMD_0201_COMMAND_0xE = "0E000000"                              // 设置触发速度慢
    const val LD6002_CMD_0201_COMMAND_0xF = "0F000000"                              // 设置触发速度中
    const val LD6002_CMD_0201_COMMAND_0x10 = "10000000"                              // 设置触发速度快。
    const val LD6002_CMD_0201_COMMAND_0x11 = "11000000"                              // 获取触发速度状态
    const val LD6002_CMD_0201_COMMAND_0x12 = "12000000"                              // 获取 Z 轴范围。注： 这条协议仅适用于 3D
    const val LD6002_CMD_0201_COMMAND_0x13 = "13000000"                              // 设置安装方式为顶装。注： 这条协议仅适用于 3D
    const val LD6002_CMD_0201_COMMAND_0x14 = "14000000"                              // 设置安装方式为侧装。注： 这条协议仅适用于 3D
    const val LD6002_CMD_0201_COMMAND_0x15 = "15000000"                              // 获取安装方式
    const val LD6002_CMD_0201_COMMAND_0x16 = "16000000"                              // 打开无人时低功耗模式
    const val LD6002_CMD_0201_COMMAND_0x17 = "17000000"                              // 打开无人时低功耗模式
    const val LD6002_CMD_0201_COMMAND_0x18 = "18000000"                              // 获取无人时低功耗模式是否打开
    const val LD6002_CMD_0201_COMMAND_0x19 = "19000000"                              // 获取无人时低功耗模式睡眠时间。
    const val LD6002_CMD_0201_COMMAND_0x1A = "1A000000"                              // 重置无人状态
    const val LD6002_CMD_0201_COMMAND_0x1B = "1B000000"                              // 关闭雷达模式，P20 输出高电平
    const val LD6002_CMD_0201_COMMAND_0x1C = "1C000000"                              // 关闭雷达模式，P20 输出低电平

    const val LD6002_CMD_0202 = "0202"                                               // 设置干扰区域和检测区域的坐标位置
    const val LD6002_CMD_0203_INTERFERENCE_AREA_1 = "00000000"                       // 干扰区域1的ID
    const val LD6002_CMD_0203_INTERFERENCE_AREA_2 = "00000100"                       // 干扰区域2的ID
    const val LD6002_CMD_0203_INTERFERENCE_AREA_3 = "00000200"                       // 干扰区域3的ID
    const val LD6002_CMD_0203_INTERFERENCE_AREA_4 = "00000300"                       // 干扰区域4的ID
    const val LD6002_CMD_0203_MONITORING_AREA_1 = "00000400"                         // 检测区域1的ID
    const val LD6002_CMD_0203_MONITORING_AREA_2 = "00000500"                         // 检测区域2的ID
    const val LD6002_CMD_0203_MONITORING_AREA_3 = "00000600"                         // 检测区域3的ID
    const val LD6002_CMD_0203_MONITORING_AREA_4 = "00000700"                         // 检测区域4的ID

    const val LD6002_CMD_0203 = "0203"                                               // 设置保持延时时间
    const val LD6002_CMD_0204 = "0204"                                               // 设置Z轴范围
    const val LD6002_CMD_0205 = "0205"                                               // 设置无人时低功耗模式睡眠时间

    const val LD6002_CMD_VERSION = "0100000000FFFFFE"                                // 获取版本号
    const val LD6002_CMD_VERSION_REPLY = "FFFF"                                      // 获取版本号的回复

    const val LD6002_CMD_UPGRADE = "01000000003000CE"                                // 进入升级的指令
    const val LD6002_CMD_UPGRADE_REPLY = "3000"                                      // 升级的指令的回复

    /*************************************************HMD02****************************************************/
    const val HMD02_CMD_OPEN = "01000000040201F91B000000E4"                          // 开灯指令
    const val HMD02_CMD_CLOSE = "01000000040201F91C000000E5"                         // 关灯指令
    const val HMD02_CMD_MODE = "01000000040201F917000000E8"                         // 正常检测模式

    /************************************************保存模板****************************************************/
    const val SAVE_TEMPLATE = "SAVE_TEMPLATE"

    /**
     * 判断某个界面是否在前台
     *
     * @param context
     * @param className
     * 某个界面名称
     */
    fun isForeground(context: Context?, className: String): Boolean {
        if (context == null || TextUtils.isEmpty(className)) {
            return false
        }
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val list = am.getRunningTasks(1)
        if (list != null && list.size > 0) {
            val cpn = list[0].topActivity
            val firstName = cpn!!.className
            if (className == firstName) {
                return true
            }
        }
        return false
    }



    /**
     * 获取SSID
     * @param activity 上下文
     * @return  WIFI 的SSID
     */
    fun getWIFISSID(activity: Activity): String {
        val ssid = "unknown id"
        if (Build.VERSION.SDK_INT <= 26 || Build.VERSION.SDK_INT == 28) {
            val mWifiManager = (activity.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            val info = mWifiManager.connectionInfo
            return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                info.ssid
            } else {
                info.ssid.replace("\"", "")
            }
        } else if (Build.VERSION.SDK_INT == 27) {
            val connManager = (activity.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
            val networkInfo = connManager.activeNetworkInfo!!
            if (networkInfo.isConnected) {
                if (networkInfo.extraInfo != null) {
                    return networkInfo.extraInfo.replace("\"", "")
                }
            }
        }
        return ssid
    }



    /**
     * 获取版本号
     * @return 当前应用的版本号
     */
    fun getVersion(con: Context): String {
        return try {
            val manager = con.packageManager
            val info = manager.getPackageInfo(con.packageName, 0)
            info.versionName
        } catch (e: Exception) {
            e.printStackTrace()
            "0.0"
        }
    }

    /**
     * 获取SDK版本�?
     * @return
     */
    @JvmStatic
    fun getAndroidSDKVersion(): Int {
        var version = 0
        try {
            version = Integer.valueOf(Build.VERSION.SDK)
        } catch (e: NumberFormatException) {
        }
        return version
    }

    /**
     * 获取当前系统时间
     */
    fun getNowSystemTime() : String {
        var currentTime = ""
        val cal = Calendar.getInstance()
        //格式化指定形式的时间
//        currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(cal.time) //获取到完整的时间
        currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm").format(cal.time) //获取到完整的时间
        return currentTime
    }

    /** 年月日
     * 输入格式：2020-12-09
     * 输出格式：Dec-09,2020
     */
    fun getDateInfo(strDate: String) :String{
        val iMonth = strDate.split("-")[1].toString().toInt()
        when(iMonth) {
            1 -> return "Jan-${strDate.split("-")[2]} , ${strDate.split("-")[0]}"
            2 -> return "Feb-${strDate.split("-")[2]} , ${strDate.split("-")[0]}"
            3 -> return "Mar-${strDate.split("-")[2]} , ${strDate.split("-")[0]}"
            4 -> return "Apr-${strDate.split("-")[2]} , ${strDate.split("-")[0]}"
            5 -> return "May-${strDate.split("-")[2]} , ${strDate.split("-")[0]}"
            6 -> return "Jun-${strDate.split("-")[2]} , ${strDate.split("-")[0]}"
            7 -> return "Jul-${strDate.split("-")[2]} , ${strDate.split("-")[0]}"
            8 -> return "Aug-${strDate.split("-")[2]} , ${strDate.split("-")[0]}"
            9 -> return "Sept-${strDate.split("-")[2]} , ${strDate.split("-")[0]}"
            10 -> return "Oct-${strDate.split("-")[2]} , ${strDate.split("-")[0]}"
            11 -> return "Nov-${strDate.split("-")[2]} , ${strDate.split("-")[0]}"
            12 -> return "Dec-${strDate.split("-")[2]} , ${strDate.split("-")[0]}"
        }
        return strDate
    }



    /**
     * 获取当前手机的语言环境
     * zh 中文 zh-rCN
     * en 英文 en-rUS
     * es 西班牙语 es-rES
     * de 德语 de-rDE
     * fr 法语 fr-rFR
     * it 意大利语 it-rIT
     */
    public fun getStrNowLanguageType() : String{
        val l = Locale.getDefault()
        val language = l.language
        val country = l.country.toLowerCase()

        if (language.equals("es",true) || // 西班牙语
            language.equals("de",true) || // 德语
            language.equals("fr",true) || // 法语
            language.equals("it",true)) // 意大利语
        {
            return language
        }
        else // 默认用en  英文
            return "en"
    }



}
