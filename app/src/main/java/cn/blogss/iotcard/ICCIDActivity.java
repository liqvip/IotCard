package cn.blogss.iotcard;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;

import android_serialport_api.SerialPortManager;

/**
 * 待解决问题
 * 1. 如何获取物联卡的手机号码
 * 2. 如何判断物联卡是否欠费
 * 3. 物联卡被锁定是什么意思？
 */
public class ICCIDActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "ICCIDActivity";
    public static final String ACTION_SIM_STATE_CHANGED = "android.intent.action.SIM_STATE_CHANGED";
    private final SimStateReceive simStateReceive = new SimStateReceive();
    private TextView tvLevel, tvIccid;

    //声明mlocationClient对象
    public AMapLocationClient mlocationClient;
    //声明mLocationOption对象
    public AMapLocationClientOption mLocationOption = null;

    private final PhoneStateListener phoneStateListener = new PhoneStateListener(){
        @Override
        public void onSignalStrengthChanged(int asu) {
            // Convert asu to dBm
            int dBm = -113 + 2 * asu;
            Log.i(TAG, "dBm: "+ dBm);
            int level = getLevel(dBm);
            Log.i(TAG, "level: " + level);
        }
    };

    private int getLevel(int dBm) {
        if(dBm > -55){
            return 4;
        }else if(between(dBm, -70, -55)){
            return 3;
        }else if(between(dBm, -85, -54)){
            return 2;
        }else if(between(dBm, -100, -84)){
            return 1;
        }
        return 0;
    }

    private boolean between(int cur, int min, int max){
        return Math.max(min, cur) == Math.min(cur, max);
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_iccid);
        tvLevel = findViewById(R.id.tv_level);
        tvIccid = findViewById(R.id.tv_iccid);
        registerReceiver(simStateReceive, new IntentFilter(ACTION_SIM_STATE_CHANGED));
    }

    /**
     * 获取基站信息
     * MCC，Mobile Country Code，移动国家代码（中国的为460）。
     * MNC，Mobile Network Code，移动网络号码（中国移动为0，中国联通为1，中国电信为2）。
     * LAC，Location Area Code，位置区域码。
     * CID，Cell Identity，基站编号。
     * BSSS，Base station signal strength，基站信号强度。
     * 在线免费基站定位查询：http://www.minigps.net/cellsearch.html
     */
    public void getBaseStationInfo() throws Exception{
        TelephonyManager telephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        int lac;
        int cid;
        String networkOperator = telephonyManager.getNetworkOperator();
        Log.i(TAG, "networkOperator: " + networkOperator);
        // networkOperator possibly is empty, StringIndexOutOfBoundsException
        String mcc = networkOperator.substring(0, 3);
        String mnc = networkOperator.substring(3);
        int phoneType = telephonyManager.getPhoneType();
        Log.i(TAG, "phoneType: " + phoneType);
        if(phoneType == TelephonyManager.PHONE_TYPE_NONE || phoneType == TelephonyManager.PHONE_TYPE_GSM){
            GsmCellLocation gsmCellLocation = (GsmCellLocation) telephonyManager.getCellLocation();
            if(gsmCellLocation != null){
                lac = gsmCellLocation.getLac();
                cid = gsmCellLocation.getCid();
                Log.i(TAG, "getBaseStationInfo: mcc = " + mcc + ", mnc = " + mnc + ", cid = " + cid + ", lac = " + lac);
            }else{
                Log.i(TAG, "getBaseStationInfo: gsmCellLocation is null");
            }

        }else if(phoneType == TelephonyManager.PHONE_TYPE_CDMA){
            CdmaCellLocation cdmaCellLocation = (CdmaCellLocation) telephonyManager.getCellLocation();
            if(cdmaCellLocation != null){
                lac = cdmaCellLocation.getNetworkId();
                cid = cdmaCellLocation.getBaseStationId();
                Log.i(TAG, "getBaseStationInfo: mcc = " + mcc + ", mnc = " + mnc + ", cid = " + cid + ", lac = " + lac);
            }else{
                Log.i(TAG, "getBaseStationInfo: cdmaCellLocation is null");
            }
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

    }

    /**
     * SIM 卡状态广播
     */
    private class SimStateReceive extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "sim card state changed.");
            String action = intent.getAction();
            Log.i(TAG, "action: " + action);

            if(TextUtils.equals(action, ACTION_SIM_STATE_CHANGED)){
                TelephonyManager telephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
                switch (telephonyManager.getSimState()){
                    case TelephonyManager.SIM_STATE_READY:
                        Log.i(TAG, "sim card ready.");
                        // 获取基站信息
                        try {
                            getBaseStationInfo();
                        } catch (Exception e){
                            e.printStackTrace();
                        }
                        // 获取信号强度
                        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTH);
                        // 获取iccid
                        try {
//                            String iccid = getIccid(telephonyManager);
                            getInfo();
//                            tvIccid.setText(iccid);
                        } catch (Exception e) {
                            tvIccid.setText(e.getMessage());
                        }

                        break;
                    case TelephonyManager.SIM_STATE_PIN_REQUIRED:
                    case TelephonyManager.SIM_STATE_PUK_REQUIRED:
                    case TelephonyManager.SIM_STATE_NETWORK_LOCKED:
                        Log.i(TAG, "sim card is locked.");
                        break;
                    case TelephonyManager.SIM_STATE_UNKNOWN:
                    case TelephonyManager.SIM_STATE_ABSENT:
                    case TelephonyManager.SIM_STATE_NOT_READY:
                    default:
                        Log.i(TAG, "sim card not ready.");
                }
            }
        }
    }


    /**
     * ICCID为IC卡的唯一识别号码，共有20位数字组成，其编码格式为：XXXXXX 0MFSS YYGXX XXXXX。
     * 前六位运营商代码
     * 中国移动的为：898600, 898602
     * 中国联通的为: 898601
     * 中国电信: 898603
     */
    private String getIccid(TelephonyManager telephonyManager) {
        String iccid = "N/A";
        return TextUtils.isEmpty(telephonyManager.getSimSerialNumber()) ? iccid : telephonyManager.getSimSerialNumber();
    }

    private void getInfo(){
        Uri uri = Uri.parse("content://telephony/siminfo");
        Cursor cursor = null;
        ContentResolver contentResolver = getApplicationContext().getContentResolver();
        cursor = contentResolver.query(uri,
                new String[]{"_id", "sim_id", "imsi","icc_id","number","display_name"}, "0=0",
                new String[]{}, null);
        if (null != cursor) {
            while (cursor.moveToNext()) {
                String icc_id = cursor.getString(cursor.getColumnIndex("icc_id"));
                String imsi_id = cursor.getString(cursor.getColumnIndex("imsi"));
                String phone_num = cursor.getString(cursor.getColumnIndex("number"));
                String display_name = cursor.getString(cursor.getColumnIndex("display_name"));
                int sim_id = cursor.getInt(cursor.getColumnIndex("sim_id"));
                int _id = cursor.getInt(cursor.getColumnIndex("_id"));
                Log.d("Q_M", "icc_id-->" + icc_id);
                Log.d("Q_M", "imsi_id-->" + imsi_id);
                Log.d("Q_M", "phone_num-->" + phone_num);
                Log.d("Q_M", "sim_id-->" + sim_id);
                Log.d("Q_M", "display_name-->" + display_name);
                tvLevel.setText("icc_id-->" + icc_id + "imsi_id-->" + imsi_id + "phone_num-->" + phone_num + "sim_id-->" + sim_id + "display_name-->" + display_name);
            }
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(simStateReceive);
        mlocationClient.onDestroy();
    }
}