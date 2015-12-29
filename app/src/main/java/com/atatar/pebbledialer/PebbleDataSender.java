package com.atatar.pebbledialer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.util.Log;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

public class PebbleDataSender {
    private final Context context;
    private final UUID receiverUuid;
    private boolean sending;

    private BroadcastReceiver receiverAckHandler, receiverNAckHandler;

    private class Tuple {
        final int Id;
        final public PebbleDictionary Data;
        public int Retries;

        public Tuple(int id, PebbleDictionary data, int retries) {
            Data = data;
            Retries = retries;
            Id = id;
        }
    }

    private final Queue<Tuple> dataToSend = new LinkedList<Tuple>();

    public PebbleDataSender(Context ctx, UUID rUuid) {
        context = ctx;
        receiverUuid = rUuid;
        receiverAckHandler = PebbleKit.registerReceivedAckHandler(context, new PebbleKit.PebbleAckReceiver(DialerConstants.watchAppUuid) {
            @Override
            public void receiveAck(Context context, int transactionId) {
                Log.i("PDataSender", "Packet sent " + transactionId);
                if (dataToSend.size() == 0) {
                    Log.e("PDataSender", "Empty!");
                    return;
                }

                dataToSend.remove();
                if (dataToSend.size() > 0) {
                    Tuple tuple = dataToSend.peek();
                    PebbleKit.sendDataToPebbleWithTransactionId(context, receiverUuid, tuple.Data, tuple.Id);
                    Log.i("PDataSender", "Sending next packet " + tuple.Id);
                } else {
                    sending = false;
                    Log.i("PDataSender", "No more packets");
                }
            }
        });

        receiverNAckHandler = PebbleKit.registerReceivedNackHandler(context, new PebbleKit.PebbleNackReceiver(DialerConstants.watchAppUuid) {
            @Override
            public void receiveNack(final Context context, int transactionId) {
                Tuple lastSent = dataToSend.peek();
                if (lastSent == null || transactionId != lastSent.Id)
                    return;

                if (lastSent.Retries-- == 0) {
                    if (lastSent.Id == 0) {
                        Log.i("PDataSender", "Could not send packet " + transactionId);
                        dataToSend.remove();
                    } else {
                        Log.i("PDataSender", "Could not send packet " + transactionId + " (removing all after)");
                        Tuple clone[] = new Tuple[dataToSend.size()];
                        dataToSend.toArray(clone);
                        for (Tuple t : clone) {
                            if (t.Id == lastSent.Id)
                                dataToSend.remove(t);
                        }
                    }
                } else {
                    Log.i("PDataSender", "Could not send packet " + transactionId + " (retry = " + lastSent.Retries + ")");
                }

                if (dataToSend.size() > 0) {
                    //try again later
                    new android.os.Handler().postDelayed(
                        new Runnable() {
                            public void run() {
                                Tuple tuple = dataToSend.peek();
                                PebbleKit.sendDataToPebbleWithTransactionId(context, receiverUuid, tuple.Data, tuple.Id);
                                Log.i("PDataSender", "Sending packet " + tuple.Id);
                            }
                        }, 150);
                } else {
                    sending = false;
                    Log.i("PDataSender", "No more packets");
                }
            }
        });
    }

    public void sendData(PebbleDictionary data, int transactionId, int retries) {
        Tuple tuple = new Tuple(transactionId, data, retries);
        dataToSend.add(tuple);
        if (!sending) {
            sending = true;
            Log.i("PDataSender", "Sending start packet " + tuple.Id);
            PebbleKit.sendDataToPebbleWithTransactionId(context, receiverUuid, tuple.Data, tuple.Id);
        }
    }

    public void sendData(PebbleDictionary data, int transactionId) {
        sendData(data, transactionId, 3);
    }

    public void sendData(PebbleDictionary data) {
        sendData(data, 0, 3);
    }

    public void stop() {
        context.unregisterReceiver(receiverAckHandler);
        context.unregisterReceiver(receiverNAckHandler);
    }
}
