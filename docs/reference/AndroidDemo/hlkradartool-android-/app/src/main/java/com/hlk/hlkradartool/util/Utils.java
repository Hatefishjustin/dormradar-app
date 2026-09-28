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

package com.hlk.hlkradartool.util;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import java.io.InputStream;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * This class includes a small subset of standard GATT attributes for demonstration purposes.
 */
public class Utils {

    /**
     * 获取系统当前时间
     * @return
     */
    public static String getNowSystemTimeStamp() {
        Date date = new Date();
        SimpleDateFormat dateFormat= new SimpleDateFormat("HH:mm:ss");
        return dateFormat.format(date);
    }

    /**
     * 获取当前WIFI名称
     *
     * @param context 上下文
     * @return
     */
    public static String getCurrentSsid(Context context) {
        String ssid = "unknown ssid";
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        assert wifiManager != null;
        WifiInfo info = wifiManager.getConnectionInfo();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT)
            ssid = info.getSSID();
        else
            ssid = info.getSSID().replace("\"", "");

        if (ssid.indexOf("unknown ssid") >= 0) {
            ConnectivityManager connManager = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            assert connManager != null;
            NetworkInfo networkInfo = connManager.getActiveNetworkInfo();
            if (networkInfo.isConnected()) {
                if (networkInfo.getExtraInfo() != null) {
                    ssid = networkInfo.getExtraInfo().replace("\"", "");
                }
            }
            //部分手机拿不到WiFi名称
            if (ssid.indexOf("unknown ssid") >= 0) {
                int networkId = info.getNetworkId();
                @SuppressLint("MissingPermission") List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
                for (WifiConfiguration config : configuredNetworks) {
                    if (config.networkId == networkId) {
                        ssid = config.SSID;
                        break;
                    }
                }
            }
        }

