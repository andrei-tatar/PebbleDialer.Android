package atatar.com.pebbledialer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class OutcallListener extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, Intent intent) {
        String number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);

        Intent mServiceIntent = new Intent(context, DialerService.class);
        mServiceIntent.putExtra(Intent.EXTRA_PHONE_NUMBER, number);
        context.startService(mServiceIntent);
    }
}
