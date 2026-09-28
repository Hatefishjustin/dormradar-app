package com.smart.hlk_b50.adapter

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.hlk.hlkradartool.R
import com.hlk.hlkradartool.activity.DemoApplication
import com.hlk.hlkradartool.data.SearchBLEDeviceInfo
import com.hlk.hlkradartool.util.BaseVolume


class BLEDeviceListAdapter(deviceList: ArrayList<SearchBLEDeviceInfo>, mContext: Context) : RecyclerView.Adapter<BLEDeviceListAdapter.ViewHolder>() {

    var deviceList = arrayListOf<SearchBLEDeviceInfo>()
    lateinit var onItemClickListener : OnItemClickListener
    lateinit var mContext: Context
    var isChecked = false
    var selectCount = 0

    init {
        this.deviceList = deviceList
        this.mContext = mContext
    }

    fun setCheckDevice(checked : Boolean){
        this.isChecked = checked
        selectCount = 0
        if (!checked){
            for (i in 0 until deviceList.size) {
                deviceList[i].isChecked = false
            }
        }
        notifyDataSetChanged()
    }

    fun getCheckDevice():Boolean{
        return isChecked
    }

    override fun onCreateViewHolder(p0: ViewGroup, p1: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(mContext).inflate(R.layout.item_notification_info, p0, false));
    }

    override fun onBindViewHolder(p0: ViewHolder, p1: Int) {
        var name = ""
        var keyName = ""
        name = DemoApplication.getInstance().getValueBySharedPreferences(BaseVolume.EDIT_BLE_MAC+deviceList[p1].macAddress).toString()
        keyName = DemoApplication.getInstance().getValueBySharedPreferences(BaseVolume.EDIT_BLE_NAME + deviceList[p1].devName)
//        Log.e("TAG", "onBindViewHolder: " + name + "," + keyName)
        if(name.equals("")){
            if (keyName.equals("")){
                p0.tvName.text = deviceList[p1].devName
                deviceList[p1].nowName = deviceList[p1].devName
            }else{
                p0.tvName.text = keyName
                deviceList[p1].nowName = keyName
            }
        }else{
            p0.tvName.text = name
            deviceList[p1].nowName = name
        }
//        p0.tvName.setText(deviceList[p1].devName)
        p0.tvSignal.text = "RSSI:"+deviceList[p1].getiRssi()

        // 判断当前是否处于选择设备的状态
        if (isChecked) {
            p0.checkImage.visibility = View.VISIBLE
            p0.checkImage.setImageDrawable(mContext.getDrawable(R.drawable.device_normal_image))
            p0.llParent.setOnClickListener {
                if (p0.checkImage.tag.equals("false")){
                    // 计数，选择设备不能超过10个
                    if (selectCount >= 10){
                        Toast.makeText(mContext, mContext.getString(R.string.zuiduo_xuanze_10ge_shebei), Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    p0.checkImage.setImageDrawable(mContext.getDrawable(R.drawable.device_select_image))
                    p0.checkImage.tag = "true"
                    deviceList[p1].isChecked = true
                    selectCount++;
                }else{
                    p0.checkImage.setImageDrawable(mContext.getDrawable(R.drawable.device_normal_image))
                    p0.checkImage.tag = "false"
                    deviceList[p1].isChecked = false
                    if (selectCount > 0){
                        selectCount--;
                    }
                }
            }
            p0.imgEditName.setOnClickListener(null)
        }else{
            p0.checkImage.visibility = View.GONE
            p0.llParent.setOnClickListener {
                onItemClickListener.onItemClick(p1)
            }
            p0.imgEditName.setOnClickListener {
                onItemClickListener.onItemEditNameClick(p1 , deviceList[p1].macAddress , p0.tvName.getText().toString())
            }
            p0.llParent.setOnLongClickListener {
                onItemClickListener.onItemLongClick(deviceList[p1].devName , p0.tvName.getText().toString())
                true
            }
        }
        val strVer = deviceList[p1].strVerInfo
        var strUpgrade = ""
        // 如果不是2412的话再去加这个“等待升级”的提示
        if (!deviceList[p1].devName.contains("HLK-LD2412")&&!deviceList[p1].devName.contains("HLK-LD2451")){
            if (deviceList[p1].isAllowUpgrade){
                strUpgrade = mContext.getString(R.string.dengdai_shengji)
            }
        }
        if (strVer.equals("")) {
            p0.tvVerInfo.visibility = View.GONE
        }
        else {
            p0.tvVerInfo.text = "V $strVer $strUpgrade"
            p0.tvVerInfo.visibility = View.VISIBLE
        }

    }


    override fun getItemCount(): Int {
        return deviceList.size
    }


    /** 自定义的接口 */
    interface OnItemClickListener {
        fun onItemClick(position: Int)
        fun onItemEditNameClick(position: Int , mac : String , name : String)
        fun onItemLongClick(mac: String, name: String)
    }


    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var checkImage:ImageView
        lateinit var llParent: LinearLayout
        lateinit var tvName: TextView
        lateinit var tvSignal: TextView
        lateinit var tvVerInfo: TextView
        lateinit var imgEditName: ImageView

        init {
            checkImage = itemView.findViewById(R.id.check_image)
            llParent = itemView.findViewById(R.id.llParent)
            tvName = itemView.findViewById(R.id.tvName)
            tvSignal = itemView.findViewById(R.id.tvSignal)
            tvVerInfo = itemView.findViewById(R.id.tvVerInfo)
            imgEditName = itemView.findViewById(R.id.imgEditName)
        }
    }

}