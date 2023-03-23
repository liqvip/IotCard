package cn.blogss.iotcard;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

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
    private TextView tvLevel, tvIccid, tvSimInfo;
    private Button btSimInfo, btAt;

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
        tvSimInfo = findViewById(R.id.tv_sim_info);
        btSimInfo = findViewById(R.id.bt_sim_info);
        btAt = findViewById(R.id.bt_at);

        btSimInfo.setOnClickListener(this);
        btAt.setOnClickListener(this);


        registerReceiver(simStateReceive, new IntentFilter(ACTION_SIM_STATE_CHANGED));

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            XXPermissions.with(this)
                    // 申请多个权限
                    .permission(Permission.READ_PHONE_STATE)
                    .request(new OnPermissionCallback() {

                        @Override
                        public void onGranted(@NonNull List<String> permissions, boolean allGranted) {
                            if (!allGranted) {
                                return;
                            }
                        }

                        @Override
                        public void onDenied(@NonNull List<String> permissions, boolean doNotAskAgain) {
                            if (doNotAskAgain) {
                                XXPermissions.startPermissionActivity(ICCIDActivity.this, permissions);
                            } else {
                            }
                        }
                    });
        }

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
        if(id == R.id.bt_sim_info) {
            try {
                String res1 = getIccid2();
                TelephonyManager telephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
                String res2 = getIccid3(telephonyManager);

                tvSimInfo.setText(res1 + res2);
            } catch (Exception e) {
                tvIccid.setText(e.getMessage());
            }
        } else if(id == R.id.bt_at) {
            getSimInfoByAt();
        }
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
                            //getIccid2();
                            //getIccid3(telephonyManager);
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

    private String getIccid2(){
        StringBuilder sb = new StringBuilder();
        SubscriptionManager sm = SubscriptionManager.from(this);

// it returns a list with a SubscriptionInfo instance for each simcard
// there is other methods to retrieve SubscriptionInfos (see [2])
        List<SubscriptionInfo> sis = sm.getActiveSubscriptionInfoList();

// getting first SubscriptionInfo
//        SubscriptionInfo si = sis.get(0);
        Log.i(TAG, "getIccid2: size = " + sis.size());
        for (SubscriptionInfo si: sis) {
            sb.append(si.getIccId());
            Log.i(TAG, "getIccid2: " + si.getIccId());
            Log.i(TAG, "getIccid2: number =  " + si.getNumber());
        }

        return sb.toString();
    }

    private String getIccid3(TelephonyManager telephonyManager) {
        String iccid = "N/A";
        iccid = telephonyManager.getSimSerialNumber();
        Log.i(TAG, "getIccid3: " + iccid);
        return iccid;
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

    public void getSimInfoByAt(){
        String filePath = "/dev/ttyUSB1";
        File file = new File(filePath);
        BufferedWriter bufferedWriter = null;
        BufferedReader bufferedReader = null;

        try {
            bufferedWriter = new BufferedWriter(new FileWriter(file));
            bufferedReader = new BufferedReader(new FileReader(file));

            String at = "at+iccid\\r";
            bufferedWriter.write(at);

            String res;
            while ((res = bufferedReader.readLine()) != null) {
                Log.i(TAG, "getSimInfoByAt: " + res);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if(bufferedReader != null) {
                    bufferedReader.close();
                }
                if(bufferedWriter != null) {
                    bufferedWriter.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
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