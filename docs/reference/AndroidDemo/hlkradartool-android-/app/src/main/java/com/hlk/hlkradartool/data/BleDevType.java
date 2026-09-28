package com.hlk.hlkradartool.data;

public enum BleDevType {
    /** 没有自定义广播包，旧固件 */
    No_Receive_Old,
    /** 有自定义广播包，有版本号，没有密码校验，没有OTA，有分辨率 */
    Haved_Ver_No_CheckPwd_No_Upgrade_Haved_SPI,
    /** 有自定义广播包，有版本号，有密码校验，有OTA，有分辨率 */
    Haved_Ver_Haved_CheckPwd_Haved_Upgrade_Haved_SPI

    
}
