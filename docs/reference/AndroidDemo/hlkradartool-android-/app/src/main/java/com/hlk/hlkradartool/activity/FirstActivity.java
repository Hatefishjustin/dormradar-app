package com.hlk.hlkradartool.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import com.hlk.hlkradartool.R;
import com.hlk.hlkradartool.util.PermissionsChecker;
import com.hlk.hlkradartool.util.SharedPreferencesUtil;
import com.hlk.hlkradartool.view.AreaAddWindowPermission;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class FirstActivity extends Activity {
    private String TAG="FirstActivity";
    private static final int REQUEST_CODE = 0; // 请求�?
    // �?�?的全部权�?
    @SuppressLint("InlinedApi")
    static final String[] PERMISSIONS = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,//安卓12，sdk31新出的权限，用于使用蓝牙扫描附件其他的蓝牙设备
            Manifest.permission.BLUETOOTH_CONNECT,//安卓12，sdk31新出的权限，用于连接之前已经配对过的蓝牙设备
            Manifest.permission.POST_NOTIFICATIONS
    };

    private PermissionsChecker mPermissionsChecker; // 权限�?测器

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPermissionsChecker = new PermissionsChecker(this);
    }

    public void checkPermission() {
        // 缺少权限�?, 进入权限配置页面
        if (mPermissionsChecker.lacksPermissions(PERMISSIONS)) {
            startPermissionsActivity();
        } else {
            gotoNextActivity();
        }
    }

    public void onResume() {
        super.onResume();

        checkPermission();
    }

    private void startPermissionsActivity() {
        PermissionsActivity.startActivityForResult(this, REQUEST_CODE, PERMISSIONS);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 拒绝�?, 关闭页面, 缺少主要权限, 无法运行
        if (requestCode == REQUEST_CODE && resultCode == PermissionsActivity.PERMISSIONS_DENIED) {
            Toast.makeText(this,getString(R.string.queshao_quanxian), Toast.LENGTH_SHORT).show();
            finish();
        } else {
                gotoNextActivity();
        }
    }


    /** 前往下一个页面 */
    private void gotoNextActivity() {
        startActivity(new Intent(FirstActivity.this, BLEListActivity.class));
        finish();
    }
}