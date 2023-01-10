package cn.blogss.iotcard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
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

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

import android_serialport_api.SerialPortManager;

/**
 * 待解决问题
 * 1. 如何获取物联卡的手机号码
 * 2. 如何判断物联卡是否欠费
 * 3. 物联卡被锁定是什么意思？
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "MainActivity";
    public static final String ACTION_SIM_STATE_CHANGED = "android.intent.action.SIM_STATE_CHANGED";
    private LocationManager locationManager;
    private SerialPortManager serialPortManager;
    private final SimStateReceive simStateReceive = new SimStateReceive();
    private EditText etCmd;
    private Button btSend;
    private TextView tvRes;

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

    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            updateToNewLocation(location);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        getLocationByGps();
//        getLocationByGpsContinuous();
        etCmd = findViewById(R.id.et_cmd);
        btSend = findViewById(R.id.bt_send);
        tvRes = findViewById(R.id.tv_res);
        btSend.setOnClickListener(this);

        serialPortManager = new SerialPortManager.Builder()
                .path("/dev/ttyUSB1")
                .baudrate(115200)
                .flags(0)
                .openListener(opened -> {
                    Log.i(TAG, "opened: " + opened);
                })
                .dataReceiveListener(data -> {
                    String res = new String(data, StandardCharsets.UTF_8);
                    tvRes.setText("收到的响应：" + res);
                })
                .build();

        registerReceiver(simStateReceive, new IntentFilter(ACTION_SIM_STATE_CHANGED));
    }

    private  void updateToNewLocation(Location location) {
        if (location != null) {
            double altitude = location.getAltitude();
            double longitude = location.getLongitude();
            Log.i(TAG, "updateToNewLocation: altitude = " + altitude + ", longitude = " + longitude);
            if (locationManager != null) {
                locationManager.removeUpdates(locationListener);
                locationManager = null;
            }
            if (locationListener != null) {
                locationListener = null;
            }
        } else {
            Toast.makeText(getApplicationContext(), "无法获取地理信息，请确认已开启定位权限并选择定位模式为GPS、WLAN和移动网络", Toast.LENGTH_SHORT).show();
        }
    }

    public void getLocationByGps(){
        locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
//        Location location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if(location != null){
            double altitude = location.getAltitude();
            double longitude = location.getLongitude();
            Log.i(TAG, "getLocationByGps: altitude = " + altitude + ", longitude = " + longitude);
        }else {
            Log.i(TAG, "getLocationByGps: location is null");
        }
    }

    public void getLocationByGpsContinuous(){
        locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 0, locationListener);
//        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000, 0, mLocationListener01);
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
        if(id == R.id.bt_send){
            String cmd = etCmd.getText().toString() + "\r";
            serialPortManager.sendString(cmd);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private static class FakeSignalStrengthsCallback extends TelephonyCallback implements TelephonyCallback.SignalStrengthsListener {
        @Override
        public void onSignalStrengthsChanged(@NonNull SignalStrength signalStrength) {
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
                        // 获取手机号码
                        String phoneNumber = getPhoneNumber(telephonyManager);
                        Log.i(TAG, "phoneNumber: " + phoneNumber);
                        // 获取iccid
                        String iccid = getIccid(telephonyManager);
                        Log.i(TAG, "iccid: " + iccid);
                        // 连接串口
                        serialPortManager.open();

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

    private String getPhoneNumber(TelephonyManager telephonyManager) {
        String phoneNumber = "N/A";
        return TextUtils.isEmpty(telephonyManager.getLine1Number()) ? phoneNumber : telephonyManager.getLine1Number();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(simStateReceive);
        serialPortManager.close();
    }
}