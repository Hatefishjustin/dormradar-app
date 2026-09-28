package com.hlk.hlkradartool.data

class ReceiveInfo {

    public companion object {

        const val i_AUTO_PUSH_DATA = 0 // 主动上报
        const val i_SET_RESULT = 1 // 设置的回复
        const val i_QUERY_RESULT = 2 // 查询的回复

        const val i_X_MODEM_UPGRADE_RESULT = 3 // XMODEM升级的回复

        const val drawview_result = -1

    }


    var iCode:Int = -2
    var iParam = -1
    var strParam = ""
    var blParam = false
    var strMac = ""
    var objects : Any? = null

    var key = 0;

    constructor(strMac:String) {
        this.strMac = strMac
    }

    constructor(iCode: Int,strMac:String) {
        this.iCode = iCode
        this.strMac = strMac
    }

    constructor(iCode: Int,key:Int){
        this.iCode = iCode
        this.key = key
    }

}