package atatar.com.pebbledialer;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;

/**
 * Created by X550L-User1 on 18-Nov-14.
 */
public class Util {
    public static boolean isMyServiceRunning(Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager)context.getSystemService(Activity.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
