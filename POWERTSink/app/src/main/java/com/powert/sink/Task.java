package com.powert.sink;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Process;
import android.util.Log;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.Arrays;

public class Task extends AsyncTask<Void, String, Void> {
    private final long WAIT_START;            // [ms]
    private final long READ_CORES = 20;              // [ms]
    private final float BIT_THRESHOLD_START = 0.50f;
    private final int PREAMBLE_SIZE = 5;
    private final int DIFFERENT_TIME_THRESHOLD = 6;
    private final int THRESHOLD_WATCHER = 4;
    private final float CHANNEL_FREE = 0.1f;

    @SuppressLint("StaticFieldLeak")
    LinearLayout ll;
    @SuppressLint("StaticFieldLeak")
    Context context;
    boolean recording;
    int count_different_times = 0;
    float threshold = BIT_THRESHOLD_START;
    boolean synch = false;
    boolean input_mode = false;
    int iterations;
    ArrayList<Float> cpu_sums = new ArrayList<>();
    int[] word = new int[8];
    int word_idx = 0;
    long time_overhead;
    float workload_overhead;

    Task (LinearLayout ll, Context context) {
        this.ll = ll;
        this.context = context;
        this.WAIT_START = Long.parseLong(((EditText) ll.findViewById(R.id.editTextNumber)).getText().toString());
    }

    public void setRecording(boolean recording) {
        this.recording = recording;
    }

