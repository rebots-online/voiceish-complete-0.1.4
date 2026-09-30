package com.voiceish.sim;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class SmsSentReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!"com.voiceish.sim.SMS_SENT".equals(intent.getAction())) return;
        SmsStore.get(context).completePart(intent.getStringExtra("sendId"), intent.getIntExtra("part", -1), intent.getStringExtra("token"), getResultCode());
    }
}
