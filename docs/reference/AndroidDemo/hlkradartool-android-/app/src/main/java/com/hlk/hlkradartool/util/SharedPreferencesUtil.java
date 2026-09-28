package com.hlk.hlkradartool.util;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

public class SharedPreferencesUtil {

    public static void keepShared(SharedPreferences sharedPreferences,String key, String value) {
	Editor editor = sharedPreferences.edit();
	editor.putString(key, value);
	editor.commit();
    }

    public static void keepShared(SharedPreferences sharedPreferences,String key, Integer value) {
	Editor editor = sharedPreferences.edit();
	editor.putInt(key, value);
	editor.commit();
    }

    public static void keepShared(SharedPreferences sharedPreferences,String key, long value) {
	Editor editor = sharedPreferences.edit();
	editor.putLong(key, value);
	editor.commit();
    }

    public static void keepShared(SharedPreferences sharedPreferences,String key, int value) {
	Editor editor = sharedPreferences.edit();
	editor.putInt(key, value);
	editor.commit();
    }

    /**
     * ������ѡ������
     * 
     * @param key
     * @param value
     */
    public static void keepShared(SharedPreferences sharedPreferences,String key, boolean value) {
	Editor editor = sharedPreferences.edit();
	editor.putBoolean(key, value);
	editor.commit();
    }

    /**
     * ��ѯָ��key û�з���null
     * 
     * @param key
     * @return
     */
    public static String queryValue(SharedPreferences sharedPreferences,String key, String defvalue) {
	String value = sharedPreferences.getString(key, defvalue);
	// if ("".equals(value)) {
	// return "";
	// }

	return value;
    }

    /**
     * ��ѯָ��key û�з���null
     * 
     * @param key
     * @return
     */
    public static String queryValue(SharedPreferences sharedPreferences,String key) {
	String value = sharedPreferences.getString(key, "");
	if ("".equals(value)) {
	    return "";
	}

	return value;
    }

    public static Integer queryIntValue(SharedPreferences sharedPreferences,String key) {
	int value = sharedPreferences.getInt(key, 0);

	return value;
    }

    /**
     * 
     * @param key
     * @return
     */
    public static boolean queryBooleanValue(SharedPreferences sharedPreferences,String key) {
	return sharedPreferences.getBoolean(key, false);
    }

    /**
     * 
     * @param key
     * @return
     */
    public static long queryLongValue(SharedPreferences sharedPreferences,String key) {
	return sharedPreferences.getLong(key, 0);
    }


    /**
     * ����Key�����
     * 
     * @param key
     */
    public static void deleteValue(SharedPreferences sharedPreferences,String key) {
    	sharedPreferences.edit().remove(key).commit();
    }
}
