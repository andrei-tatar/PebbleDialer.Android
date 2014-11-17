package atatar.com.pebbledialer;

import android.net.Uri;

import java.io.Serializable;
import java.util.ArrayList;

/**
* Created by X550L-User1 on 08-Nov-14.
*/
class Contact implements Serializable {
    public String Name;
    public String ImageUri;
    public PhoneNumber Phone;
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
    public boolean ServiceEnabled;
    public boolean NotifyCalls;
}
