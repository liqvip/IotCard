package cn.blogss.iotcard;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

public class IotCard {
    private static final String TAG = "IotCard";
    private LocationManager locationManager;
    private TelephonyManager telephonyManager;

    public void getLocationByGps(Context context){
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
//        Location location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if(location != null){
            double altitude = location.getAltitude();
            double longitude = location.getLongitude();
            System.out.println("getLocationByGps: altitude = " + altitude + ", longitude = " + longitude);
        }else {
            System.out.println("getLocationByGps: location is null");
        }
    }

    public void getLocationByBaseStation(Context context){
       telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

       if(telephonyManager.getCellLocation() instanceof GsmCellLocation) {
           GsmCellLocation gsmCellLocation = (GsmCellLocation) telephonyManager.getCellLocation();
           if(gsmCellLocation != null){
               String networkOperator = telephonyManager.getNetworkOperator();
               String mcc = networkOperator.substring(0, 3);
               String mnc = networkOperator.substring(3);
               int cid = gsmCellLocation.getCid();
               int lac = gsmCellLocation.getLac();
               System.out.println("getLocationByBaseStation: mcc = " + mcc + ", mnc = " + mnc + ", cid = " + cid + ", lac = " + lac);
           }else{
               System.out.println("getLocationByBaseStation: gsmCellLocation is null");
           }
       } else {
           System.out.println("getLocationByBaseStation: CellLocation is not GsmCellLocation");
       }
    }


}