    public boolean isRecording() {
        return recording;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected Void doInBackground(Void... voids) {
        int last_bit = 0, current_bit = 0;
        String mode = this.context.getResources().getString(R.string.mode0);

        publishProgress("", "", "0", mode, "");

        // Check the channel
        float avarage_state;
        do {
            int N = 5;
            this.workload_overhead = 0;
            for (int i = 0; i < N; i++)
                this.workload_overhead += Utils.readCore(Math.round(500));
            avarage_state = this.workload_overhead / N;
            if (avarage_state > CHANNEL_FREE) {
                mode = this.context.getResources().getString(R.string.mode1);
                publishProgress("", "", String.valueOf(avarage_state), mode, "");
            }
        } while (avarage_state > CHANNEL_FREE && this.isRecording());

        mode = this.context.getResources().getString(R.string.mode2);

        ArrayList<Integer> preamble = new ArrayList<>();
        android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);

        publishProgress("", "", "0", mode, "");
        Log.d("SINK", "Workload overhead: " + workload_overhead);

        // Alignment
        ArrayList<Float> log_loads = new ArrayList();
        boolean peak = false;
        do {
            float start_workload = Math.max(0f, Utils.readCore(Math.round(READ_CORES)) - 0.01f);
            log_loads.add(start_workload);
            int n = log_loads.size();
            if (n > 1 && log_loads.get(n-2) < 0.5f && log_loads.get(n-1) > 0.95f)
                peak = true;
//            Log.d("SINK", String.valueOf(log_loads));
        } while (!peak && this.isRecording());

        mode = this.context.getResources().getString(R.string.mode3);

        log_loads.clear();

        while (this.isRecording()) {

            long p1 = System.currentTimeMillis();

            if (this.iterations >= 1) {
                Log.d("OVERHEAD", "" + this.time_overhead);
                try {
                    Thread.sleep(WAIT_START - this.time_overhead);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    Thread.sleep(WAIT_START - READ_CORES);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            long start = System.currentTimeMillis();
            float sum = Math.max(0f, Utils.readCore(Math.round(READ_CORES)) - 0.01f);

//                                Log.d("SINK1", "" + (System.currentTimeMillis() - start));
            this.cpu_sums.add(sum);

//                                float workload_variance = this.variance(this.cpu_sums);
//                                float workload_average = this.averageArray(this.cpu_sums);
//                                Log.d("SINK2", "" + (System.currentTimeMillis() - start));

            if (sum >= this.threshold) {
                current_bit = 1;
            } else {
                current_bit = 0;
            }

            publishProgress("", String.valueOf(this.threshold), String.valueOf(sum), mode, "");
//                                Log.d("SINK3", "" + (System.currentTimeMillis() - start));

            if (this.cpu_sums.size() > THRESHOLD_WATCHER) {
                this.cpu_sums.remove(0);
            }

            // synchronization
            if (!this.synch) {
                if (current_bit == last_bit) {
//                    this.threshold = this.averageArray(this.cpu_sums);
                    this.count_different_times = 0;
                    preamble.clear();
                }
                if (current_bit != last_bit) {
                    this.count_different_times++;
                    preamble.clear();

                    if (this.count_different_times > DIFFERENT_TIME_THRESHOLD) {
                        this.synch = true;
                        mode = this.context.getResources().getString(R.string.mode4);
                    }
                }
            } else {
                if (current_bit == last_bit && current_bit == 0) {
                    preamble.add(0);
                    if (preamble.size() >= PREAMBLE_SIZE) {
                        this.input_mode = true;
                        mode = this.context.getResources().getString(R.string.mode5);
                    }
                }
            }
//                                Log.d("SINK4", "i " + current_bit);
//                                Log.d("SINK5", "" + (System.currentTimeMillis() - start));

            // message
            if (this.input_mode) {
                this.word[this.word_idx] = current_bit;
                if (this.word_idx % 8 == 7) {
                    int dec = 0;
                    for (int i = 0; i < 8; i++)
                        dec += (this.word[i] == 1) ? Math.pow(2, 8-(i+1)) : 0;
                    Log.d("SINK6", "Message " + Arrays.toString(this.word));
                    publishProgress(String.valueOf(dec), String.valueOf(this.threshold), String.valueOf(sum), mode, String.valueOf((System.currentTimeMillis() - p1)));
                    this.word = new int[8];
                    this.word_idx = 0;
                } else
                    this.word_idx++;
            }

            last_bit = current_bit;

            if (this.iterations < 2) {
                this.time_overhead = System.currentTimeMillis() - start;
                this.iterations++;
            }

//                                Log.d("SINK7", "" + (System.currentTimeMillis() - start));
            Log.d("SINK8", "Total " + (System.currentTimeMillis() - p1));
        }

        return null;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected void onPostExecute(Void v) {
        super.onPostExecute(v);
        this.threshold = BIT_THRESHOLD_START;
        this.synch = false;
        this.input_mode = false;
        this.cpu_sums.clear();
        this.count_different_times = 0;
        this.word_idx = 0;
        this.iterations = 0;
    }

    @Override
    protected void onProgressUpdate(String... values) {
        super.onProgressUpdate(values);
        EditText editText3 = ll.findViewById(R.id.editText3);
        TextView textView7 = ll.findViewById(R.id.textView7);
        TextView textView8 = ll.findViewById(R.id.textView8);
        TextView textView9 = ll.findViewById(R.id.textView9);
        TextView textView10 = ll.findViewById(R.id.textView10);
        String time = "";

        if (!values[0].equals(""))
            editText3.append(String.valueOf(Character.toChars(Integer.parseInt(values[0]))[0]));

        textView7.setText("Threshold: " + values[1]);

        textView8.setText("Last workload: " + values[2]);

        if (!values[4].equals("")) {
            time = values[4];
            textView10.setText("Time last iteration: " + time);
        }

        textView9.setText(values[3]);

        if (values[3].equals(this.context.getResources().getString(R.string.mode0))) {
            textView9.setTextColor(Color.BLACK);
        } else if (values[3].equals(this.context.getResources().getString(R.string.mode1))) {
            textView9.setTextColor(Color.RED);
        } else if (values[3].equals(this.context.getResources().getString(R.string.mode2))) {
            textView9.setTextColor(Color.BLACK);
        } else if (values[3].equals(this.context.getResources().getString(R.string.mode3))) {
            textView9.setTextColor(Color.RED);
        } else if (values[3].equals(this.context.getResources().getString(R.string.mode4))) {
            textView9.setTextColor(Color.BLUE);
        } else if (values[3].equals(this.context.getResources().getString(R.string.mode5))) {
            textView9.setTextColor(Color.GREEN);
        }
    }

    private float averageArray(ArrayList<Float> arr) {
        float sum = 0.0f;
        int w = 0;
        for(int i = 0; i < arr.size(); i++) {
            w += (i+1);
            sum += arr.get(i) * (i+1);
//                                sum += arr.get(i);
        }
        return  sum / w;
    }

    public float variance(ArrayList<Float> arr) {
        float m = averageArray(arr);
        float sommaScartiQuad = 0;
        for(int i = 0; i < arr.size(); i++)
            sommaScartiQuad += (arr.get(i) -m) * (arr.get(i) -m);
        return sommaScartiQuad/arr.size();
    }
}
