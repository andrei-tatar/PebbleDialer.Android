package com.atatar.pebbledialer;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.telephony.SmsManager;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import org.json.JSONException;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

public class DialerService extends Service implements IDialerService {
    final byte contactsSyncPacket = (byte)16;
    final byte messageSyncPacket = (byte)14;
    final byte notifyCallId = (byte)15;

    public static final int MaxContacts = 10;

    protected PebbleDataSender pebbleDataSender;

    private Preferences preferences;
    protected String currentNumber = "";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            currentNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);

            if (intent.hasExtra(DialerConstants.EXTRA_JSON_DATA)) {
                try {
                    String jsonData = intent.getStringExtra(DialerConstants.EXTRA_JSON_DATA);
                    int transactionId = intent.getIntExtra(DialerConstants.EXTRA_TRANSACTION_ID, 0);
                    final PebbleDictionary data = PebbleDictionary.fromJson(jsonData);
                    dataReceived(data, transactionId);
                } catch (JSONException ignored) {
                }
            }
        }
        else
            sync(SyncType.AllExceptMessages);

        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        pebbleDataSender = new PebbleDataSender(this, DialerConstants.watchAppUuid);
    }

    protected Preferences getPreferences() {
        if (preferences == null) preferences = Preferences.load(this);
        return preferences;
    }

    @Override
    public void onDestroy() {
        pebbleDataSender.stop();
        super.onDestroy();
    }

    protected void sync(SyncType syncType) {
        if (syncType == SyncType.AllExceptMessages || syncType == SyncType.Contacts)
            addDataPacketsToSync(getPreferences().Contacts, false);
        if (syncType == SyncType.AllExceptMessages || syncType == SyncType.History)
            addDataPacketsToSync(Util.getHistoryContacts(this), true);
        if (syncType == SyncType.Messages)
            syncMessages();
    }

    private void dataReceived(PebbleDictionary pebbleTuples, int transactionId) {
        byte type = pebbleTuples.getUnsignedIntegerAsLong(DialerConstants.KEY_TYPE).byteValue();
        switch (type) {
            case DialerConstants.TYPE_REQUEST_CONTACTS:
                sync(SyncType.AllExceptMessages);
                break;
            case DialerConstants.TYPE_REQUEST_MESSAGES:
                sync(SyncType.Messages);
                break;
            case DialerConstants.TYPE_REQUEST_DIAL:
                currentNumber = pebbleTuples.getString(DialerConstants.KEY_PHONE_NUMBER);
                Intent intent = new Intent(Intent.ACTION_CALL);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setData(Uri.parse("tel:" + currentNumber));
                getApplicationContext().startActivity(intent);
                break;
            case DialerConstants.TYPE_REQUEST_SEND_MESSAGE:
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
        }

        PebbleKit.sendAckToPebble(getApplicationContext(), transactionId);
    }

    private void syncMessages() {
        String[] messages = getPreferences().Messages.toArray(new String[getPreferences().Messages.size()]);
        int count = messages.length;
        for (int i=0; i<count; i+=2) {
            PebbleDictionary data = new PebbleDictionary();
            data.addUint8(DialerConstants.KEY_TYPE, DialerConstants.TYPE_MESSAGE);

            for (int j=0; j<2 && i+j<count; j++) {
                int index = i + j;
                String message = messages[index];
                String messageToSend = message.substring(0, Math.min(25, message.length()));

                byte byteIndex = (byte) index;
                if (index == count - 1) byteIndex |= DialerConstants.MASK_IS_LAST;

                data.addUint8(DialerConstants.KEY_MESSAGE_INDEX + j * 10, byteIndex);
                data.addString(DialerConstants.KEY_MESSAGE_MESSAGE + j * 10, normalizeString(messageToSend));
                data.addUint32(DialerConstants.KEY_MESSAGE_HASH + j * 10, message.hashCode());
            }

            pebbleDataSender.sendData(data, messageSyncPacket);
        }

        if (count == 0) {
            PebbleDictionary data = new PebbleDictionary();
            data.addUint8(DialerConstants.KEY_TYPE, DialerConstants.TYPE_MESSAGE);
            data.addUint8(DialerConstants.KEY_MESSAGE_INDEX, (byte) (DialerConstants.MASK_IS_LAST | DialerConstants.MASK_IS_EMPTY));
            pebbleDataSender.sendData(data, messageSyncPacket);
        }
    }

    private void addDataPacketsToSync(List<Contact> contacts, boolean history) {
        int count = Math.min(MaxContacts, contacts.size());
        for (int i = 0; i < count; i += 2) {
            PebbleDictionary data = new PebbleDictionary();
            data.addUint8(DialerConstants.KEY_TYPE, DialerConstants.TYPE_CONTACT);

            for (int j=0; j<2 && i+j<count; j++) {
                int ii = i + j;
                Contact contact = contacts.get(ii);

                String name = contact.Name;
                name = name.substring(0, Math.min(20, name.length()));

                byte index = (byte)ii;
                if (ii == count-1) index |= DialerConstants.MASK_IS_LAST;
                if (history) index |= DialerConstants.MASK_IS_HISTORY;
                data.addUint8(DialerConstants.KEY_CONTACT_INDEX + j * 10, index);
                data.addString(DialerConstants.KEY_CONTACT_NAME + j * 10, normalizeString(name));
                data.addString(DialerConstants.KEY_CONTACT_PHONE + j * 10, contact.Phone.Number);
                data.addUint8(DialerConstants.KEY_CONTACT_TYPE + j * 10, (byte) contact.Phone.Type);
            }

            pebbleDataSender.sendData(data, contactsSyncPacket);
        }

        if (count == 0) {
            PebbleDictionary data = new PebbleDictionary();
            data.addUint8(DialerConstants.KEY_TYPE, DialerConstants.TYPE_CONTACT);
            data.addUint8(DialerConstants.KEY_CONTACT_INDEX, (byte) (DialerConstants.MASK_IS_LAST | DialerConstants.MASK_IS_EMPTY | (history ? DialerConstants.MASK_IS_HISTORY : 0 )));
            pebbleDataSender.sendData(data, contactsSyncPacket);
        }
    }

    private String normalizeString(String text) {
        if (!getPreferences().RemoveAccents)
            return text;

        return text == null ? null :
                Normalizer.normalize(text, Normalizer.Form.NFD)
                        .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
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
        getPreferences().save(Preferences.SaveType.Messages, this);
    }

    @Override
    public void insertMessage(int position, String message) {
        getPreferences().Messages.add(position, message);
        getPreferences().save(Preferences.SaveType.Messages, this);
    }

    @Override
    public void updateMessage(int position, String message) {
        getPreferences().Messages.set(position, message);
        getPreferences().save(Preferences.SaveType.Messages, this);
    }

    @Override
    public void removeMessage(int position) {
        getPreferences().Messages.remove(position);
        getPreferences().save(Preferences.SaveType.Messages, this);
    }

    @Override
    public Contact[] getContacts() {
        return getPreferences().Contacts.toArray(new Contact[getPreferences().Contacts.size()]);
    }

    @Override
    public void appendContact(Contact contact) {
        getPreferences().Contacts.add(contact);
        getPreferences().save(Preferences.SaveType.Contacts, this);
        sync(SyncType.Contacts);
    }

    @Override
    public void removeContact(int position) {
        getPreferences().Contacts.remove(position);
        getPreferences().save(Preferences.SaveType.Contacts, this);
        sync(SyncType.Contacts);
    }

    @Override
    public void insertContact(int position, Contact contact) {
        getPreferences().Contacts.add(position, contact);
        getPreferences().save(Preferences.SaveType.Contacts, this);
        sync(SyncType.Contacts);
    }

    @Override
    public boolean getRemoveAccents() {
        return getPreferences().RemoveAccents;
    }

    @Override
    public void setRemoveAccents(boolean removeAccents) {
        getPreferences().RemoveAccents = removeAccents;
        getPreferences().save(Preferences.SaveType.General, this);
    }
}