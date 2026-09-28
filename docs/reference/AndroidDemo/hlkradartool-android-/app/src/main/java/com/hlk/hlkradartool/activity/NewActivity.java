package com.hlk.hlkradartool.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.hlk.hlkradartool.R;
import com.hlk.hlkradartool.data.ClientManager;
import com.hlk.hlkradartool.data.DataBean;
import com.inuker.bluetooth.library.Constants;
import com.smartIPandeInfo.data.MessageInfo;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class NewActivity extends BaseActivity{
    private final String TAG = NewActivity.class.getSimpleName();
    TextView view;
    String strNowDevMac = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new);

        strNowDevMac = getIntent().getStringExtra("deviceMac");

        view = findViewById(R.id.data_text);

        EventBus.getDefault().register(this);
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onReceiveMessageInfo(DataBean msg){
        view.setText(view.getText() + "\n" + msg.getMessage());
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (ClientManager.getClient().getConnectStatus(strNowDevMac) == Constants.STATUS_DEVICE_CONNECTED) {
            Log.e(TAG, strNowDevMac + ",控制页面即将关闭，主动断开连接-----------");
            ClientManager.getClient().disconnect(strNowDevMac);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        EventBus.getDefault().unregister(this);
    }
}
