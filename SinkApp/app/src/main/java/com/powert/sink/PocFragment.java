package com.powert.sink;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
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
    private final float WAIT_START = 496;            // [ms]
    private final float BIT_THRESHOLD_START = 0.10f;
    private final int PREAMBLE_SIZE = 5;
    private final int DIFFERENT_TIME_THRESHOLD = 10;
    private final int THRESHOLD_WATCHER = 10;

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
                    AsyncTask<Void, Float, Void> task = new AsyncTask<Void, Float, Void>() {
//                        int numCores = Utils.getNumCores();
                        float wait_variation = WAIT_START;         // [ms]
                        int count_different_times = 0;
                        float threshold = BIT_THRESHOLD_START;
                        boolean synch = false;
                        boolean input_mode = false;
                        int max_different_times = 0;
                        float max_variation_different_times = 0;
                        ArrayList<Float> cpu_sums = new ArrayList<>();
                        int[] word = new int[8];
                        int word_idx = 0;

                        @RequiresApi(api = Build.VERSION_CODES.O)
                        @Override
                        protected Void doInBackground(Void... voids) {
                            int last_bit = 0, current_bit = 0;
                            float mode = 0f;

                            ArrayList<Integer> preamble = new ArrayList<>();
                            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);
                            while (recording[0]) {
                                long start = System.currentTimeMillis();

                                float sum = Math.max(0f, Utils.readCore(Math.round(WAIT_START)) - 0.01f);
                                Log.d("SINK", "" + (System.currentTimeMillis() - start));
                                this.cpu_sums.add(sum);

                                float workload_variance = this.variance(this.cpu_sums);
                                float workload_average = this.averageArray(this.cpu_sums);
                                Log.d("SINK", "" + (System.currentTimeMillis() - start));

                                if (sum >= threshold) {
                                    current_bit = 1;
                                } else {
                                    current_bit = 0;
                                }

                                publishProgress(null, threshold, WAIT_START, workload_average, workload_variance, sum, mode);
                                Log.d("SINK", "" + (System.currentTimeMillis() - start));

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
                                            mode = 1f;
                                        }
                                    }
                                } else {
                                    if (current_bit == last_bit && current_bit == 0) {
                                        preamble.add(0);
                                        if (preamble.size() >= PREAMBLE_SIZE) {
                                            this.input_mode = true;
                                            mode = 2f;
                                        }
                                    }
                                }
                                Log.d("SINK", "i " + current_bit);
                                Log.d("SINK", "" + (System.currentTimeMillis() - start));

                                // message
                                if (this.input_mode) {
                                    this.word[this.word_idx] = current_bit;
                                    if (this.word_idx % 8 == 7) {
                                        int dec = 0;
                                        for (int i = 0; i < 8; i++)
                                            dec += (this.word[i] == 1) ? Math.pow(2, 8-(i+1)) : 0;
                                        Log.d("SINK", "Message " + Arrays.toString(this.word));
                                        publishProgress((float)dec, threshold, WAIT_START, workload_average, workload_variance, sum, mode);
                                        this.word = new int[8];
                                        this.word_idx = 0;
                                    } else
                                        this.word_idx++;
                                }

                                last_bit = current_bit;
                                Log.d("SINK", "" + (System.currentTimeMillis() - start));
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
                            this.wait_variation = 0;
                            this.threshold = BIT_THRESHOLD_START;
                            this.synch = false;
                            this.input_mode = false;
                            this.cpu_sums.clear();
                            this.count_different_times = 0;
                            this.max_different_times = 0;
                            this.max_variation_different_times = 0;
                            this.word_idx = 0;
                        }

                        @Override
                        protected void onProgressUpdate(Float... values) {
                            super.onProgressUpdate(values);
                            EditText editText3 = ll.findViewById(R.id.editText3);
                            TextView textView7 = ll.findViewById(R.id.textView7);
                            TextView textView8 = ll.findViewById(R.id.textView8);
                            TextView textView9 = ll.findViewById(R.id.textView9);

                            if (values[0] != null) {
                                int value = Math.round(values[0]);
                                editText3.append(String.valueOf(Character.toChars(value)[0]));
                            }

                            textView7.setText("Last evaluated threshold: " + String.valueOf(values[1]));
                            textView8.setText("Last evaluated time iteration: " + String.valueOf(values[2]) + "ms" +
                                    "\nActual workload average: " + values[3] +
                                    "\nActual workload variance: " + values[4] +
                                    "\nLast workload: " + values[5]);

                            if (values[6] == 0f) {
                                textView9.setTextColor(Color.RED);
                                textView9.setText("Not synchronized");
                            } else if (values[6] == 1f) {
                                textView9.setTextColor(Color.BLUE);
                                textView9.setText("Synchronized");
                            } else if (values[6] == 2f) {
                                textView9.setTextColor(Color.GREEN);
                                textView9.setText("Preamble received");
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
