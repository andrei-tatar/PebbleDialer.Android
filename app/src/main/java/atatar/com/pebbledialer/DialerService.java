package atatar.com.pebbledialer;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.text.Normalizer;
import java.util.List;

public class DialerService extends Service implements IDialerService {
    final byte contactsSyncPacket = (byte)13;
    final byte messageSyncPacket = (byte)14;
    final byte notifyCallId = (byte)15;

    public static final int MaxContacts = 10;

    protected PebbleDataSender pebbleDataSender;

    private Preferences preferences;
    protected String currentNumber = "";

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
        pebbleDataSender = new PebbleDataSender(this, Constants.watchAppUuid);
        PebbleKit.registerReceivedDataHandler(this, new DialerDataReceiver(this));

        TelephonyManager telephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(new DialerPhoneStateListener(this), PhoneStateListener.LISTEN_CALL_STATE);
    }

    protected Preferences getPreferences() {
        if (preferences == null) preferences = Preferences.load(this);
        return preferences;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    protected void sync(SyncType syncType) {
        if (syncType == SyncType.AllExceptMessages || syncType == SyncType.History)
            addDataPacketsToSync(Util.getHistoryContacts(this), true);
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
                data.addString(Constants.KEY_MESSAGE_MESSAGE + j * 10, normalizeString(messageToSend));
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
                data.addString(Constants.KEY_CONTACT_NAME + j * 10, normalizeString(name));
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
        if (getPreferences().ServiceEnabled)
            sync(SyncType.Contacts);
    }

    @Override
    public void removeContact(int position) {
        getPreferences().Contacts.remove(position);
        getPreferences().save(Preferences.SaveType.Contacts, this);
        if (getPreferences().ServiceEnabled)
            sync(SyncType.Contacts);
    }

    @Override
    public void insertContact(int position, Contact contact) {
        getPreferences().Contacts.add(position, contact);
        getPreferences().save(Preferences.SaveType.Contacts, this);
        if (getPreferences().ServiceEnabled)
            sync(SyncType.Contacts);
    }

    @Override
    public void setEnabled(boolean enabled) {
        getPreferences().ServiceEnabled = enabled;
        getPreferences().save(Preferences.SaveType.General, this);
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
    public boolean setNotifyCalls(boolean notifyCalls) {
        if (notifyCalls && !Util.isRootAvailable())
            return false;

        getPreferences().NotifyCalls = notifyCalls;
        getPreferences().save(Preferences.SaveType.General, this);

        return true;
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