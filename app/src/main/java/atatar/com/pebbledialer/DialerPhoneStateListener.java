package atatar.com.pebbledialer;

import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

public class DialerPhoneStateListener extends PhoneStateListener {
    private final DialerService service;
    private boolean isRinging = false;

    public DialerPhoneStateListener(DialerService service) {
        this.service = service;
    }

    @Override
    public void onCallStateChanged(int state, String incomingNumber) {
        if (!service.getPreferences().NotifyCalls)
            return;

        switch (state) {
            case TelephonyManager.CALL_STATE_IDLE:
                isRinging = false;
                PebbleDictionary hideCall = new PebbleDictionary();
                hideCall.addUint8(0, (byte) 0xCA);
                hideCall.addUint8(1, (byte) 0);
                service.pebbleDataSender.sendData(hideCall);
                break;

            case TelephonyManager.CALL_STATE_OFFHOOK:
                PebbleKit.startAppOnPebble(service, Constants.watchAppUuid);

                PebbleDictionary showCall = new PebbleDictionary();
                showCall.addUint8(0, (byte) 0xCA);
                showCall.addUint8(1, (byte) 2);
                if (!isRinging) {
                    showCall.addString(2, Util.getName(service.currentNumber, service));
                    showCall.addString(3, service.currentNumber);
                }

                service.pebbleDataSender.sendData(showCall, service.notifyCallId, 3);
                break;

            case TelephonyManager.CALL_STATE_RINGING:
                PebbleKit.startAppOnPebble(service, Constants.watchAppUuid);
                isRinging = true;

                PebbleDictionary showRingingCall = new PebbleDictionary();
                showRingingCall.addUint8(0, (byte) 0xCA);
                showRingingCall.addUint8(1, (byte) 1);
                showRingingCall.addString(2, Util.getName(incomingNumber, service));
                showRingingCall.addString(3, incomingNumber);
                service.currentNumber = incomingNumber;

                service.pebbleDataSender.sendData(showRingingCall, service.notifyCallId, 3);
                break;
        }
    }
}
