package atatar.com.pebbledialer;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.ContactsContract;

import java.util.ArrayList;
import java.util.List;

public class Util {
    public static boolean isMyServiceRunning(Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Activity.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public static List<Contact> getHistoryContacts(Context context) {
        List<Contact> contacts = new ArrayList<Contact>();

        String[] projection = new String[]{
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                //CallLog.Calls.DURATION,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.DATE,
                //CallLog.Calls._ID
        };

        Cursor cursor = context.getContentResolver().query(CallLog.Calls.CONTENT_URI, projection, null, null, CallLog.Calls.DATE + " DESC");
        int numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER);
        int nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME);
        int typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE);
        while (cursor.moveToNext()) {
            Contact c = new Contact();
            String name = cursor.getString(nameIndex), phoneNumber = cursor.getString(numberIndex);
            if (phoneNumber == null)
                continue;

            if (name == null)
                name = "Unknown";

            boolean exists = false;
            for (Contact existing : contacts) {
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
            if (contacts.size() >= DialerService.MaxContacts)
                break;
        }
        cursor.close();

        return contacts;
    }

    public static boolean isRootAvailable(){
        Process p = null;
        try{
            p = Runtime.getRuntime().exec(new String[] {"su", "-c", "exit 0"});
            int result = p.waitFor();
            if(result != 0) throw new Exception("Root check result with exit command " + result);
            return true;
        }
        catch (Exception e) {
            return false;
        }finally {
            if(p != null)
                p.destroy();
        }
    }

    public static String getName(String phoneNumber, Context context) {
        if (phoneNumber == null || phoneNumber.length() == 0)
            return "Unknown";

        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
        Cursor c = context.getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
        if (c.moveToFirst()) {
            return c.getString(0);
        }

        return "Unknown";
    }
}
