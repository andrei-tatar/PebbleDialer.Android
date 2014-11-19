package atatar.com.pebbledialer;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.telephony.PhoneStateListener;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class DialerService extends Service implements IDialerService {
    final byte contactsSyncPacket = (byte)13;
    final byte messageSyncPacket = (byte)14;
    final byte notifyCallId = (byte)15;

    public static final int MaxContacts = 10;

    enum SyncType {
        AllExceptMessages,
        Contacts,
        History,
        Messages
    }

    enum SaveType {
        All,
        General,
        Contacts,
        Messages
    }

    private PebbleDataSender pebbleDataSender;

    private Preferences preferences;
    private String currentNumber = "";

    private PhoneStateListener phoneStateListener = new PhoneStateListener() {
        private boolean isRinging = false;

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            switch (state) {
                case TelephonyManager.CALL_STATE_IDLE:
                    isRinging = false;
                    PebbleDictionary hideCall = new PebbleDictionary();
                    hideCall.addUint8(0, (byte) 0xCA);
                    hideCall.addUint8(1, (byte) 0);
                    pebbleDataSender.sendData(hideCall);
                    break;

                case TelephonyManager.CALL_STATE_OFFHOOK:
                    if (!getPreferences().NotifyCalls) break;
                    PebbleKit.startAppOnPebble(getApplicationContext(), Constants.watchAppUuid);

                    PebbleDictionary showCall = new PebbleDictionary();
                    showCall.addUint8(0, (byte) 0xCA);
                    showCall.addUint8(1, (byte) 2);
                    if (!isRinging) {
                        showCall.addString(2, getName(currentNumber));
                        showCall.addString(3, currentNumber);
                    }

                    pebbleDataSender.sendData(showCall, notifyCallId, 3);
                    break;

                case TelephonyManager.CALL_STATE_RINGING:
                    if (!getPreferences().NotifyCalls) break;
                    PebbleKit.startAppOnPebble(getApplicationContext(), Constants.watchAppUuid);
                    isRinging = true;

                    PebbleDictionary showRingingCall = new PebbleDictionary();
                    showRingingCall.addUint8(0, (byte) 0xCA);
                    showRingingCall.addUint8(1, (byte) 1);
                    showRingingCall.addString(2, getName(incomingNumber));
                    showRingingCall.addString(3, incomingNumber);
                    currentNumber = incomingNumber;

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
                    sync(SyncType.AllExceptMessages);
                    break;
                case Constants.TYPE_REQUEST_MESSAGES:
                    if (!getPreferences().ServiceEnabled) {
                        PebbleKit.sendNackToPebble(context, i);
                        return;
                    }
                    sync(SyncType.Messages);
                    break;
                case Constants.TYPE_REQUEST_DIAL:
                    if (!getPreferences().ServiceEnabled) {
                        PebbleKit.sendNackToPebble(context, i);
                        return;
                    }

                    currentNumber = pebbleTuples.getString(Constants.KEY_PHONE_NUMBER);
                    Intent intent = new Intent(Intent.ACTION_CALL);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.setData(Uri.parse("tel:" + currentNumber));
                    context.startActivity(intent);
                    break;
                case Constants.TYPE_REQUEST_SEND_MESSAGE:
                    if (!getPreferences().ServiceEnabled) {
                        PebbleKit.sendNackToPebble(context, i);
                        return;
                    }

                    String phoneNumber = pebbleTuples.getString(1);
                    if (phoneNumber == null) return;

                    int hashCode = pebbleTuples.getUnsignedIntegerAsLong(2).intValue();
                    for (String message : getPreferences().Messages) {
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
        if (!getPreferences().ServiceEnabled) stopSelf();

        String phoneNumber = null;
        if (intent != null)
            phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);

        if (phoneNumber != null)
            currentNumber = phoneNumber;
        else
            sync(SyncType.AllExceptMessages);

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Context context = getApplicationContext();
        pebbleDataSender = new PebbleDataSender(getApplicationContext(), Constants.watchAppUuid);
        PebbleKit.registerReceivedDataHandler(context, dataReceiver);

        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
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

    private void sync(SyncType syncType) {
        if (syncType == SyncType.AllExceptMessages || syncType == SyncType.History)
            addDataPacketsToSync(getHistoryContacts(), true);
        if (syncType == SyncType.AllExceptMessages || syncType == SyncType.Contacts)
            addDataPacketsToSync(getPreferences().Contacts, false);
        if (syncType == SyncType.Messages)
            syncMessages();
    }

    private void syncMessages() {
        String[] messages = getPreferences().Messages.toArray(new String[getPreferences().Messages.size()]);
        int count = messages.length;
        for (int i=0; i<count; i+=2) {
            PebbleDictionary data = new PebbleDictionary();
            data.addUint8(Constants.KEY_TYPE, Constants.TYPE_MESSAGE);

            for (int j=0; j<2 && i+j<count; j++) {
                int index = i + j;
                String message = messages[index];
                String messageToSend = message.substring(0, Math.min(25, message.length()));

                byte byteIndex = (byte) index;
                if (index == count - 1) byteIndex |= Constants.MASK_IS_LAST;

                data.addUint8(Constants.KEY_MESSAGE_INDEX + j * 10, byteIndex);
                data.addString(Constants.KEY_MESSAGE_MESSAGE + j * 10, messageToSend);
                data.addUint32(Constants.KEY_MESSAGE_HASH + j * 10, message.hashCode());
            }

            pebbleDataSender.sendData(data, messageSyncPacket);
        }

        if (count == 0) {
            PebbleDictionary data = new PebbleDictionary();
            data.addUint8(Constants.KEY_TYPE, Constants.TYPE_MESSAGE);
            data.addUint8(Constants.KEY_MESSAGE_INDEX, (byte) (Constants.MASK_IS_LAST | Constants.MASK_IS_EMPTY));
            pebbleDataSender.sendData(data, messageSyncPacket);
        }
    }

    private void addDataPacketsToSync(List<Contact> contacts, boolean history) {
        int count = Math.min(MaxContacts, contacts.size());
        for (int i = 0; i < count; i += 2) {
            PebbleDictionary data = new PebbleDictionary();
            data.addUint8(Constants.KEY_TYPE, Constants.TYPE_CONTACT);

            for (int j=0; j<2 && i+j<count; j++) {
                int ii = i + j;
                Contact contact = contacts.get(ii);

                String name = contact.Name;
                name = name.substring(0, Math.min(20, name.length()));

                byte index = (byte)ii;
                if (ii == count-1) index |= Constants.MASK_IS_LAST;
                if (history) index |= Constants.MASK_IS_HISTORY;
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
            data.addUint8(Constants.KEY_CONTACT_INDEX, (byte) (Constants.MASK_IS_LAST | Constants.MASK_IS_EMPTY | (history ? Constants.MASK_IS_HISTORY : 0 )));
            pebbleDataSender.sendData(data, contactsSyncPacket);
        }
    }

    /*private String removeAccents(String text) {
        return text == null ? null :
                Normalizer.normalize(text, Normalizer.Form.NFD)
                        .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }*/

    private void load() {
        preferences = new Preferences();
        SharedPreferences prefs;

        prefs = getSharedPreferences("general", MODE_PRIVATE);
        preferences.ServiceEnabled = prefs.getBoolean("ServiceEnabled", true);
        preferences.NotifyCalls = prefs.getBoolean("NotifyCalls", false);

        prefs = getSharedPreferences("contacts", MODE_PRIVATE);
        int count = prefs.getInt("Count", 0);
        for (int i=0; i<count; i++) {
            String contact = prefs.getString("Contact" + i, null);
            if (contact == null) break;

            Contact c = new Contact();
            String[] parts = contact.split(Pattern.quote("^"));

            if (parts.length != 4) continue;

            c.Name = parts[0];
            c.ImageUri = parts[1];
            c.Phone.Number = parts[2];
            c.Phone.Label = parts[3];
            preferences.Contacts.add(c);
        }

        prefs = getSharedPreferences("messages", MODE_PRIVATE);
        count = prefs.getInt("Count", 0);
        for (int i=0; i<count; i++) {
            String message = prefs.getString("Message" + i, null);
            if (message == null) break;
            preferences.Messages.add(message);
        }
    }

    private void save(SaveType type) {
        SharedPreferences.Editor editor;

        if (type == SaveType.All || type == SaveType.General) {
            editor = getSharedPreferences("general", MODE_PRIVATE).edit();
            editor.putBoolean("ServiceEnabled", preferences.ServiceEnabled);
            editor.putBoolean("NotifyCalls", preferences.NotifyCalls);
            editor.commit();
        }

        if (type == SaveType.All || type == SaveType.Contacts) {
            editor = getSharedPreferences("contacts", MODE_PRIVATE).edit();
            editor.clear();
            int index = 0;
            editor.putInt("Count", preferences.Contacts.size());
            for (Contact c : preferences.Contacts)
                editor.putString("Contact" + (index++), c.Name + "^" + c.ImageUri + "^" + c.Phone.Number + "^" + c.Phone.Label);
            editor.commit();
        }

        if (type == SaveType.All || type == SaveType.Messages) {
            editor = getSharedPreferences("messages", MODE_PRIVATE).edit();
            editor.clear();
            int index = 0;
            editor.putInt("Count", preferences.Messages.size());
            for (String c : preferences.Messages)
                editor.putString("Message" + (index++), c);
            editor.commit();
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
    public String[] getMessages() {
        return getPreferences().Messages.toArray(new String[getPreferences().Messages.size()]);
    }

    @Override
    public void appendMessage(String message) {
        getPreferences().Messages.add(message);
        save(SaveType.Messages);
    }

    @Override
    public void insertMessage(int position, String message) {
        getPreferences().Messages.add(position, message);
        save(SaveType.Messages);
    }

    @Override
    public void removeMessage(int position) {
        getPreferences().Messages.remove(position);
        save(SaveType.Messages);
    }

    @Override
    public Contact[] getContacts() {
        return getPreferences().Contacts.toArray(new Contact[getPreferences().Contacts.size()]);
    }

    @Override
    public void appendContact(Contact contact) {
        getPreferences().Contacts.add(contact);
        save(SaveType.Contacts);
        if (getPreferences().ServiceEnabled)
            sync(SyncType.Contacts);
    }

    @Override
    public void removeContact(int position) {
        getPreferences().Contacts.remove(position);
        save(SaveType.Contacts);
        if (getPreferences().ServiceEnabled)
            sync(SyncType.Contacts);
    }

    @Override
    public void insertContact(int position, Contact contact) {
        getPreferences().Contacts.add(position, contact);
        save(SaveType.Contacts);
        if (getPreferences().ServiceEnabled)
            sync(SyncType.Contacts);
    }

    @Override
    public void setEnabled(boolean enabled) {
        getPreferences().ServiceEnabled = enabled;
        save(SaveType.General);
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
        save(SaveType.General);
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
    String[] getMessages();
    void appendMessage(String message);
    void insertMessage(int position, String message);
    void removeMessage(int position);

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