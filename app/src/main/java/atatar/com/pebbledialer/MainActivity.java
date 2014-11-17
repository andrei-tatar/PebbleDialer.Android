package atatar.com.pebbledialer;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.util.ArrayList;

import de.timroes.android.listview.EnhancedListView;

public class MainActivity extends Activity {
    final int PICK_CONTACT = 1;
    private EnhancedListAdapter mAdapter;
    private boolean isServiceAlreadyRunning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EnhancedListView mListView = (EnhancedListView) findViewById(R.id.list);
        mAdapter = new EnhancedListAdapter(getLayoutInflater());

        mListView.setDismissCallback(new EnhancedListView.OnDismissCallback() {
            @Override
            public EnhancedListView.Undoable onDismiss(EnhancedListView enhancedListView, int i) {
                if (dialerService == null)
                    return new EnhancedListView.Undoable() {
                        @Override
                        public void undo() {

                        }
                    };

                final int position = i;
                final Contact item = (Contact) mAdapter.getItem(position);
                mAdapter.remove(position);
                dialerService.removeContact(position);

                return new EnhancedListView.Undoable() {
                    @Override
                    public void undo() {
                        dialerService.insertContact(position, item);
                        mAdapter.insert(position, item);
                    }
                };
            }
        });

        mListView.setUndoStyle(EnhancedListView.UndoStyle.MULTILEVEL_POPUP);
        mListView.setAdapter(mAdapter);
        mListView.setSwipeDirection(EnhancedListView.SwipeDirection.BOTH);
        mListView.enableSwipeToDismiss();
    }

    @Override
    protected void onStart() {
        super.onStart();
        isServiceAlreadyRunning = isMyServiceRunning(DialerService.class);
        bindService(new Intent(getApplicationContext(), DialerService.class), dialerServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (dialerService != null)
            unbindService(dialerServiceConnection );
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (dialerService != null) {
            MenuItem item = menu.findItem(R.id.action_enable);
            boolean shouldEnable = dialerService.getEnabled();
            if (shouldEnable && !isServiceAlreadyRunning) {
                startService(new Intent(getApplicationContext(),DialerService.class));
                isServiceAlreadyRunning = true;
            }

            item.setTitle(getString(shouldEnable ? R.string.service_enabled : R.string.service_disabled));

            item = menu.findItem(R.id.action_notifycalls);
            item.setChecked(dialerService.getNotifyCalls());
        }

        return super.onPrepareOptionsMenu(menu);
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (dialerService == null)
            return false;

        switch (id) {
            case R.id.action_installapp:
                Intent watchFaceInstallIntent = new Intent(Intent.ACTION_VIEW);
                watchFaceInstallIntent.setData(Uri.parse("pebble://appstore/5460b9da4a403f42b800004f"));
                watchFaceInstallIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                watchFaceInstallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getApplicationContext().startActivity(watchFaceInstallIntent);
                return true;
            case R.id.action_add:
                if (mAdapter.getCount() >= 20) {
                    Toast.makeText(getApplicationContext(), "Can't add more contacts", Toast.LENGTH_LONG).show();
                    return true;
                }

                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType(ContactsContract.Contacts.CONTENT_TYPE);
                startActivityForResult(intent, PICK_CONTACT);
                return true;
            case R.id.action_enable:
                if (item.getTitle().equals(getString(R.string.service_enabled))) {
                    stopService(new Intent(getApplicationContext(),DialerService.class));
                    item.setTitle(getString(R.string.service_disabled));
                    dialerService.setEnabled(false);
                }
                else {
                    startService(new Intent(getApplicationContext(),DialerService.class));
                    item.setTitle(getString(R.string.service_enabled));
                    dialerService.setEnabled(true);
                }
                return true;
            case R.id.action_notifycalls:
                dialerService.setNotifyCalls(item.isChecked());
                break;

            case R.id.action_test1:
                dialerService.testMethod(1);
                return true;
            case R.id.action_test2:
                dialerService.testMethod(2);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) return;

        switch (requestCode) {
            case PICK_CONTACT:
                final ContactInfo info = getContactInfo(data.getData());
                if (info == null) return;
                if (info.Numbers.length == 0) {
                    Toast.makeText(getApplicationContext(), "No phone number for selected contact", Toast.LENGTH_LONG).show();
                    return;
                } else if (info.Numbers.length > 1) {
                    String[] stringList = new String[info.Numbers.length];
                    for (int i = 0; i < info.Numbers.length; i++) {
                        PhoneNumber nr = info.Numbers[i];
                        stringList[i] = nr.Label + "\n" + nr.Number;
                    }

                    new AlertDialog.Builder(this)
                            .setTitle("Select phone number")
                            .setSingleChoiceItems(stringList, 0,
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int whichButton) {
                                            dialog.dismiss();
                                            int selectedPosition = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                                            info.Contact.Phone = info.Numbers[selectedPosition];
                                            mAdapter.add(info.Contact);
                                            dialerService.appendContact(info.Contact);
                                        }
                                    })
                            .show();
                } else {
                    info.Contact.Phone = info.Numbers[0];
                    mAdapter.add(info.Contact);
                    dialerService.appendContact(info.Contact);
                }

                break;
        }
    }

    private ContactInfo getContactInfo(Uri data) {
        Cursor c = null;

        try {
            c = getContentResolver().query(data, new String[]{Contacts._ID, Contacts.DISPLAY_NAME}, null, null, null);
            if (!c.moveToFirst()) return null;

            int columnIndex = c.getColumnIndex(Contacts._ID);
            long contactId = c.getLong(columnIndex);
            final Contact contact = new Contact();
            contact.Name = c.getString(c.getColumnIndex(Contacts.DISPLAY_NAME));
            c.close();

            c = getContentResolver().query(Data.CONTENT_URI, new String[]{
                            //Data._ID,
                            Data.MIMETYPE,
                            Phone.NUMBER,
                            Phone.LABEL,
                            Phone.TYPE,
                            Photo.PHOTO_THUMBNAIL_URI},
                    Data.CONTACT_ID + "=? AND (" + Data.MIMETYPE + "='" + Photo.CONTENT_ITEM_TYPE + "' OR " + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "')",
                    new String[]{String.valueOf(contactId)}, null);

            int numberIndex = c.getColumnIndex(Phone.NUMBER);
            int typeIndex = c.getColumnIndex(Phone.TYPE);
            int labelIndex = c.getColumnIndex(Phone.LABEL);
            int thumbIndex = c.getColumnIndex(Photo.PHOTO_THUMBNAIL_URI);
            int mimeTypeIndex = c.getColumnIndex(Data.MIMETYPE);

            final ArrayList<PhoneNumber> list = new ArrayList<PhoneNumber>();

            while (c.moveToNext()) {
                String number = c.getString(numberIndex);
                String typeString;
                String mimeType = c.getString(mimeTypeIndex);
                if (mimeType.equals(Photo.CONTENT_ITEM_TYPE)) {
                    String thumb = c.getString(thumbIndex);
                    if (thumb != null)
                        contact.ImageUri = thumb;
                    continue;
                }

                int type = c.getInt(typeIndex);
                if (type == Phone.TYPE_CUSTOM) {
                    typeString = c.getString(labelIndex);
                } else {
                    int rType = Phone.getTypeLabelResource(type);
                    typeString = getResources().getString(rType);
                }

                PhoneNumber cNumber = new PhoneNumber();
                cNumber.Label = typeString;
                cNumber.Number = number;
                list.add(cNumber);
            }

            ContactInfo info = new ContactInfo();
            info.Contact = contact;

            info.Numbers = list.toArray(new PhoneNumber[list.size()]);
            return info;
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    private IDialerService dialerService;

    public ServiceConnection dialerServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            dialerService =  ((DialerService.LocalBinder)binder).getService();


            Contact[] contacts = dialerService.getContacts();
            mAdapter.clear();
            for (Contact c : contacts) mAdapter.add(c);
            invalidateOptionsMenu();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            dialerService = null;
        }
    };
}
