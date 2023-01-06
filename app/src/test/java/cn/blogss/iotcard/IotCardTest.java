package cn.blogss.iotcard;

import static org.junit.Assert.*;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class IotCardTest {
    private Context context;
    IotCard iotCard;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication();
        iotCard = new IotCard();
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void getLocationByGps() {
        iotCard.getLocationByGps(context);
    }

    @Test
    public void getLocationByBaseStation() {
        iotCard.getLocationByBaseStation(context);
    }
}