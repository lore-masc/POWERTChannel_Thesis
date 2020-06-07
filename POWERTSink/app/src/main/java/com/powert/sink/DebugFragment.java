package com.powert.sink;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Objects;
import java.util.regex.Pattern;

public class DebugFragment extends Fragment {
    private LinearLayout ll;
    private static final String AppDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/sink/";
    private static final String FILE_PATH = AppDir + "data.csv";
    private String overhead;
    private boolean recording;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ll = (LinearLayout) inflater.inflate(R.layout.debug_fragment, container, false);

        this.recording = false;

        // Create app dir
        File directory = new File(AppDir);
        if (!directory.exists())
            directory.mkdir();

        TextView save_message = ll.findViewById(R.id.textView);
        save_message.setText(save_message.getText() + " " + FILE_PATH);

        Button button = ll.findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final Button btn = (Button) view;
                String btn_state = btn.getTag().toString();

                recording = !recording;
                switch(btn_state) {
                    case "0":
                        @SuppressLint("StaticFieldLeak")
                        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void> () {
                            @RequiresApi(api = Build.VERSION_CODES.O)
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
                                TextView textView4 = ll.findViewById(R.id.textView4);
                                String textView4_txt = getResources().getString(R.string.overhead);
                                textView4.setText(textView4_txt + " " + overhead + " ms");
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
        });

        final Switch switch2 = ll.findViewById(R.id.switch2);

        switch2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean enable_loads = switch2.isChecked();
                if (enable_loads)
                    ll.findViewById(R.id.editText2).setEnabled(true);
                else
                    ll.findViewById(R.id.editText2).setEnabled(false);
            }
        });

        return ll;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void record() {
        File file = new File(FILE_PATH);
        if (file.exists())
            file.delete();

        boolean enable_frequencies = ((Switch) ll.findViewById(R.id.switch1)).isChecked();
        boolean enable_loads = ((Switch) ll.findViewById(R.id.switch2)).isChecked();
        boolean enable_cputemp = ((Switch) ll.findViewById(R.id.switch3)).isChecked();
        boolean enable_threads = ((Switch) ll.findViewById(R.id.switch4)).isChecked();
        boolean enable_ram = ((Switch) ll.findViewById(R.id.switch5)).isChecked();
        boolean enable_battery_temp = ((Switch) ll.findViewById(R.id.switch6)).isChecked();
        boolean enable_battery_volt = ((Switch) ll.findViewById(R.id.switch7)).isChecked();

        try{
            //get how many cores there are from function
            int numCores = Utils.getNumCores();

            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            BatteryReceiver batteryReceiver = new BatteryReceiver();
            Objects.requireNonNull(getActivity()).registerReceiver(batteryReceiver, filter);

            ActivityManager activityManager = (ActivityManager) getActivity().getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();

            FileOutputStream fileOutputStream = new FileOutputStream(file, true);
            OutputStreamWriter writer = new OutputStreamWriter(fileOutputStream);
            String header = "timestamp ";
            if (enable_frequencies) {
                header += "CPUID ";
                header = this.fixHeader(header, "CPUID", "cpu", numCores);
            }
            if (enable_loads)
                header += "cpuloads ";
            if (enable_cputemp)
                header += "cpu_temp ";
            if (enable_threads)
                header += "threads ";
            if (enable_ram)
                header += "ram ";
            if (enable_battery_temp)
                header += "battery_temp ";
            if (enable_battery_volt)
                header += "battery_power";

            header = header.replace(" ", ", ");
            header += "\n";

            writer.write(header);

            while (this.recording) {
                long t0 = System.currentTimeMillis();

                String data = "" + System.currentTimeMillis() + " ";
                if (enable_frequencies) {
                    ArrayList<String> cpu_freq = new ArrayList<>();
                    for (int i = 0; i < numCores; i++)
                        cpu_freq.add(Utils.get_param("/sys/devices/system/cpu/cpu" + i +"/cpufreq/scaling_cur_freq"));
                    data += TextUtils.join(" ", cpu_freq) + " ";
                }
                if (enable_loads) {
                    EditText editText = ll.findViewById(R.id.editText2);
                    int wait = Integer.parseInt(editText.getText().toString());

                    float cpu_load =  Utils.readCore(wait);
                    data += cpu_load + " ";
                }
                if (enable_cputemp) {
                    double cpu_temp = Double.parseDouble(Utils.get_param("/sys/class/thermal/thermal_zone0/temp")) / 1000.0;
                    data += cpu_temp + " ";
                }
                if (enable_threads) {
                    int threads = 0;
                    //                        .getRunningServices(Integer.MAX_VALUE).size();
                    for (Thread t : Thread.getAllStackTraces().keySet())
                        if (t.getState() == Thread.State.RUNNABLE) threads++;

                    data += threads + " ";
                }
                if (enable_ram) {
                    activityManager.getMemoryInfo(mi);
                    double availableMegs = mi.availMem / 0x100000L;
                    double used_ram = (double)mi.totalMem / 0x100000L - availableMegs;
                    data += used_ram + " ";
                }
                if (enable_battery_temp) {
                    double battery_temp = batteryReceiver.getTemp() / 10.0;
                    data += battery_temp + " ";
                }
                if (enable_battery_volt) {
                    double battery_power = batteryReceiver.getVoltage() / 1000.0;
                    data += battery_power;
                }

                data = data.replace(" ", ", ");
                data += "\n";

                writer.append(data);

                overhead = String.valueOf(System.currentTimeMillis() - t0);

                long delay = Long.parseLong(String.valueOf(((EditText) ll.findViewById(R.id.editText)).getText()));
                if (delay > 0) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            writer.close();
            fileOutputStream.close();
        } catch(IOException ex){
            ex.printStackTrace();
        }
    }

    private String fixHeader(String header, final String TAG, final String TARGET, int numCores) {
        for (int i = 0; i < numCores; i++)
            header = header.replace(TAG, TARGET + i + " " + TAG);
        return header.replace(" " + TAG, "");
    }
}
