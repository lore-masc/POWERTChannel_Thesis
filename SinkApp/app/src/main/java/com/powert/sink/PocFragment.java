package com.powert.sink;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Arrays;

public class PocFragment extends Fragment {
    private final float WAIT_START = 500;            // [ms]
    private final long READ_CORES = 20;              // [ms]
    private final float BIT_THRESHOLD_START = 0.50f;
    private final int PREAMBLE_SIZE = 5;
    private final int DIFFERENT_TIME_THRESHOLD = 6;
    private final int THRESHOLD_WATCHER = 4;

    public PocFragment () {

    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final LinearLayout ll = (LinearLayout) inflater.inflate(R.layout.poc_fragment, container, false);
        final boolean[] recording = {false};

        final Button button2 = ll.findViewById(R.id.button2);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String btn_text = button2.getText().toString();
                if (btn_text.equals(getResources().getString(R.string.listen))) {
                    button2.setText(getResources().getString(R.string.stop));
                    recording[0] = true;

                    @SuppressLint("StaticFieldLeak")
                    AsyncTask<Void, String, Void> task = new AsyncTask<Void, String, Void>() {
                        int count_different_times = 0;
                        float threshold = BIT_THRESHOLD_START;
                        boolean synch = false;
                        boolean input_mode = false;
                        int iterations;
                        int max_different_times = 0;
                        float max_variation_different_times = 0;
                        ArrayList<Float> cpu_sums = new ArrayList<>();
                        int[] word = new int[8];
                        int word_idx = 0;
                        long overhead;

                        @RequiresApi(api = Build.VERSION_CODES.O)
                        @Override
                        protected Void doInBackground(Void... voids) {
                            int last_bit = 0, current_bit = 0;
                            String mode = "Not synchronized";

                            ArrayList<Integer> preamble = new ArrayList<>();
                            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);

                            // synchronize on first max point
//                            ArrayList<Long> log_timestamps = new ArrayList();
                            ArrayList<Float> log_loads = new ArrayList();
                            boolean peak = false;
                            do {
                                float start_workload = Math.max(0f, Utils.readCore(Math.round(READ_CORES)) - 0.01f);
//                                log_timestamps.add(System.currentTimeMillis());
                                log_loads.add(start_workload);
                                int n = log_loads.size();
                                if (n > 1 && log_loads.get(n-2) < 0.5f && log_loads.get(n-1) > 0.95f)
                                    peak = true;
                            } while (!peak);

//                            Log.d("SINK", String.valueOf(log_loads));
                            log_loads.clear();
                            log_loads = null;

                            while (recording[0]) {

                                long p1 = System.currentTimeMillis();

                                if (this.iterations >= 1) {
                                    Log.d("OVERHEAD", "" + overhead);
                                    try {
                                        Thread.sleep((long) WAIT_START-overhead);
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

                                if (sum >= threshold) {
                                    current_bit = 1;
                                } else {
                                    current_bit = 0;
                                }

                                publishProgress("", String.valueOf(threshold), String.valueOf(sum), mode, "");
//                                Log.d("SINK3", "" + (System.currentTimeMillis() - start));

                                if (this.cpu_sums.size() > THRESHOLD_WATCHER) {
                                    this.cpu_sums.remove(0);
                                }

                                // synchronization
                                if (!this.synch) {
                                    if (current_bit == last_bit) {
                                        threshold = this.averageArray(this.cpu_sums);
                                        this.count_different_times = 0;
                                        preamble.clear();
                                    }
                                    if (current_bit != last_bit) {
                                        this.count_different_times++;
                                        preamble.clear();

                                        if (this.count_different_times > DIFFERENT_TIME_THRESHOLD) {
                                            this.synch = true;
                                            mode = "Synchronized";
                                        }
                                    }
                                } else {
                                    if (current_bit == last_bit && current_bit == 0) {
                                        preamble.add(0);
                                        if (preamble.size() >= PREAMBLE_SIZE) {
                                            this.input_mode = true;
                                            mode = "Preamble received";
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
                                        publishProgress(String.valueOf(dec), String.valueOf(threshold), String.valueOf(sum), mode, String.valueOf((System.currentTimeMillis() - p1)));
                                        this.word = new int[8];
                                        this.word_idx = 0;
                                    } else
                                        this.word_idx++;
                                }

                                last_bit = current_bit;

                                if (this.iterations < 1) {
                                    this.overhead = System.currentTimeMillis() - start;
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
                            this.max_different_times = 0;
                            this.max_variation_different_times = 0;
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

                            textView7.setText("Last evaluated threshold: " + values[1]);

                            textView8.setText("Last workload: " + values[2]);

                            if (!values[4].equals("")) {
                                time = values[4];
                                textView10.setText("Time last iteration: " + time);
                            }

                            textView9.setText(values[3]);
                            if (values[3].equals("Not synchronized")) {
                                textView9.setTextColor(Color.RED);
                            } else if (values[3].equals("Synchronized")) {
                                textView9.setTextColor(Color.BLUE);
                            } else if (values[3].equals("Preamble received")) {
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
                    };
                    task.execute();
                } else {
                    button2.setText(getResources().getString(R.string.listen));
                    recording[0] = false;
                }
            }
        });

        return ll;
    }
}
