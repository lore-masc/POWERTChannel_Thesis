package com.powert.sink;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;

public class Task extends AsyncTask<Void, String, Void> {
    private static long wait;                               // [ms]
    private static final long READ_CORES = 20;              // [ms]
    private static float bit_threshold;
    private static final float PEAK = 0.8f;
    private static final int BYTE = 8;
    private static final int PREAMBLE_SIZE = 5;
    private static float overhead_workload;
    private static int average_size;
    private boolean manchester;
    private ArrayList<Integer> bits;

    @SuppressLint("StaticFieldLeak")
    LinearLayout ll;
    @SuppressLint("StaticFieldLeak")
    Context context;
    boolean recording;


    Task (LinearLayout ll, Context context) {
        this.ll = ll;
        this.context = context;
        this.manchester = false;
        wait = Long.parseLong(((EditText) ll.findViewById(R.id.editTextNumber)).getText().toString());
        average_size = Math.round(wait / READ_CORES);

        // Listen actual rumor
        ArrayList<Float> rumor_workload = new ArrayList<>();
        for (int i = 0; i < 20; i++)
            rumor_workload.add(Utils.readCore(READ_CORES));
        overhead_workload = weightedAverageArray(rumor_workload);
        publishProgress("", "", "", String.valueOf(overhead_workload));
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
        String mode = this.context.getResources().getString(R.string.mode1);
        boolean start = true;
        boolean zero_bit = true;
        boolean synchronization = true;
        long start_range = System.currentTimeMillis();
        long consecutive_bits;
        this.bits = new ArrayList<>();
        ArrayList<Float> samples = new ArrayList<>();

        if (this.usingManchesterEncoding()) {
            ArrayList<Long> logTimestamps = new ArrayList<>();
            ArrayList<Float> logSamples = new ArrayList<>();
            ArrayList<Integer> logBits = new ArrayList<>();
            long last_measure = 0;
            int count = 0;

            while (this.isRecording()) {
                Log.d("SINK", "" + bits);
                float sampled_workload;

                // waiting first peak
                do {
                    sampled_workload = Math.max(0, Utils.readCore(READ_CORES) - overhead_workload);
                    if (start && sampled_workload >= PEAK) {
//                    Log.d("SINK", "MEASURED: " + sampled_workload);
                        start = false;
                    }
                } while (start && this.isRecording());

                samples.add(sampled_workload);

                if (samples.size() > 2) {
                    samples.remove(0);
//                    float actual_av = arithmeticAverageArray(samples);
                    float actual_diff = samples.get(samples.size()-1) - samples.get(0);

                    long now = System.currentTimeMillis();

                    logTimestamps.add(now);
                    logSamples.add(sampled_workload);

                    float percent_time = (now - last_measure) / (2.0f*wait);
                    if (percent_time >= 0.85f) {
//                        Log.d("SINK", "Time: " + (now - last_measure) / (2.0*wait) + " - Diff: " + actual_diff + " - " +  Math.abs(actual_diff) + " >= " + bit_threshold + "? " + (Math.abs(actual_diff) >= bit_threshold) + " *");
                        if (Math.abs(actual_diff) >= bit_threshold)
                            Log.d("SINK", "Time: " + (now - last_measure) / (2.0*wait) + " - Diff: " + actual_diff + " **");

                        if (now > 1.3f && logSamples.size() > 3) {
                            actual_diff = logSamples.get(logSamples.size()-2) - logSamples.get(logSamples.size()-3);
                        }

                        if (actual_diff >= bit_threshold) {
                            bits.add(0);
                            logBits.add(0);
                            last_measure = now;
                            if (synchronization) {
                                count++;
                                if (count >= PREAMBLE_SIZE) {
                                    bits.clear();
                                    mode = this.context.getResources().getString(R.string.mode2);
                                    synchronization = false;
                                }
                            }
                        } else if (actual_diff <= -bit_threshold) {
                            bits.add(1);
                            logBits.add(1);
                            last_measure = now;
                            if (synchronization) {
                                count = 0;
                            }
                        } else {
                            logBits.add(-1);
                        }
                    } else {
                        logBits.add(-1);
                        Log.d("SINK", "Time: " + (now - last_measure) / (2.0 * wait) + " - Diff: " + actual_diff);
                    }

                    // get message
                    if (!synchronization && bits.size() >= BYTE) {
                        int dec = binaryToDec();
                        publishProgress(mode, String.valueOf(actual_diff), String.valueOf(dec), "");
                    } else {
                        publishProgress(mode, String.valueOf(actual_diff), "", "");
                    }
                }

            }

            // Return last bits
            if (bits.size() > 0) {
                int dec;
                for (int i = 0; i < BYTE - bits.size(); i++) {
                    bits.add(0);
                }
                dec = binaryToDec();
                publishProgress(mode, "", String.valueOf(dec), "");
            }
            Log.d("Sink", "Finish");
        } else {
            while (this.isRecording()) {
                Log.d("SINK", "" + bits);
                float sampled_workload;

                // waiting first peak
                do {
                    sampled_workload = Math.max(0, Utils.readCore(READ_CORES) - overhead_workload);
                    if (start && sampled_workload >= PEAK) {
//                    Log.d("SINK", "MEASURED: " + sampled_workload);
                        start = false;
                        start_range = System.currentTimeMillis();
                    }
                } while (start && this.isRecording());
                samples.add(sampled_workload);

                long last_measure = System.currentTimeMillis();

                if (samples.size() > average_size) {
                    samples.remove(0);
                    float actual_workload = weightedAverageArray(samples);

                    publishProgress(mode, String.valueOf(actual_workload), "", "");

                    // synchronization
                    if (actual_workload >= bit_threshold && zero_bit) {
                        consecutive_bits = Math.max(1, Math.round((last_measure - start_range) / (wait * 1.0f)));
//                    Log.d("SINK", "Entrato nel 1 dopo " + (last_measure - start_range) + " ms = " + Math.round((last_measure - start_range) / (wait * 1.0f)));
                        start_range = System.currentTimeMillis();
                        if (consecutive_bits >= PREAMBLE_SIZE && synchronization) {
                            bits.clear();
                            mode = this.context.getResources().getString(R.string.mode2);
                            synchronization = false;
                            consecutive_bits -= PREAMBLE_SIZE;
                            publishProgress(mode, String.valueOf(actual_workload), "", "");
                        }
                        for (int i = 0; i < consecutive_bits; i++) bits.add(0);
                        zero_bit = false;
                    } else if (actual_workload < bit_threshold && !zero_bit) {
                        consecutive_bits = Math.max(1, Math.round((last_measure - start_range) / (wait * 1.0f)));
//                    Log.d("SINK", "Entrato nello 0 dopo " + (last_measure - start_range) + " ms = " + Math.round((last_measure - start_range) / (wait * 1.0f)));
                        start_range = System.currentTimeMillis();
                        for (int i = 0; i < consecutive_bits; i++) bits.add(1);
                        zero_bit = true;
                    }

                    // get message
                    if (!synchronization && bits.size() >= BYTE) {
                        // not working
                        int dec = binaryToDec();
                        publishProgress(mode, String.valueOf(actual_workload), String.valueOf(dec), "");
                    } else if (!synchronization && Math.round((last_measure - start_range) / (wait * 1.0f)) > BYTE + (BYTE - bits.size())) {
                        // corner case of remaining zero-bits
                        for (int i = 0; i < BYTE - bits.size(); i++) bits.add(0);
                    }
                }

            }
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
    }

    @Override
    protected void onProgressUpdate(String... values) {
        super.onProgressUpdate(values);
        EditText editText3 = ll.findViewById(R.id.editText3);
        TextView textView8 = ll.findViewById(R.id.textView8);
        TextView textView9 = ll.findViewById(R.id.textView9);
        TextView textView10 = ll.findViewById(R.id.textView10);
        TextView textView13 = ll.findViewById(R.id.textView13);

        if (!values[3].equals(""))
            textView10.setText("Overhead workload: " + values[3]);

        if (values.length == 5)
            textView13.setText("Last evaluated threshold: " + values[4]);

        if (!values[2].equals(""))
            editText3.append(String.valueOf(Character.toChars(Integer.parseInt(values[2]))[0]));

        textView8.setText("Last workload: " + values[1]);

        textView9.setText(values[0]);
        if (values[0].equals(this.context.getResources().getString(R.string.mode1))) {
            textView9.setTextColor(Color.BLUE);
        } else if (values[0].equals(this.context.getResources().getString(R.string.mode2))) {
            textView9.setTextColor(Color.GREEN);
        }
    }

    private boolean usingManchesterEncoding() {
        return this.manchester;
    }

    void useManchesterEncoding (boolean enable) {
        this.manchester = enable;
        this.bit_threshold = (this.usingManchesterEncoding()) ? 0.3f : 0.46f;
    }

    private static float weightedAverageArray(ArrayList<Float> arr) {
        float sum = 0.0f;
        int w = 0;
        for(int i = 0; i < arr.size(); i++) {
            w += (i+1);
            sum += arr.get(i) * (i+1);
        }
        return  sum / w;
    }

    private static float arithmeticAverageArray(ArrayList<Float> arr) {
        float sum = 0.0f;
        for(int i = 0; i < arr.size(); i++) {
            sum += arr.get(i);
        }
        return  sum / arr.size();
    }

    public static float stdDev(ArrayList<Float> arr) {
        float m = arithmeticAverageArray(arr);
        float sommaScartiQuad = 0;
        for(int i = 0; i < arr.size(); i++)
            sommaScartiQuad += (arr.get(i) -m);
        return sommaScartiQuad/arr.size();
    }

    private int binaryToDec() {
        int dec = 0;
        int size = bits.size();
        for (int i = 0; i < Math.min(BYTE, size); i++)
            dec += (bits.get(i) == 1) ? Math.pow(2, BYTE - (i + 1)) : 0;

        Log.d("SINK", "" + bits);
        Log.d("Sink", "" + String.valueOf(Character.toChars(dec)));

        for (int i = 0; i < Math.min(BYTE, size); i++)
            bits.remove(0);
        return dec;
    }

}
