package atatar.com.pebbledialer;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

public class DialerService extends Service implements IDialerService {
    final byte contactsSyncPacket = (byte)13;
    final byte notifyCallId = (byte)15;

    enum SyncType {
        All,
        Contacts,
        History
    }

    private final static String contactsFileName = "contacts.data";
    private PebbleDataSender pebbleDataSender;

    private Preferences preferences;
    private TelephonyManager telephonyManager;
    private String calledNumber = "";

    private PhoneStateListener phoneStateListener = new PhoneStateListener() {
        private boolean isRinging = false;

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            switch (state) {
                case TelephonyManager.CALL_STATE_IDLE:
                    isRinging = false;
                    /*PebbleDictionary hideCall = new PebbleDictionary();
                    hideCall.addUint8(0, (byte) 0xCA);
                    hideCall.addUint8(1, (byte) 0);
                    pebbleDataSender.sendData(hideCall);*/
                    PebbleKit.closeAppOnPebble(getApplicationContext(), Constants.watchAppUuid);
                    break;

                case TelephonyManager.CALL_STATE_OFFHOOK:
                    PebbleDictionary showCall = new PebbleDictionary();
                    showCall.addUint8(0, (byte) 0xCA);
                    showCall.addUint8(1, (byte) 2);
                    if (!isRinging) {
                        showCall.addString(2, getName(calledNumber));
                        showCall.addString(3, calledNumber);
                    }

                    pebbleDataSender.sendData(showCall);
                    break;

                case TelephonyManager.CALL_STATE_RINGING:
                    if (!getPreferences().NotifyCalls) break;
                    isRinging = true;
                    PebbleKit.startAppOnPebble(getApplicationContext(), Constants.watchAppUuid);

                    PebbleDictionary showRingingCall = new PebbleDictionary();
                    showRingingCall.addUint8(0, (byte) 0xCA);
                    showRingingCall.addUint8(1, (byte) 1);
                    showRingingCall.addString(2, getName(incomingNumber));
                    showRingingCall.addString(3, incomingNumber);

                    pebbleDataSender.sendData(showRingingCall, notifyCallId, 3);
                    break;
            }
        }
    };

    private PebbleKit.PebbleDataReceiver dataReceiver = new PebbleKit.PebbleDataReceiver(Constants.watchAppUuid) {
        @Override
        public void receiveData(final Context context, int i, final PebbleDictionary pebbleTuples) {
            byte type = pebbleTuples.getUnsignedIntegerAsLong(Constants.KEY_TYPE).byteValue();
            switch (type) {
                case Constants.TYPE_REQUEST_CONTACTS:
                    if (!getPreferences().ServiceEnabled) {
                        PebbleKit.sendNackToPebble(context, i);
                        return;
                    }
                    syncContacts(SyncType.All);
                    break;
                case Constants.TYPE_REQUEST_DIAL:
                    if (!getPreferences().ServiceEnabled) {
                        PebbleKit.sendNackToPebble(context, i);
                        return;
                    }

                    calledNumber = pebbleTuples.getString(Constants.KEY_PHONE_NUMBER);
                    Intent intent = new Intent(Intent.ACTION_CALL);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.setData(Uri.parse("tel:" + calledNumber));
                    context.startActivity(intent);
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
    };

    private String getName(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() == 0)
            return "Unknown";

        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
        Cursor c = getApplicationContext().getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
        if (c.moveToFirst()) {
            return c.getString(0);
        }

        return "Unknown";
    }



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        syncContacts(SyncType.All);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Context context = getApplicationContext();
        pebbleDataSender = new PebbleDataSender(getApplicationContext(), Constants.watchAppUuid);
        PebbleKit.registerReceivedDataHandler(context, dataReceiver);

        telephonyManager = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    private Preferences getPreferences() {
        if (preferences == null) load();
        return preferences;
    }

    private  List<Contact> getHistoryContacts() {
        List<Contact> contacts = new ArrayList<Contact>();

        String[] projection = new String[] {
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                //CallLog.Calls.DURATION,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.DATE,
                //CallLog.Calls._ID
        };

        Context context = getApplicationContext();
        Cursor cursor = context.getContentResolver().query(CallLog.Calls.CONTENT_URI, projection, null, null, CallLog.Calls.DATE + " DESC");
        int numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER);
        int nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME);
        int typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE);
        while (cursor.moveToNext()) {
            Contact c = new Contact();
            String name = cursor.getString(nameIndex), phoneNumber = cursor.getString(numberIndex);
            if (name == null || phoneNumber == null)
                continue;

            boolean exists = false;
            for (Contact existing:contacts){
                if (existing.Phone.Number.endsWith(phoneNumber)) {
                    exists = true;
                    break;
                }
            }
            if (exists)
                continue;

            int type = cursor.getInt(typeIndex);

            c.Name = name;
            c.Phone = new PhoneNumber();
            c.Phone.Number = phoneNumber;

            switch (type) {
                case CallLog.Calls.INCOMING_TYPE:
                    c.Phone.Type = 0;
                    break;
                case CallLog.Calls.MISSED_TYPE:
                    c.Phone.Type = 1;
                    break;
                case CallLog.Calls.OUTGOING_TYPE:
                    c.Phone.Type = 2;
                    break;
            }

            contacts.add(c);
            if (contacts.size() >= 20)
                break;
        }
        cursor.close();

        return contacts;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void syncContacts(SyncType syncType) {
        if (syncType == SyncType.All || syncType == SyncType.History)
            addDataPacketsToSync(getHistoryContacts(), true);
        if (syncType == SyncType.All || syncType == SyncType.Contacts)
            addDataPacketsToSync(getPreferences().Contacts, false);
    }

    private void addDataPacketsToSync(List<Contact> contacts, boolean history) {
        int count = contacts.size();
        for (int i = 0; i < count; i += 2) {
            PebbleDictionary data = new PebbleDictionary();
            data.addUint8(Constants.KEY_TYPE, Constants.TYPE_CONTACT);

            for (int j=0; j<2 && i+j<count; j++) {
                int ii = i + j;
                Contact contact = contacts.get(ii);

                String name = contact.Name;
                name = name.substring(0, Math.min(20, name.length()));

                byte index = (byte)ii;
                if (ii == count-1) index |= 0x80;
                if (history) index |= 0x40;
                data.addUint8(Constants.KEY_CONTACT_INDEX + j * 10, index);
                data.addString(Constants.KEY_CONTACT_NAME + j * 10, name);
                data.addString(Constants.KEY_CONTACT_PHONE + j * 10, contact.Phone.Number);
                data.addUint8(Constants.KEY_CONTACT_TYPE+ j * 10, (byte)contact.Phone.Type);
            }

            pebbleDataSender.sendData(data, contactsSyncPacket);
        }

        if (count == 0) {
            PebbleDictionary data = new PebbleDictionary();
            data.addUint8(Constants.KEY_TYPE, Constants.TYPE_CONTACT);
            data.addUint8(Constants.KEY_CONTACT_INDEX, (byte) (0x80 | 0x20 | (history ? 0x40 : 0 )));
            pebbleDataSender.sendData(data, contactsSyncPacket);
        }
    }

    /*private String removeAccents(String text) {
        return text == null ? null :
                Normalizer.normalize(text, Normalizer.Form.NFD)
                        .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }*/

    private void load() {
        FileInputStream fis;
        try {
            fis = getApplicationContext().openFileInput(contactsFileName);

            ObjectInputStream is = new ObjectInputStream(fis);
            preferences = (Preferences) is.readObject();
            is.close();
        } catch (Exception ex) {
            ex.printStackTrace();
            preferences = new Preferences();
            preferences.ServiceEnabled = false;
            preferences.Contacts = new ArrayList<Contact>();
            preferences.NotifyCalls = true;
        }
    }

    private void save() {
        try {
            Context context = getApplicationContext();
            FileOutputStream fos = context.openFileOutput(DialerService.contactsFileName, Context.MODE_PRIVATE);
            ObjectOutputStream os = new ObjectOutputStream(fos);
            os.writeObject(preferences);
            os.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public class LocalBinder extends Binder {
        IDialerService getService() {
            // Return this instance of LocalService so clients can call public methods
            return DialerService.this;
        }
    }

    private final LocalBinder binder  = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public Contact[] getContacts() {
        return getPreferences().Contacts.toArray(new Contact[getPreferences().Contacts.size()]);
    }

    @Override
    public void appendContact(Contact contact) {
        getPreferences().Contacts.add(contact);
        save();
        if (getPreferences().ServiceEnabled)
            syncContacts(SyncType.Contacts);
    }

    @Override
    public void removeContact(int position) {
        getPreferences().Contacts.remove(position);
        save();
        if (getPreferences().ServiceEnabled)
            syncContacts(SyncType.Contacts);
    }

    @Override
    public void insertContact(int position, Contact contact) {
        getPreferences().Contacts.add(position, contact);
        save();
        if (getPreferences().ServiceEnabled)
            syncContacts(SyncType.Contacts);
    }

    @Override
    public void setEnabled(boolean enabled) {
        getPreferences().ServiceEnabled = enabled;
        save();
    }

    @Override
    public boolean getEnabled() {
        return getPreferences().ServiceEnabled;
    }

    @Override
    public boolean getNotifyCalls() {
        return getPreferences().NotifyCalls;
    }

    @Override
    public void setNotifyCalls(boolean notifyCalls) {
        getPreferences().NotifyCalls = notifyCalls;
        save();
    }

    @Override
    public void testMethod(int i) {
        if (i == 1) {
            String incomingNumber = "0767515061";
            PebbleKit.startAppOnPebble(getApplicationContext(), Constants.watchAppUuid);

            PebbleDictionary data = new PebbleDictionary();
            data.addUint8(0, (byte) 0xCA);
            data.addUint8(1, (byte) 1); //1 - show call, 0 - hide
            data.addString(2, getName(incomingNumber));
            data.addString(3, incomingNumber);
            pebbleDataSender.sendData(data, notifyCallId, 3);
        } else if (i == 2) {
            PebbleKit.startAppOnPebble(getApplicationContext(), Constants.watchAppUuid);

            PebbleDictionary data = new PebbleDictionary();
            data.addUint8(0, (byte) 0xCA);
            data.addUint8(1, (byte) 2); //answered
            pebbleDataSender.sendData(data, notifyCallId, 3);
        }
    }
}

interface IDialerService {
    Contact[] getContacts();
    void appendContact(Contact contact);
    void insertContact(int position, Contact contact);
    void removeContact(int position);
    void setEnabled(boolean enabled);
    boolean getEnabled();

    boolean getNotifyCalls();
    void setNotifyCalls(boolean notifyCalls);

    void testMethod(int i);
}