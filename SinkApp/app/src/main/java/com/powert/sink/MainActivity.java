package com.powert.sink;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_PERMISSION_CODE = 1;
    private static final String AppDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/sink/";
    private static final String FILE_PATH = AppDir + "data.csv";
    private String overhead;
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
                        TextView textView4 = findViewById(R.id.textView4);
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

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void record() {
        File file = new File(FILE_PATH);
        if (file.exists())
            file.delete();

        try{
            float[] coreValues = new float[10];
            //get how many cores there are from function
            int numCores = this.getNumCores();
            for(byte i = 0; i < numCores; i++) {
                coreValues[i] = readCore(i);
            }

            FileOutputStream fileOutputStream = new FileOutputStream(file, true);
            OutputStreamWriter writer = new OutputStreamWriter(fileOutputStream);
            String header = "timestamp, CPUID CPULOAD cpu_temp, threads, ram, battery_temp, battery_power\n";
            header = this.fixHeader(header, "CPUID", "cpu", numCores);
            header = this.fixHeader(header, "CPULOAD", "cpuload", numCores);

            writer.write(header);

            while (this.recording) {
                long t0 = System.currentTimeMillis();
                IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                BatteryReceiver batteryReceiver = new BatteryReceiver();
                registerReceiver(batteryReceiver, filter);

                ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
//                        .getRunningServices(Integer.MAX_VALUE).size();
                int threads = 0;
                for (Thread t : Thread.getAllStackTraces().keySet()) {
                    if (t.getState() == Thread.State.RUNNABLE) threads++;
                }

                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                activityManager.getMemoryInfo(mi);
                double availableMegs = mi.availMem / 0x100000L;
                double used_ram = (double)mi.totalMem / 0x100000L - availableMegs;

                ArrayList<String> cpu_freq = new ArrayList<>();
                for (int i = 0; i < numCores; i++)
                    cpu_freq.add(get_param("/sys/devices/system/cpu/cpu" + i +"/cpufreq/scaling_cur_freq"));

                ArrayList<Float> cpu_load = new ArrayList<>();
                for (int i = 0; i < numCores; i++)
                    cpu_load.add(readCore(i));
                double cpu_temp = Double.parseDouble(get_param("/sys/class/thermal/thermal_zone0/temp")) / 1000.0;
                double battery_temp = batteryReceiver.getTemp() / 10.0;
                double battery_power = batteryReceiver.getVoltage() / 1000.0;

                String data = System.currentTimeMillis() + ", " +
                        TextUtils.join(", ", cpu_freq) + ", " +
                        TextUtils.join(", ", cpu_load) + ", " +
                        cpu_temp + ", " + threads + ", " + used_ram + ", " +
                        battery_temp + ", " + battery_power + "\n";

                writer.append(data);

                overhead = String.valueOf(System.currentTimeMillis() - t0);

                long delay = Long.parseLong(String.valueOf(((EditText) findViewById(R.id.editText)).getText()));
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

    //for multi core value
    private float readCore(int i) {
        /*
         * how to calculate multicore
         * this function reads the bytes from a logging file in the android system (/proc/stat for cpu values)
         * then puts the line into a string
         * then spilts up each individual part into an array
         * then(since he know which part represents what) we are able to determine each cpu total and work
         * then combine it together to get a single float for overall cpu usage
         */
        try {
            RandomAccessFile reader = new RandomAccessFile("/proc/stat", "r");
            //skip to the line we need
            for(int ii = 0; ii < i + 1; ++ii)
                reader.readLine();
            String load = reader.readLine();

            //cores will eventually go offline, and if it does, then it is at 0% because it is not being
            //used. so we need to do check if the line we got contains cpu, if not, then this core = 0
            if(load.contains("cpu")) {
                String[] toks = load.split(" ");

                //we are recording the work being used by the user and system(work) and the total info
                //of cpu stuff (total)
                //https://stackoverflow.com/questions/3017162/how-to-get-total-cpu-usage-in-linux-c/3017438#3017438

                long work1 = Long.parseLong(toks[1])+ Long.parseLong(toks[2]) + Long.parseLong(toks[3]);
                long total1 = Long.parseLong(toks[1])+ Long.parseLong(toks[2]) + Long.parseLong(toks[3]) +
                        Long.parseLong(toks[4]) + Long.parseLong(toks[5])
                        + Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);

                try {
                    //short sleep time = less accurate. But android devices typically don't have more than
                    //4 cores, and I'n my app, I run this all in a second. So, I need it a bit shorter
                    Thread.sleep(100);
                } catch (Exception e) {}

                reader.seek(0);
                //skip to the line we need
                for(int ii = 0; ii < i + 1; ++ii)
                    reader.readLine();
                load = reader.readLine();
                //cores will eventually go offline, and if it does, then it is at 0% because it is not being
                //used. so we need to do check if the line we got contains cpu, if not, then this core = 0%
                if(load.contains("cpu")) {
                    reader.close();
                    toks = load.split(" ");

                    long work2 = Long.parseLong(toks[1])+ Long.parseLong(toks[2]) + Long.parseLong(toks[3]);
                    long total2 = Long.parseLong(toks[1])+ Long.parseLong(toks[2]) + Long.parseLong(toks[3]) +
                            Long.parseLong(toks[4]) + Long.parseLong(toks[5])
                            + Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);

                    //here we find the change in user work and total info, and divide by one another to get our total
                    //seems to be accurate need to test on quad core
                    //https://stackoverflow.com/questions/3017162/how-to-get-total-cpu-usage-in-linux-c/3017438#3017438

                    return (float)(work2 - work1) / ((total2 - total1));
                } else {
                    reader.close();
                    return 0;
                }

            } else {
                reader.close();
                return 0;
            }
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }

        return 0;
    }

    private int getNumCores() {
        //Private Class to display only CPU devices in the directory listing
        class CpuFilter implements FileFilter {
            @Override
            public boolean accept(File pathname) {
                //Check if filename is "cpu", followed by one or more digits
                if(Pattern.matches("cpu[0-9]+", pathname.getName())) {
                    return true;
                }
                return false;
            }
        }

        try {
            //Get directory containing CPU info
            File dir = new File("/sys/devices/system/cpu/");
            //Filter to only list the devices we care about
            File[] files = dir.listFiles(new CpuFilter());
            //Return the number of cores (virtual CPU devices)
            return files.length;
        } catch(Exception e) {
            //Default to return 1 core
            return 1;
        }
    }

    private String fixHeader(String header, final String TAG, final String TARGET, int numCores) {
        for (int i = 0; i < numCores; i++)
            header = header.replace(TAG, TARGET + i + ", " + TAG);
        return header.replace(" " + TAG, "");
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
