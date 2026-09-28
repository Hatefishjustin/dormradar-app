package com.hlk.hlkradartool.view;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.hlk.hlkradartool.R;


public class AreaConfirmWindowHint extends Dialog implements View.OnClickListener {
	private Context context;
	private TextView tvConfirm;
	private TextView titleTv;
	private String defaultName = "",title;
	private boolean isToast = false;
	private PeriodListener listener;
	private String btnContent = "";
	public AreaConfirmWindowHint(Context context) {
		super(context);
		this.context = context;
	}

	public AreaConfirmWindowHint(Context context, int theme, String title , PeriodListener listener) {
		super(context, theme);
		this.context = context;
		this.title = title;
		this.listener = listener;
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
		this.setContentView(R.layout.window_confirm_hint);
		tvConfirm = (TextView) findViewById(R.id.tvConfirm);
		titleTv = (TextView) findViewById(R.id.dialog_title);
		titleTv.setText(title);
		tvConfirm.setOnClickListener(this);
		if(!btnContent.equals("")){
			tvConfirm.setText(btnContent);
			btnContent = "";
		}


		setCancelable(false);
		setCanceledOnTouchOutside(false);
		
	}
	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		int id = v.getId();
		switch (id) {
		case R.id.tvConfirm:
			listener.refreshListener();
			dismiss();
			break;
		default:
			break;
		}
	}


	public void setMsgAndShow(String strTitle) {
		this.title = strTitle;
		if (titleTv != null)
			titleTv.setText(title);
		if (!isShowing()){
			show();
		}
	}

	public void setMsgAndShow(String strTitle,String btnContent) {
		this.title = strTitle;
		this.btnContent = btnContent;
		if (titleTv != null)
			titleTv.setText(title);
		if (tvConfirm != null)
			tvConfirm.setText(btnContent);
		if (!isShowing()){
			show();
		}
	}





}