package com.powert.sink;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class MainActivity extends AppCompatActivity {
    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_PERMISSION_CODE = 1;
    private static final String AppDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/sink/";
    private static final String FILE_PATH = AppDir + "data.csv";
    private boolean recording;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.recording = false;

        // Create app dir
        File directory = new File(AppDir);
        if (!directory.exists())
            directory.mkdir();

        TextView save_message = findViewById(R.id.textView);
        save_message.setText(save_message.getText() + " " + FILE_PATH);

        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermission();
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermission();
        }
    }

    @SuppressLint("StaticFieldLeak")
    public void onButtonPress(View view) {
        final Button btn = (Button) view;
        String btn_state = btn.getTag().toString();

        this.recording = !this.recording;
        switch(btn_state) {
            case "0":
                AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void> () {
                    @Override
                    protected Void doInBackground(Void... voids) {
                        record();
                        return null;
                    }

                    @Override
                    protected void onPreExecute() {
                        btn.setText(getResources().getString(R.string.stop));
                        super.onPreExecute();
                    }

                    @Override
                    protected void onPostExecute(Void v) {
                        super.onPostExecute(v);
                    }
                };
                task.execute();
                break;
            case "1":
                btn.setText(getResources().getString(R.string.start));
                break;
        }
        btn.setTag(1 - Integer.parseInt(btn_state));
    }

    private void record() {
        File file = new File(FILE_PATH);
        if (file.exists())
            file.delete();

        try{
            FileOutputStream fileOutputStream = new FileOutputStream(file, true);
            OutputStreamWriter writer = new OutputStreamWriter(fileOutputStream);
            writer.write("timestamp, cpu0, cpu1, cpu2, cpu3, cpu_temp, cpu_load, ram, battery_temp, battery_power\n");

            while (this.recording) {
                IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                BatteryReceiver batteryReceiver = new BatteryReceiver();
                registerReceiver(batteryReceiver, filter);

                ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
//                        .getRunningServices(Integer.MAX_VALUE).size();
                int cpu_load = 0;
                for (Thread t : Thread.getAllStackTraces().keySet()) {
                    if (t.getState()==Thread.State.RUNNABLE) cpu_load++;
                }

                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                activityManager.getMemoryInfo(mi);
                double availableMegs = mi.availMem / 0x100000L;
                double used_ram = (double)mi.totalMem / 0x100000L - availableMegs;

                String cpu0_freq = get_param("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq");
                String cpu1_freq = get_param("/sys/devices/system/cpu/cpu1/cpufreq/scaling_cur_freq");
                String cpu2_freq = get_param("/sys/devices/system/cpu/cpu2/cpufreq/scaling_cur_freq");
                String cpu3_freq = get_param("/sys/devices/system/cpu/cpu3/cpufreq/scaling_cur_freq");
                double cpu_temp = Double.parseDouble(get_param("/sys/class/thermal/thermal_zone0/temp")) / 1000.0;
                double battery_temp = batteryReceiver.getTemp() / 10.0;
                double battery_power = batteryReceiver.getVoltage() / 1000.0;

                writer.append(System.currentTimeMillis() + ", " +
                        cpu0_freq + ", " + cpu1_freq + ", " + cpu2_freq + ", " + cpu3_freq + ", " +
                        cpu_temp + ", " + cpu_load + ", " + used_ram + ", " +
                        battery_temp + ", " + battery_power + "\n");

                try {
                    Thread.sleep(Long.parseLong(String.valueOf(((EditText) findViewById(R.id.editText)).getText())));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            writer.close();
            fileOutputStream.close();
        } catch(IOException ex){
            ex.printStackTrace();
        }
    }

    private static String get_param(String path) throws IOException {
        ProcessBuilder cmd;
        String[] cpu0 = {"/system/bin/cat", path};
        cmd = new ProcessBuilder(cpu0);
        Process process = cmd.start();

        BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
        StringBuilder cpu0_freq = new StringBuilder();
        cpu0_freq.append(bufferedReader.readLine());
        bufferedReader.close();
        return cpu0_freq.toString();
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                },
                REQUEST_PERMISSION_CODE);
    }
}
