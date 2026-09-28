package com.hlk.hlkradartool.data

import com.hlk.hlkradartool.util.BaseVolume
import com.hlk.hlkradartool.util.Utils
import java.util.*

class CreateControlData {

    companion object {

        /**
         * 校验密码
         */
        fun checkKeyByPwd(strPwd:String) : String {
            var strData = ""
            var strLength = "0800"
            var strType = BaseVolume.CMD_TYPE_CHECK_KEY
            var strHexPwd = Utils.bytesToHexString(strPwd.toByteArray())
            strData = BaseVolume.CMD_TYPE_SET_HEAD + strLength + strType + strHexPwd + BaseVolume.CMD_TYPE_SET_END
            return strData
        }

        /**
         * 设置控制密码
         */
        fun setCtrKeyByPwd(strPwd:String) : String {
            var strData = ""
            var strLength = "0800"
            var strType = BaseVolume.CMD_TYPE_SET_CTR_KEY
            var strHexPwd = Utils.bytesToHexString(strPwd.toByteArray())
            strData = BaseVolume.CMD_TYPE_SET_HEAD + strLength + strType + strHexPwd + BaseVolume.CMD_TYPE_SET_END
            return strData
        }

        /**
         * 设置光敏配置
         */
        fun setPhotosensitive(state:String , value:Int) : String {
            var strData = ""
            var strLength = "0400"
            var strType = BaseVolume.CMD_TYPE_SET_PHOTOSENSITIVE
            var strState = state
            var strvalue = String.format(Locale.ENGLISH, "%02X", value)
            strData = BaseVolume.CMD_TYPE_SET_HEAD + strLength + strType + strState + strvalue + BaseVolume.CMD_TYPE_SET_END
            return strData
        }

        /**
         * 设置电频
         */
        fun setFrequent(state:Int , value:Int , frequent : String) : String {
            var strData = ""
            var strLength = "0600"
            var strType = BaseVolume.CMD_TYPE_SET_PHOTOSENSITIVE
            var strState = String.format(Locale.ENGLISH, "%02X", state)
            var strvalue = String.format(Locale.ENGLISH, "%02X", value)
            strData = BaseVolume.CMD_TYPE_SET_HEAD + strLength + strType + strState + strvalue + frequent +"00" + BaseVolume.CMD_TYPE_SET_END
            return strData
        }


        /**
         * 设置最大距离门与无人持续时间
         */
        fun setFromDoorAndKeepTime(iDoorNum:Int,iKeepTime:Int) : String {
            var strData = ""
            var strLength = "1400"
            var strType = BaseVolume.CMD_TYPE_FROM_DOOR_KEEP_TIME
            var strSportDoorHex = reversalHex(iDoorNum,8,true)
            var strStaticDoorHex = reversalHex(iDoorNum,8,true)
            var strKeepTimeHex = reversalHex(iKeepTime,8,true)
            strData = BaseVolume.CMD_TYPE_SET_HEAD + strLength + strType +
                    "0000" + strSportDoorHex +
                    "0100" + strStaticDoorHex +
                    "0200" + strKeepTimeHex + BaseVolume.CMD_TYPE_SET_END
            return strData
        }

        /**
         * 设置距离门灵敏度(所有门，传0xFFFF)
         */
        fun setFromDoorSPL(iDoorNumber:Int,iSPLSportValue:Int,iSPLStaticValue:Int) : String {
            var strData = ""
            var strLength = "1400"
            var strType = BaseVolume.CMD_TYPE_FROM_DOOR_SPL
            var strDoorNumberHex = reversalHex(iDoorNumber,8,true)
            var strSPLSportHex = reversalHex(iSPLSportValue,8,true)
            var strSPLStaticHex = reversalHex(iSPLStaticValue,8,true)
            strData = BaseVolume.CMD_TYPE_SET_HEAD + strLength + strType +
                    "0000" + strDoorNumberHex +
                    "0100" + strSPLSportHex +
                    "0200" + strSPLStaticHex + BaseVolume.CMD_TYPE_SET_END
            return strData
        }



        /**
         * 原始数据
         * 16进制长度
         * 是否需要反转（大小端）
         */
        private fun reversalHex(iValue:Int,iHexLength:Int,isReversal:Boolean):String {
            val strBigHex = String.format("%0${iHexLength}X",iValue)
            if (isReversal) {
//                var strReverHex = ""
//                for (iN in 0 until strBigHex.length/2) {
//                    strReverHex = strBigHex.substring(iN*2,(iN+1)*2) + strReverHex
//                }
                return Utils.reversalHex(strBigHex);
            }
            else {
                return strBigHex
            }

        }


        /**
         * 设置门限值
         */
        fun setThreshold(upperLimit:Int , lowerLimit:Int ) : String {
            var strData = ""
            var strLength = "0600"
            var strType = BaseVolume.CMD_TYPE_SET_THRESHOLD_VALUE
            var strUpperLimit = String.format(Locale.ENGLISH, "%02X", upperLimit)+"00"
            var strLowerLimit = String.format(Locale.ENGLISH, "%02X", lowerLimit)+"00"
            strData = BaseVolume.CMD_TYPE_SET_HEAD + strLength + strType + strUpperLimit + strLowerLimit + BaseVolume.CMD_TYPE_SET_END
            return strData
        }


        /**
         * 2411s
         * 设置运动范围、微动范围、无人持续时间
         */
        fun setScopeTime2411s(maxRangeMotion:Int , minRangeMotion:Int , maxTinyMotion:Int , minTinyMotion:Int , keepTime:Int) : String {
            var strData = ""
            var strLength = "2000"
            var strType = BaseVolume.CMD_WRITE_SCOPE_TIME_DATA_2411s
            var strMaxRangeMotion = String.format(Locale.ENGLISH, "%04X", maxRangeMotion)
            var strMinRangeMotion = String.format(Locale.ENGLISH, "%04X", minRangeMotion)
            var strMaxTinyMotion = String.format(Locale.ENGLISH, "%04X", maxTinyMotion)
            var strMinTinyMotion = String.format(Locale.ENGLISH, "%04X", minTinyMotion)
            var strKeepTime = String.format(Locale.ENGLISH, "%04X", keepTime)
            strData = BaseVolume.CMD_TYPE_SET_HEAD + strLength + strType + "0000" + strMaxRangeMotion.subSequence(2,4) + strMaxRangeMotion.subSequence(0,2) + "00000100" +
                    strMinRangeMotion.subSequence(2,4) + strMinRangeMotion.subSequence(0,2) + "00000200" +
                    strMaxTinyMotion.subSequence(2,4) + strMaxTinyMotion.subSequence(0,2) + "00000300" +
                    strMinTinyMotion.subSequence(2,4) + strMinTinyMotion.subSequence(0,2) + "00000400" +
                    strKeepTime.subSequence(2,4) + strKeepTime.subSequence(0,2) + "0000" + BaseVolume.CMD_TYPE_SET_END
            return strData
        }

        /**
         * 组装2412基础参数配置的指令
         */
        // FDFCFBFA
        // 0700
        // 0200
        // 04092A0000
        // 04030201
        fun setBaseData2412(minRange:Int,maxRange:Int,keepTime: Int,outConfig:String):String{
            var strData = ""
            val strHead = BaseVolume.CMD_SET_BASE_DATA_2412_HEAD
            val strMinRange = String.format(Locale.ENGLISH, "%2s", minRange.toString(16)).replace(' ','0')
            val strMaxRange = String.format(Locale.ENGLISH, "%2s", maxRange.toString(16)).replace(' ','0')
            val strKeepTime = Utils.reversalHex(String.format(Locale.ENGLISH, "%4s", keepTime.toString(16)).replace(' ','0'))
            strData = strHead + strMinRange + strMaxRange + strKeepTime + outConfig + BaseVolume.CMD_TYPE_SET_END
            return strData

        }
        /**
         * 组装日志开启关闭的指令
         */
        // FDFCFBFA
        // 0400
        // B800
        // 0100
        // 04030201
        fun setRizhiopen(isopen:Boolean):String{
            var strData = ""
            val strHead = BaseVolume.ALL_HEADER
            var isopendata="0000"
            if(isopen){
                isopendata="0100"
            }
            strData = strHead + "0400" + BaseVolume.RIZHIKAIGUAN + isopendata  + BaseVolume.CMD_TYPE_SET_END
            return strData

        }
        /**
         * 组装日志清除关闭的指令
         */
        // FDFCFBFA
        // 0200
        // B700
        // 04030201
        fun setRizhiqingchu():String{
            var strData = ""
            val strHead = BaseVolume.ALL_HEADER

            strData = strHead + "0200" + BaseVolume.QINGCHURIZHI + BaseVolume.CMD_TYPE_SET_END
            return strData

        }
        /**
         * 组装日志读取关闭的指令
         */
        // FDFCFBFA
        // 0600
        // B600
        //1700 0000 17=23字节 AA   01   00 00   30 64 24 03 0A 04 03 06 04   00 00 00 00 00 00 00 00 00   55
        // 04030201
        fun setRizhiduqu(lengh:Int):String{
            var strData = ""
            val strHead = BaseVolume.ALL_HEADER
            var strlen=String.format(Locale.ENGLISH, "%04X", lengh)
            strlen=strlen.substring(2,4)+strlen.substring(0,2)+"0000"

            strData = strHead + "0600" + BaseVolume.RIZHIDUQU +strlen+ BaseVolume.CMD_TYPE_SET_END
            return strData

        }
        /**
         * 组装日志清除关闭的指令
         */
        // FDFCFBFA
        // 0200
        // B500
        // 04030201
        fun setRizhichangdu():String{
            var strData = ""
            val strHead = BaseVolume.ALL_HEADER
            strData = strHead + "0200" + BaseVolume.RIZHICHANGDU + BaseVolume.CMD_TYPE_SET_END
            return strData

        }

    }






}