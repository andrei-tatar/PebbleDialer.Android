package com.atatar.pebbledialer;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.regex.Pattern;

/**
* Created by X550L-User1 on 08-Nov-14.
*/
class Contact implements Serializable {
    public String Name;
    public String ImageUri;
    public PhoneNumber Phone;

    public Contact() {
        Phone = new PhoneNumber();
    }
}

class PhoneNumber implements Serializable
{
    public String Number;
    public String Label;
    public int Type = 255;
}

class ContactInfo
{
    public Contact Contact;
    public PhoneNumber[] Numbers;
}

class Preferences implements Serializable {
    public ArrayList<Contact> Contacts;
    public ArrayList<String> Messages;
    public boolean RemoveAccents;

    public Preferences(){
        Contacts = new ArrayList<Contact>();
        Messages = new ArrayList<String>();
    }

    public enum SaveType {
        All,
        General,
        Contacts,
        Messages
    }

    public static Preferences load(Context context) {
        Preferences preferences = new Preferences();
        SharedPreferences prefs;

        prefs = context.getSharedPreferences("general", Context.MODE_PRIVATE);
        preferences.RemoveAccents = prefs.getBoolean("RemoveAccents", false);

        prefs = context.getSharedPreferences("contacts", Context.MODE_PRIVATE);
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

        prefs = context.getSharedPreferences("messages", Context.MODE_PRIVATE);
        count = prefs.getInt("Count", 0);
        for (int i=0; i<count; i++) {
            String message = prefs.getString("Message" + i, null);
            if (message == null) break;
            preferences.Messages.add(message);
        }

        return preferences;
    }

    public void save(SaveType type, Context context) {
        SharedPreferences.Editor editor;

        if (type == SaveType.All || type == SaveType.General) {
            editor = context.getSharedPreferences("general", Context.MODE_PRIVATE).edit();
            editor.putBoolean("RemoveAccents", RemoveAccents);
            editor.commit();
        }

        if (type == SaveType.All || type == SaveType.Contacts) {
            editor = context.getSharedPreferences("contacts", Context.MODE_PRIVATE).edit();
            editor.clear();
            int index = 0;
            editor.putInt("Count", Contacts.size());
            for (Contact c : Contacts)
                editor.putString("Contact" + (index++), c.Name + "^" + c.ImageUri + "^" + c.Phone.Number + "^" + c.Phone.Label);
            editor.commit();
        }

        if (type == SaveType.All || type == SaveType.Messages) {
            editor = context.getSharedPreferences("messages", Context.MODE_PRIVATE).edit();
            editor.clear();
            int index = 0;
            editor.putInt("Count", Messages.size());
            for (String c : Messages)
                editor.putString("Message" + (index++), c);
            editor.commit();
        }
    }
}
