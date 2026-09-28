package com.hlk.hlkradartool.view;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.hlk.hlkradartool.R;

public class CheckCtrPwdWindowDialog extends Dialog implements View.OnClickListener {
	private Context context;
	private TextView confirmBtn;
	private TextView cancelBtn;
	private TextView tvDefaultPwd;
	private TextView tvPwdFail;
	private EditText edPwd;

	private PeriodListener periodListener;
	private String strDefaultPwd = "";
	private boolean isPwdFail = false;

	public PeriodListener getPeriodListener() {
		return periodListener;
	}

	public CheckCtrPwdWindowDialog(Context context) {
		super(context);
		this.context = context;
	}

	public CheckCtrPwdWindowDialog(Context context, int theme, PeriodListener periodListener) {
		super(context, theme);
		this.context = context;
		this.periodListener = periodListener;
	}


	
	/****
	 * 
	 * @author mqw
	 *
	 */
	public interface PeriodListener {
		public void refreshListener(String string);
		public void cancelListener();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.window_area_check_pwd);
		confirmBtn = (TextView) findViewById(R.id.confirm_btn);
		cancelBtn = (TextView) findViewById(R.id.cancel_btn);
		tvDefaultPwd = (TextView) findViewById(R.id.tvDefaultPwd);
		tvPwdFail = (TextView) findViewById(R.id.tvPwdFail);
		edPwd = (EditText) findViewById(R.id.areaName);
		edPwd.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {

			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				tvPwdFail.setVisibility(View.INVISIBLE);
			}

			@Override
			public void afterTextChanged(Editable s) {

			}
		});


		setCancelable(false);
		
		confirmBtn.setOnClickListener(this);
		cancelBtn.setOnClickListener(this);

//		tvDefaultPwd.setText(context.getString(R.string.moren_mima)+strDefaultPwd);
		if (isPwdFail)
			tvPwdFail.setVisibility(View.VISIBLE);
		else
			tvPwdFail.setVisibility(View.INVISIBLE);
		
	}


	public void updateContent(String strDefault,boolean isPwdFail) {
		this.strDefaultPwd = strDefault;
		this.isPwdFail = isPwdFail;

		if (!isShowing()) {
			show();
		}

//		tvDefaultPwd.setText(context.getString(R.string.moren_mima)+strDefaultPwd);
		if (isPwdFail)
			tvPwdFail.setVisibility(View.VISIBLE);
		else
			tvPwdFail.setVisibility(View.INVISIBLE);

	}
		 
	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		int id = v.getId();
		switch (id) {
		case R.id.cancel_btn:
			if (periodListener != null)
				periodListener.cancelListener();
			break;
		case R.id.confirm_btn:

			String strPwd = edPwd.getText().toString();
			if (strPwd.length() != 6) {
				Toast.makeText(context,context.getString(R.string.shuru_kongzhi_mima),Toast.LENGTH_SHORT).show();
				return;
			}
			dismiss();
			if (periodListener != null)
				periodListener.refreshListener(strPwd);

			break;

		default:
			break;
		}
	}
}