        return ssid;
    }

    /**
     * 反转Hex(大小端)
     * @param strHex
     * @return
     */
    public static String reversalHex(String strHex) {
        String strNewHex = "";
        for (int i = 0;i<strHex.length()/2;i++) {
            strNewHex = strHex.substring(i*2,(i+1)*2)+strNewHex;
        }

        return strNewHex;
    }
    
    
    public static String bytesToHexString(byte[] src){
        StringBuilder stringBuilder = new StringBuilder("");
        if (src == null || src.length <= 0) {  
            return null;  
        }  
        for (int i = 0; i < src.length; i++) {  
            int v = src[i] & 0xFF;  
            String hv = Integer.toHexString(v);
            if (hv.length() < 2) {  
                stringBuilder.append(0);  
            }  
            stringBuilder.append(hv);  
        }  
        return stringBuilder.toString().toUpperCase();
    }

    /**
     * Convert hex string to byte[]
     * @param hexString the hex string
     * @return byte[]
     */
    public static byte[] hexStringToBytes(String hexString) {
        if (hexString == null || hexString.equals("")) {
            return null;
        }
        hexString = hexString.toUpperCase();
        int length = hexString.length() / 2;
        char[] hexChars = hexString.toCharArray();
        byte[] d = new byte[length];
        for (int i = 0; i < length; i++) {
            int pos = i * 2;
            d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
        }
        return d;
    }

    /**
     * 异或取反校验
     * @param data
     * @return
     */
    public static String xorBytes(byte[] data) {
        byte ret = 0;
        for (int i = 0; i < data.length; i++) {
            ret ^= data[i]; // 对每个字节进行异或操作
        }
        ret = (byte) ~ret; // 对结果进行按位取反

        return String.format("%02X", ret & 0xFF);
    }

    /**
     * Convert char to byte
     * @param c char
     * @return byte
     */
    public static byte charToByte(char c) {
        return (byte) "0123456789ABCDEF".indexOf(c);
    }

    /**
     * float类型转换成16进制
     * @param changeData
     * @return
     */
    public static String hexadecimal(float changeData){
        return Integer.toHexString(Float.floatToIntBits(changeData));
    }

    /**
     * 16进制转换为float类型,无符号
     * @param changeData
     * @return
     */
    public static float hexToTen1(String changeData){
        return Float.intBitsToFloat(Integer.parseInt(changeData,16));
    }

    /**
     * 16进制转换为float类型 如果包含负数就要此方法
     * @param changeData
     * @return
     */
    public static float hexToTen2(String changeData){
        return Float.intBitsToFloat(new BigInteger(changeData, 16).intValue());
    }

    /**
     * 解析32位的16进制float数据
     * @param hex
     * @return
     */
    public static float hexToFloat(String hex){
        BigInteger b = new BigInteger(hex, 16);
        return Float.intBitsToFloat(b.intValue());
    }

    /**
     * 将float值转换为32位的16进制字符串
     *
     * @param value float值
     * @return float值的32位16进制表示
     */
    public static String floatToHex(float value) {
        // 获取float值的IEEE 754二进制表示（即int值）
        int intBits = Float.floatToIntBits(value);
        // 将int值转换为无符号的十六进制字符串
        // 注意：Integer.toHexString()会忽略int的符号位，这正是我们想要的
        String hex = Integer.toHexString(intBits);
        // 确保字符串长度为8（必要时在前面补零）
        // 因为float是32位，转换成十六进制后应该是8个字符
        return hex.length() == 8 ? hex : String.format("%08x", intBits);
    }



    /**
     * 二进制转16进制
     * @param bString
     * @return
     */
    public static String binaryString2hexString(String bString)
    {
        if (bString == null || bString.equals("") || bString.length() % 8 != 0)
            return null;
        StringBuffer tmp = new StringBuffer();
        int iTmp = 0;
        for (int i = 0; i < bString.length(); i += 4)
        {
            iTmp = 0;
            for (int j = 0; j < 4; j++)
            {
                iTmp += Integer.parseInt(bString.substring(i + j, i + j + 1)) << (4 - j - 1);
            }
            tmp.append(Integer.toHexString(iTmp));
        }

        String str = tmp.toString();
        if (str.length() < 2) {
            str = "0" + str;
        }
        return str;
    }

    /** 16进制转2进制 */
    public static String hexToBinary(String string) {
        // TODO Auto-generated method stub
        string = string.toUpperCase();
        String s="";
        for (int i = 0; i < string.length(); i++) {
            s+=g(string.charAt(i));
        }
        return s;
    }

    private static String g(char charAt) {


        // TODO Auto-generated method stub
        switch (charAt) {
            case '0':
                return "0000";
            case '1':
                return "0001";
            case '2':
                return "0010";
            case '3':
                return "0011";
            case '4':
                return "0100";
            case '5':
                return "0101";
            case '6':
                return "0110";
            case '7':
                return "0111";
            case '8':
                return "1000";
            case '9':
                return "1001";
            case 'A':
                return "1010";
            case 'B':
                return "1011";
            case 'C':
                return "1100";
            case 'D':
                return "1101";
            case 'E':
                return "1110";
            case 'F':
                return "1111";

        }
        return null;
    }

    /**
     * 32位，8个长度的字符串数据转为带正负符号的10进制
     * 例如FFFFFC13   结果是-1000
     */
    public short hexStringToShort(String hexString) {
        int value = Integer.parseInt(hexString, 16) & 0XFFFF;
        return (short) value;
    }

    /**
     * 获取版本号
     * @return 当前应用的版本号
     */
    public static String getVersion(Context con) {
        try {
            PackageManager manager = con.getPackageManager();
            PackageInfo info = manager.getPackageInfo(con.getPackageName(), 0);
            String version = info.versionName;
            return  version;
        } catch (Exception e) {
            e.printStackTrace();
            return "0.0";
        }
    }

    /**
     * 获取SDK版本�?
     * @return
     */
    @SuppressWarnings("deprecation")
    public static int getAndroidSDKVersion() {
        int version = 0;
        try {
            version = Integer.valueOf(Build.VERSION.SDK);
        } catch (NumberFormatException e) {
        }
        return version;
    }


    /** 根据路径读取图片资源，最省内存的方法 */
    public static Bitmap readBitMap(Context context, int resId){
        if (resId == 0) {
            return null;
        }
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inPreferredConfig = Bitmap.Config.RGB_565;
        opt.inPurgeable = true;
        opt.inInputShareable = true;
        //获取资源图片
        InputStream is = context.getResources().openRawResource(resId);
        return BitmapFactory.decodeStream(is,null,opt);
    }

    /** 字符串特定字符变色 */
    public static SpannableString matcherSearchText(String text,String keyword,int iTextStyle) {
        SpannableString ss = new SpannableString(text);
        int iStart = text.indexOf(keyword);
        ss.setSpan(new ForegroundColorSpan(iTextStyle), iStart,iStart+keyword.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return ss;
    }

    /** 对比两个版本大小的方法 */
    public static boolean contrastVersion(String current , String server){
        boolean upgrade = false;
        String[] currentVersion = current.split("\\.");
        String[] serverVersion = server.split("\\.");
        if (currentVersion.length != serverVersion.length)
            return upgrade;
        for(int i=0;i<currentVersion.length;i++){
            if(serverVersion.length>i){
                if(Integer.parseInt(currentVersion[i])<Integer.parseInt(serverVersion[i])){
                    upgrade = true;
                    break;
                }
            }
            if(serverVersion.length>i){
                if(Integer.parseInt(currentVersion[i])>Integer.parseInt(serverVersion[i])){
                    upgrade = false;
                    break;
                }
            }
        }
        return upgrade;
    }

    /** 计算物理量的方法 */
    public static String pictureNumber(int number) {
        String strNum = "0";
        if(number<5){
            strNum = "0";
        }else if(5<=number && number<15){
            strNum = "10";
        }else if(15<=number && number<25){
            strNum = "20";
        }else if(25<=number && number<35){
            strNum = "30";
        }else if(35<=number && number<45){
            strNum = "40";
        }else if(45<=number && number<55){
            strNum = "50";
        }else if(55<=number && number<65){
            strNum = "60";
        }else if(65<=number && number<75){
            strNum = "70";
        }else if(75<=number && number<85){
            strNum = "80";
        }else if(85<=number && number<95){
            strNum = "90";
        }else if(95<=number){
            strNum = "100";
        }

        return strNum;
    }

    //将时间转换为时间戳
    public static String dateToStamp(long s) throws Exception {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        long lt = s;
        Date date1 = new Date(lt);
        String res = simpleDateFormat.format(date1);
        return res;
    }

    public static boolean isActivityTop(Class cls,Context context){
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        String name = manager.getRunningTasks(1).get(0).topActivity.getClassName();
        return name.equals(cls.getName());
    }

    public static String replaceString(String string , String before, String after){
        String strString = string.replaceAll(before,after);
        return strString;
    }


}
