package com.powert.sink;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;

public class BatteryReceiver extends BroadcastReceiver {
    int voltage = -1;
    int temp = -1;

    @Override
    public void onReceive(Context context, Intent intent) {
        temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
    }

    public int getTemp() {
        return this.temp;
    }

    public int getVoltage() {
        return this.voltage;
    }
}
