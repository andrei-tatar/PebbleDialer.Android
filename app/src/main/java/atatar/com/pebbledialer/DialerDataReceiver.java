package atatar.com.pebbledialer;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.telephony.SmsManager;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.io.IOException;
import java.util.ArrayList;

public class DialerDataReceiver extends PebbleKit.PebbleDataReceiver {

    private final DialerService service;

    protected DialerDataReceiver(DialerService service) {
        super(Constants.watchAppUuid);
        this.service = service;
    }

    @Override
    public void receiveData(Context context, int i, PebbleDictionary pebbleTuples) {
        byte type = pebbleTuples.getUnsignedIntegerAsLong(Constants.KEY_TYPE).byteValue();
        switch (type) {
            case Constants.TYPE_REQUEST_CONTACTS:
                if (!service.getPreferences().ServiceEnabled) {
                    PebbleKit.sendNackToPebble(context, i);
                    return;
                }
                service.sync(SyncType.AllExceptMessages);
                break;
            case Constants.TYPE_REQUEST_MESSAGES:
                if (!service.getPreferences().ServiceEnabled) {
                    PebbleKit.sendNackToPebble(context, i);
                    return;
                }
                service.sync(SyncType.Messages);
                break;
            case Constants.TYPE_REQUEST_DIAL:
                if (!service.getPreferences().ServiceEnabled) {
                    PebbleKit.sendNackToPebble(context, i);
                    return;
                }

                service.currentNumber = pebbleTuples.getString(Constants.KEY_PHONE_NUMBER);
                Intent intent = new Intent(Intent.ACTION_CALL);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setData(Uri.parse("tel:" + service.currentNumber));
                context.startActivity(intent);
                break;
            case Constants.TYPE_REQUEST_SEND_MESSAGE:
                if (!service.getPreferences().ServiceEnabled) {
                    PebbleKit.sendNackToPebble(context, i);
                    return;
                }

                String phoneNumber = pebbleTuples.getString(1);
                if (phoneNumber == null) return;

                int hashCode = pebbleTuples.getUnsignedIntegerAsLong(2).intValue();
                for (String message : service.getPreferences().Messages) {
                    if (message.hashCode() == hashCode) {
                        SmsManager sm = SmsManager.getDefault();
                        ArrayList<String> parts = sm.divideMessage(message);
                        sm.sendMultipartTextMessage(phoneNumber, null, parts, null, null);
                        break;
                    }
                }
                break;
            case Constants.TYPE_REQUEST_HANG_CALL:
                try {
                    //hang
                    Runtime.getRuntime().exec(new String[] {"su", "-c", "input keyevent 6"});
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case Constants.TYPE_REQUEST_ANSWER_CALL:
                try {
                    //answer
                    Runtime.getRuntime().exec(new String[] {"su", "-c", "input keyevent 5"});
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case Constants.TYPE_REQUEST_SILENCE:
                try {
                    //vol down
                    Runtime.getRuntime().exec(new String[] {"su", "-c", "input keyevent 164"});
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
        }

        PebbleKit.sendAckToPebble(context, i);
    }
}
