package com.atatar.pebbledialer;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTabHost;
import android.view.Menu;
import android.view.MenuItem;

import java.util.ArrayList;

public class MainActivity extends FragmentActivity {
    private boolean isServiceAlreadyRunning;
    private IDialerService dialerService;
    private ArrayList<IServiceConnectedListener> serviceConnectedListeners = new ArrayList<IServiceConnectedListener>();

    @Override
    protected void onStart() {
        super.onStart();

        isServiceAlreadyRunning = Util.isMyServiceRunning(getApplicationContext(), DialerService.class);
        bindService(new Intent(getApplicationContext(), DialerService.class), dialerServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (dialerService != null)
            unbindService(dialerServiceConnection );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        FragmentTabHost mTabHost = (FragmentTabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup(this, getSupportFragmentManager(), R.id.realtabcontent);

        mTabHost.addTab(mTabHost.newTabSpec("contacts").setIndicator("Contacts"),
                ContactsFragment.class, null);
        mTabHost.addTab(mTabHost.newTabSpec("sms").setIndicator("Quick SMS"),
                SmsFragment.class, null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (dialerService != null) {
            MenuItem item = menu.findItem(R.id.action_removeaccents);
            item.setChecked(dialerService.getRemoveAccents());
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id) {
            case R.id.action_installapp:
                Intent watchFaceInstallIntent = new Intent(Intent.ACTION_VIEW);
                watchFaceInstallIntent.setData(Uri.parse("pebble://appstore/5460b9da4a403f42b800004f"));
                watchFaceInstallIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                watchFaceInstallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getApplicationContext().startActivity(watchFaceInstallIntent);
                return true;

            case R.id.action_removeaccents:
                if (dialerService == null) return false;
                item.setChecked(!item.isChecked());
                dialerService.setRemoveAccents(item.isChecked());
                return true;
        }

        return false;
    }

    public ServiceConnection dialerServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            dialerService = ((DialerService.LocalBinder)binder).getService();
            for (IServiceConnectedListener listener : serviceConnectedListeners)
                listener.onServiceConnected(dialerService);
            serviceConnectedListeners.clear();
            invalidateOptionsMenu();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            dialerService = null;
        }
    };

    public void addServiceConnectionListener(IServiceConnectedListener listener) {
        if (dialerService != null)
            listener.onServiceConnected(dialerService);
        else
            serviceConnectedListeners.add(listener);
    }
}
