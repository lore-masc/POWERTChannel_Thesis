package com.powert.powertnoiser;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private static final long READ_CORES = 20;
    private static final float ALARM_THRESHOLD = 0.6f;
    private static final long SINGLE_SAMPLE = 5000;
    private static int INITIAL_THREADS;
    private ModuleForwarder moduleForwarder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        final ArrayList<Long> timestamps = new ArrayList<>();
//        final ArrayList<Float> cpu = new ArrayList<>();
        final ArrayList<Long> durations = new ArrayList<>();
        try {
            this.moduleForwarder = new ModuleForwarder(getApplicationContext());
            Random random = new Random();
            float[] input = new float[1280];
            for (int i = 0; i < input.length; i++)
                input[i] = random.nextFloat();
            this.moduleForwarder.prepare(input);
        } catch (IOException e) {
            e.printStackTrace();
        }

        @SuppressLint("StaticFieldLeak")
        AsyncTask<Void, String, Void> cpu_usage = new AsyncTask<Void, String, Void>() {
            float min;

            @SuppressLint({"StaticFieldLeak", "WrongThread"})
            @Override
            protected Void doInBackground(Void... voids) {
                float sampled_workload;
                float risk_index = 0;
                boolean risk = false;

                INITIAL_THREADS = Utils.currentRunnableThreads();
                Log.d("NOISER", "THREAD INIZIALI: " + INITIAL_THREADS);

                publishProgress(String.valueOf(risk), String.valueOf(risk_index));

                while (true) {
                    risk_index = 0;
                    durations.clear();
//                    timestamps.clear();
//                    cpu.clear();
                    publishProgress(String.valueOf(risk), String.valueOf(risk_index*100));

                    // Wait first peak
                    do {
                        sampled_workload = Utils.readCore(READ_CORES);
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        if (sampled_workload >= ALARM_THRESHOLD) risk = true;
                    } while (!risk);

//                    Log.d("NOISER", "Peak reached");

                    // Periodic CPU usage evaluation
                    boolean up = false;
                    Long start_up = 0L, start_down = 0L;
                    Long last_added = System.currentTimeMillis();
                    while (risk) {
                        sampled_workload = Utils.readCore(READ_CORES);
//                        Log.d("NOISER", "Sampled: " + sampled_workload + " Last: " + (System.currentTimeMillis() - last_added));
//                        timestamps.add(System.currentTimeMillis());
//                        cpu.add(sampled_workload);

                        if (System.currentTimeMillis() - last_added > ((durations.size() > 0) ? durations.get(0) * 5 : 1000f)) {
                            risk = false;
                        }

                        if (!up && sampled_workload >= 0.5f) {
                            start_up = System.currentTimeMillis();
                            if (start_down != 0L) durations.add(start_up - start_down);
                            last_added = start_up;
                            up = true;
                        } else if (up && sampled_workload < 0.2f) {
                            start_down = System.currentTimeMillis();
                            if (start_up != 0L) durations.add(start_down - start_up);
                            last_added = start_down;
                            up = false;
                        }

                        if (durations.size() > 5) {
                            durations.remove(0);

                            roundUp(durations);
                            min = Collections.min(durations);
                            Log.d("NOISER", durations.toString());

                            float count = 0;
                            for (Long interval : durations) {
                                float deviation = getFractionalDigits(interval / min);
                                if (deviation <= 0.3f) {
                                    count++;
                                }
                            }
                            risk_index = count / durations.size();

                            if (risk_index >= 0.5f) {
                                Log.d("NOISER", "RISK DETECTED " + risk_index);
                                publishProgress(String.valueOf(risk), String.valueOf(risk_index * 100));
                            }
                        }
                    }
                    publishProgress(String.valueOf(risk), String.valueOf(risk_index*100));
                }

            }

            @SuppressLint("SetTextI18n")
            @Override
            protected void onProgressUpdate(String... values) {
                super.onProgressUpdate(values);
                ImageButton imageButton = findViewById(R.id.imageButton);
                TextView textView2 = findViewById(R.id.textView2);
                @SuppressLint("UseSwitchCompatOrMaterialCode") Switch switch1 = findViewById(R.id.switch1);
                RadioButton radioButton1 = findViewById(R.id.radioButton1);
                RadioButton radioButton2 = findViewById(R.id.radioButton2);
                boolean risk = Boolean.parseBoolean(values[0]);

                textView2.setText(values[1].substring(0, values[1].indexOf(".")) + "%");

                if (risk) {
                    imageButton.setVisibility(View.VISIBLE);

                    if (switch1.isChecked()) {
                        Log.d("RANDOMNOISER", "START NOISER for " + min * 10 + "ms");
                        if (radioButton2.isChecked()) {
                            new AsyncTask<Void, Void, Void>(){
                                @Override
                                protected Void doInBackground(Void... voids) {
                                    Long start = System.currentTimeMillis();
                                    while (System.currentTimeMillis() < start + SINGLE_SAMPLE) {
                                        Log.d("RENICER", "I'm renicing");
                                        Utils.reniceTop();
                                    }
                                    return null;
                                }
                            }.execute();
                        }
                        moduleForwarder.forward(SINGLE_SAMPLE);
                    }
//                    Log.d("NOISER", "THREAD STARTED");
                } else {
                    imageButton.setVisibility(View.INVISIBLE);
                    values[1] = "0.00";
                }

            }
        }.execute();
    }

    private static void roundUp(ArrayList<Long> toRound) {
        final int MULTIPLE = 50;
        for (int i = 0; i < toRound.size(); i++) {
            Long l = toRound.get(i);
            if (l % MULTIPLE == 0)    toRound.set(i, l);
            else toRound.set(i, (MULTIPLE - l % MULTIPLE) + l);
        }
    }

    private static float getFractionalDigits(float number) {
        int decimal = (int) number;
        return number - decimal;
    }
}