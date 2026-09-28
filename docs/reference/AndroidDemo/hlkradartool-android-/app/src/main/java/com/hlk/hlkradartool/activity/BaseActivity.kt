package com.hlk.hlkradartool.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hlk.hlkradartool.R
import com.hlk.hlkradartool.util.StatusBarUtil
import com.hlk.hlkradartool.view.*


open class BaseActivity : AppCompatActivity() {
    protected lateinit var mContext: Context
    protected lateinit var mHandler: Handler


    protected lateinit var areaAddWindowHint: AreaAddWindowHint
    protected lateinit var areaGetPermissionWindow: AreaGetPermissionWindow
    protected lateinit var disconnectStatusHint: AreaAddWindowAddDevice
    public lateinit var loadingDialog: LoadingDialog
    protected lateinit var checkCtrPwdWindowDialog: CheckCtrPwdWindowDialog

    private var sharedPreferences: SharedPreferences? = null

    /**
     * 同级函数，类似于静态属性
     */
    companion object {
        private lateinit var baseActivity : BaseActivity
        fun getInstance():BaseActivity{
            return baseActivity
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mContext = this
        baseActivity = this
        mHandler = Handler()
        areaAddWindowHint = AreaAddWindowHint(mContext, R.style.dialog_style, null)
        areaGetPermissionWindow = AreaGetPermissionWindow(mContext, R.style.dialog_style, null)
        loadingDialog = LoadingDialog(mContext, R.style.dialog_style)
        setStatusBar(false)

        disconnectStatusHint = AreaAddWindowAddDevice(this, R.style.dialog_style, getString(R.string.lanya_duankai_tuihui_liebiao), object : AreaAddWindowAddDevice.PeriodListener {
            override fun cancelListener() {}
            override fun refreshListener() {
                startActivity(Intent(mContext, BLEListActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
        }, true, "", "")

        sharedPreferences = getSharedPreferences("SmartRoller", MODE_PRIVATE)

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    protected open fun setStatusBar(isColor:Boolean) {
        //这里做了两件事情，1.使状态栏透明并使contentView填充到状态栏 2.预留出状态栏的位置，防止界面上的控件离顶部靠的太近。这样就可以实现开头说的第二种情况的沉浸式状态栏了
        StatusBarUtil.setStatusBarColor(this,isColor)
    }

    protected fun showToast(str: String?) {
        mHandler.post { Toast.makeText(applicationContext, str, Toast.LENGTH_SHORT).show() }
    }


    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                val view = currentFocus
                hideKeyboard(ev, view, this@BaseActivity) //调用方法判断是否需要隐藏键盘
            }
            else -> {
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    /**
     * 根据传入控件的坐标和用户的焦点坐标，判断是否隐藏键盘，如果点击的位置在控件内，则不隐藏键盘
     *
     * @param view
     * 控件view
     * @param event
     * 焦点位置
     * @return 是否隐藏
     */
    fun hideKeyboard(event: MotionEvent, view: View?, activity: Activity) {
        try {
            if (view != null && view is EditText) {
                val location = intArrayOf(0, 0)
                view.getLocationInWindow(location)
                val left = location[0]
                val top = location[1]
                val right = (left
                        + view.getWidth())
                val bootom = top + view.getHeight()
                // 判断焦点位置坐标是否在空间内，如果位置在控件外，则隐藏键盘
                if (event.rawX < left || event.rawX > right || event.y < top || event.rawY > bootom) {
                    // 隐藏键盘
                    val token = view.getWindowToken()
                    val inputMethodManager = activity
                            .getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    inputMethodManager.hideSoftInputFromWindow(
                            token,
                            InputMethodManager.HIDE_NOT_ALWAYS
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    open fun saveValueBySharedPreferences(strKey: String?, iValue: String?) {
        val editor = sharedPreferences!!.edit()
        editor.putString(strKey, iValue!!)
        editor.commit()
    }

    open fun getStringBySharedPreferences(strKey: String?): String? {
        return sharedPreferences!!.getString(strKey, "")
    }

    // 隐藏软键盘
    fun hideSoftInputFromWindow(){
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

        imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    fun dip2px(dpValue: Float): Int {
        val scale = getScale(this)
        return (dpValue * scale + 0.5f).toInt()
    }

    private fun getScale(context: Context): Float {
        val fontScale = context.resources.displayMetrics.scaledDensity
        return findScale(fontScale)
    }

    private fun findScale(scale: Float): Float {
        var scale = scale
        if (scale <= 1) {
            scale = 1f
        } else if (scale <= 1.5) {
            scale = 1.5f
        } else if (scale <= 2) {
            scale = 2f
        } else if (scale <= 3) {
            scale = 3f
        }
        return scale
    }

}