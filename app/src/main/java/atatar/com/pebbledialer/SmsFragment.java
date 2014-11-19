package atatar.com.pebbledialer;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import de.timroes.android.listview.EnhancedListView;

public class SmsFragment extends Fragment implements IServiceConnectedListener {
    private IDialerService dialerService;
    private EnhancedListAdapter<String> mAdapter;

    private class SmsViewHolder {
        TextView smsTextView;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        mAdapter = new EnhancedListAdapter<String>(getLayoutInflater(savedInstanceState), R.layout.sms_list_item) {
            @Override
            protected void populateView(View view, String item) {
                SmsViewHolder holder;
                if (view.getTag() instanceof SmsViewHolder)
                    holder = (SmsViewHolder)view.getTag();
                else {
                    holder = new SmsViewHolder();
                    holder.smsTextView = (TextView)view.findViewById(R.id.smsTextView);
                    view.setTag(holder);
                }

                holder.smsTextView.setText(item);
            }
        };

        ((MainActivity)getActivity()).addServiceConnectionListener(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.sms_view, container, false);

        final EnhancedListView.Undoable emptyUndoable = new EnhancedListView.Undoable() { @Override public void undo() { } };
        EnhancedListView mListView = (EnhancedListView)view.findViewById(R.id.sms_list);
        mListView.setDismissCallback(new EnhancedListView.OnDismissCallback() {
            @Override
            public EnhancedListView.Undoable onDismiss(EnhancedListView enhancedListView, int i) {
                if (dialerService == null) return emptyUndoable;

                final int position = i;
                final String sms = mAdapter.getItem(position);
                mAdapter.remove(position);
                dialerService.removeMessage(position);

                return new EnhancedListView.Undoable() {
                    @Override
                    public void undo() {
                        dialerService.insertMessage(position, sms);
                        mAdapter.insert(position, sms);
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
            final EditText input = new EditText(getActivity());
            new AlertDialog.Builder(getActivity())
                    .setTitle("New Message")
                    .setMessage("Type the new message")
                    .setView(input)
                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            String message = input.getText().toString();
                            mAdapter.add(message);
                            dialerService.appendMessage(message);
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            dialog.dismiss();
                        }
                    }).show();
            return true;
        }

        return false;
    }

    @Override
    public void onServiceConnected(IDialerService service) {
        dialerService = service;
        mAdapter.clear();
        for (String c : dialerService.getMessages()) mAdapter.add(c);
    }
}