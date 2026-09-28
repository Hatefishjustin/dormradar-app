package com.hlk.hlkradartool.view;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.hlk.hlkradartool.R;


public class AreaAddWindowAddDevice extends Dialog implements View.OnClickListener {
    private Context context;
    private Button confirmBtn;
    private Button cancelBtn;
    private TextView areaName;
    private View view1;
    private PeriodListener listener;
    private String defaultName = "";
    private String strCancel = "";
    private String strConfirm = "";
    private boolean isToast = false;
    public AreaAddWindowAddDevice(Context context) {
        super(context);
        this.context = context;
    }

    public AreaAddWindowAddDevice(Context context, int theme, PeriodListener listener) {
        super(context, theme);
        this.context = context;
        this.listener = listener;
    }

    public AreaAddWindowAddDevice(Context context, int theme, String titleName, PeriodListener listener, boolean isToast, String strCancel, String strConfirm) {
        super(context, theme);
        this.context = context;
        this.listener = listener;
        this.defaultName = titleName;
        this.isToast = isToast;
        this.strCancel = strCancel;
        this.strConfirm = strConfirm;
    }


    /****
     *
     * @author mqw
     *
     */
    public interface PeriodListener {
        public void cancelListener();
        public void refreshListener();
    }

    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.window_area_add_device);
        confirmBtn = (Button) findViewById(R.id.confirm_btn);
        cancelBtn = (Button) findViewById(R.id.cancel_btn);
        view1 = (View) findViewById(R.id.view1);
        areaName = (TextView) findViewById(R.id.areaName);
        areaName.setText(defaultName);
        confirmBtn.setOnClickListener(this);
        cancelBtn.setOnClickListener(this);
        if (isToast){
            cancelBtn.setVisibility(View.GONE);
            view1.setVisibility(View.GONE);
        } else{
            cancelBtn.setVisibility(View.VISIBLE);
            view1.setVisibility(View.VISIBLE);
        }


        if (!strCancel.equalsIgnoreCase(""))
            cancelBtn.setText(strCancel);
        if (!strConfirm.equalsIgnoreCase(""))
            confirmBtn.setText(strConfirm);

        setCancelable(false);
        setCanceledOnTouchOutside(false);

    }
    @Override
    public void onClick(View v) {
        // TODO Auto-generated method stub
        int id = v.getId();
        switch (id) {
            case R.id.cancel_btn:
                if(listener != null)
                    listener.cancelListener();
                dismiss();
                break;
            case R.id.confirm_btn:
                if(listener != null)
                    listener.refreshListener();
                dismiss();
                break;

            default:
                break;
        }
    }


    public void setMsgAndShow(String strTitle, PeriodListener periodListener, boolean isToast) {
        this.defaultName = strTitle;
        this.listener = periodListener;
        this.isToast = isToast;
        if (areaName != null)
            areaName.setText(defaultName);
        if (cancelBtn != null) {
            if (isToast)
                cancelBtn.setVisibility(View.GONE);
            else
                cancelBtn.setVisibility(View.VISIBLE);
        }

        if (!isShowing()) show();
    }

    public void setMsgAndShow(String strTitle, PeriodListener periodListener, boolean isToast, String strCancel, String strConfirm) {
        this.defaultName = strTitle;
        this.listener = periodListener;
        this.isToast = isToast;
        this.strCancel = strCancel;
        this.strConfirm = strConfirm;
        if (areaName != null)
            areaName.setText(defaultName);
        if (cancelBtn != null) {
            cancelBtn.setText(strCancel);
            confirmBtn.setText(strConfirm);
            if (isToast)
                cancelBtn.setVisibility(View.GONE);
            else
                cancelBtn.setVisibility(View.VISIBLE);
        }
        if (!isShowing()) show();
    }




}