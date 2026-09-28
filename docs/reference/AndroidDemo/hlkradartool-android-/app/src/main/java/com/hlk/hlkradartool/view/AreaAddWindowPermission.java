package com.hlk.hlkradartool.view;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.hlk.hlkradartool.R;


public class AreaAddWindowPermission extends Dialog implements View.OnClickListener {
    private Context context;
    private Button confirmBtn;
    private Button cancelBtn;
    private TextView titleTv,tvPrivacy;
    private PeriodListener listener;
    private String defaultName = "",title;
    private String strCancel = "";
    private String strConfirm = "";
    private boolean isToast = false;
    public AreaAddWindowPermission(Context context) {
        super(context);
        this.context = context;
    }

    public AreaAddWindowPermission(Context context, int theme, PeriodListener listener) {
        super(context, theme);
        this.context = context;
        this.listener = listener;
    }

    public AreaAddWindowPermission(Context context, int theme, String titleName, PeriodListener listener, String defaultName, boolean isToast, String strCancel, String strConfirm) {
        super(context, theme);
        this.context = context;
        this.listener = listener;
        this.defaultName = defaultName;
        this.title = titleName;
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
        this.setContentView(R.layout.window_area_permission);
        confirmBtn = (Button) findViewById(R.id.confirm_btn);
        cancelBtn = (Button) findViewById(R.id.cancel_btn);
        titleTv = (TextView) findViewById(R.id.dialog_title);
        titleTv.setText(title);
        confirmBtn.setOnClickListener(this);
        cancelBtn.setOnClickListener(this);
        tvPrivacy = (TextView) findViewById(R.id.tvPrivacy);
        tvPrivacy.setOnClickListener(this);
        if (isToast)
            cancelBtn.setVisibility(View.GONE);
        else
            cancelBtn.setVisibility(View.VISIBLE);

        if (!strCancel.equalsIgnoreCase(""))
            cancelBtn.setText(strCancel);
        if (!strConfirm.equalsIgnoreCase(""))
            confirmBtn.setText(strConfirm);

        setCancelable(false);
        setCanceledOnTouchOutside(false);
        tvPrivacy.setText(Html.fromHtml("<u>"+context.getString(R.string.yinsi_xieyi)+"</u>"));

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
            case R.id.tvPrivacy:
                break;

            default:
                break;
        }
    }


    public void setMsgAndShow(String strTitle,PeriodListener periodListener,boolean isToast) {
        this.title = strTitle;
        this.listener = periodListener;
        this.isToast = isToast;
        if (titleTv != null)
            titleTv.setText(title);
        if (cancelBtn != null) {
            if (isToast)
                cancelBtn.setVisibility(View.GONE);
            else
                cancelBtn.setVisibility(View.VISIBLE);
        }

        if (!isShowing()) show();
    }

    public void setMsgAndShow(String strTitle,PeriodListener periodListener,boolean isToast,String strCancel,String strConfirm) {
        this.title = strTitle;
        this.listener = periodListener;
        this.isToast = isToast;
        this.strCancel = strCancel;
        this.strConfirm = strConfirm;
        if (titleTv != null)
            titleTv.setText(title);
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