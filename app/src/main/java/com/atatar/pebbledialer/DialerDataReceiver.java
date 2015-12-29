package com.atatar.pebbledialer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.getpebble.android.kit.Constants;

import java.util.UUID;

public class DialerDataReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent.getAction().equals(Constants.INTENT_APP_RECEIVE)) {
            final UUID receivedUuid = (UUID) intent.getSerializableExtra(Constants.APP_UUID);

            // Pebble-enabled apps are expected to be good citizens and only inspect broadcasts containing their UUID
            if (!DialerConstants.watchAppUuid.equals(receivedUuid))
                return;

            final int transactionId = intent.getIntExtra(Constants.TRANSACTION_ID, -1);
            final String jsonData = intent.getStringExtra(Constants.MSG_DATA);
            if (jsonData == null || jsonData.isEmpty()) return;

            Intent i = new Intent(context, DialerService.class);
            i.putExtra(DialerConstants.EXTRA_JSON_DATA, jsonData);
            i.putExtra(DialerConstants.EXTRA_TRANSACTION_ID, transactionId);
            context.startService(i);
        }
    }
}
