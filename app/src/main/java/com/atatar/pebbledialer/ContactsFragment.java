package com.atatar.pebbledialer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import de.timroes.android.listview.EnhancedListView;

public class ContactsFragment extends Fragment implements IServiceConnectedListener {

    private EnhancedListAdapter<Contact> mAdapter;
    private IDialerService dialerService;
    private final int PICK_CONTACT = 1;

    class ContactViewHolder {
        ImageView imageView;
        TextView nameTextView, numberTextView;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        mAdapter = new EnhancedListAdapter<Contact>(getLayoutInflater(savedInstanceState), R.layout.contact_list_item) {
            @Override
            protected void populateView(View view, Contact item) {
                ContactViewHolder holder;
                if (view.getTag() instanceof ContactViewHolder)
                    holder = (ContactViewHolder)view.getTag();
                else {
                    holder = new ContactViewHolder();
                    holder.imageView = (ImageView)view.findViewById(R.id.contactImage);
                    holder.nameTextView = (TextView)view.findViewById(R.id.nameTextView);
                    holder.numberTextView = (TextView)view.findViewById(R.id.numberTextView);
                    view.setTag(holder);
                }

                if (item.ImageUri != null)
                    holder.imageView.setImageURI(Uri.parse(item.ImageUri));
                else
                    holder.imageView.setImageResource(android.R.drawable.sym_action_call);

                holder.numberTextView.setText(item.Phone.Label + " " + item.Phone.Number);
                holder.nameTextView.setText(item.Name);
            }
        };

        ((MainActivity)getActivity()).addServiceConnectionListener(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.contacts_view, container, false);

        final EnhancedListView.Undoable emptyUndoable = new EnhancedListView.Undoable() { @Override public void undo() { } };
        EnhancedListView mListView = (EnhancedListView)view.findViewById(R.id.list);
        mListView.setDismissCallback(new EnhancedListView.OnDismissCallback() {
            @Override
            public EnhancedListView.Undoable onDismiss(EnhancedListView enhancedListView, int i) {
                if (dialerService == null) return emptyUndoable;

                final int position = i;
                final Contact item = mAdapter.getItem(position);
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
        return view;
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_contacts, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_add) {
            if (mAdapter.getCount() >= DialerService.MaxContacts) {
                Toast.makeText(getActivity().getApplicationContext(), "Can't add more contacts", Toast.LENGTH_LONG).show();
            } else {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType(Contacts.CONTENT_TYPE);
                startActivityForResult(intent, PICK_CONTACT);
            }
            return true;
        }

        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) return;

        switch (requestCode) {
            case PICK_CONTACT:
                final ContactInfo info = getContactInfo(data.getData());
                if (info == null) return;
                if (info.Numbers.length == 0) {
                    Toast.makeText(getActivity().getApplicationContext(), "No phone number for selected contact", Toast.LENGTH_LONG).show();
                    return;
                } else if (info.Numbers.length > 1) {
                    String[] stringList = new String[info.Numbers.length];
                    for (int i = 0; i < info.Numbers.length; i++) {
                        PhoneNumber nr = info.Numbers[i];
                        stringList[i] = nr.Label + "\n" + nr.Number;
                    }

                    new AlertDialog.Builder(getActivity())
                            .setTitle("Select phone number")
                            .setSingleChoiceItems(stringList, 0,
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int whichButton) {
                                            dialog.dismiss();
                                            int selectedPosition = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                                            info.Contact.Phone = info.Numbers[selectedPosition];
                                            mAdapter.add(info.Contact);
                                            dialerService.appendContact(info.Contact);
                                            dialog.dismiss();
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

    @Override
    public void onServiceConnected(IDialerService service) {
        dialerService = service;
        mAdapter.clear();
        for (Contact c : dialerService.getContacts()) mAdapter.add(c);
    }

    private ContactInfo getContactInfo(Uri data) {
        Cursor c = null;

        try {
            c = getActivity().getContentResolver().query(data, new String[]{Contacts._ID, Contacts.DISPLAY_NAME}, null, null, null);
            if (!c.moveToFirst()) return null;

            int columnIndex = c.getColumnIndex(Contacts._ID);
            long contactId = c.getLong(columnIndex);
            final Contact contact = new Contact();
            contact.Name = c.getString(c.getColumnIndex(Contacts.DISPLAY_NAME));
            c.close();

            c = getActivity().getContentResolver().query(Data.CONTENT_URI, new String[]{
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
}
