package com.smartIPandeInfo.data

class MessageInfo {

    public companion object {

        const val i_GATT_CONNECT_STATE = 0x00 // GATT 连接状态的变化
        const val i_GATT_CONNECT_STATE_CONNECTING = 0x01 // GATT 连接中
        const val i_GATT_CONNECT_STATE_CONNECTED = 0x02 // GATT 连接成功
        const val i_GATT_CONNECT_STATE_DISCONNECT = 0x03 // GATT 连接断开
        const val i_GATT_SERVICE_DISCOVER = 0x1000 // GATT 获取服务
        const val i_GATT_RECEIVE_DATA = 0x2000 // GATT 接收数据
        const val i_GATT_READ_RESULT = 0x3000 // GATT 读数据的回调
        const val i_GATT_WRITE_RESULT = 0x4000 // GATT 写数据的回调
        const val i_GATT_MTU_CHANGE = 0x5000 // GATT MTU改变的回调
        const val i_GATT_SCAN_BLE_DEV = 0x6000 // 搜索蓝牙设备的回调


        const val i_UPDATE_ROOM_INFO = 0x10000 // 更新房间信息


    }


    var iCode:Int = -1
    var iParam = -1
    var strParam = ""
    var blParam = false
    var strMac = ""
    var objects : Any? = null
    var byteArray : ByteArray? = null

    constructor(iCode: Int) {
        this.iCode = iCode
    }

    constructor(iCode: Int,strMac:String) {
        this.iCode = iCode
        this.strMac = strMac
    }



}