package com.powert.sink;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.RequiresApi;

import org.apache.commons.math3.stat.regression.SimpleRegression;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

public class Task extends AsyncTask<Void, Integer, Void> {
    private static long wait;                               // [ms]
    private static final long SINGLE_SAMPLE = 10;
    private static double bit_threshold;
    private static final int PEAK = 3;
    private static final int BYTE = 8;
    private static final int PREAMBLE_SIZE = 5;
    private static int average_size;
    private boolean manchester;
    private ArrayList<Integer> bits;

    @SuppressLint("StaticFieldLeak")
    LinearLayout ll;
    @SuppressLint("StaticFieldLeak")
    Context context;
    boolean recording;
    ModuleForwarder moduleForwarder;

    Task (LinearLayout ll, Context context) throws IOException {
        this.ll = ll;
        this.context = context;
        this.moduleForwarder = new ModuleForwarder(this.context);
        this.manchester = false;

        Random random = new Random();
        float[] input = new float[1280];
        for (int i = 0; i < input.length; i++)
            input[i] = random.nextFloat();
        this.moduleForwarder.prepare(input);

        wait = Long.parseLong(((EditText) ll.findViewById(R.id.editTextNumber)).getText().toString());
        average_size = Math.round(wait / (2*SINGLE_SAMPLE));
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
        this.bits = new ArrayList<>();
        ArrayList<Integer> logBits = new ArrayList<>();
        ArrayList<Integer> samples = new ArrayList<>();
        ArrayList<Long> timestamps = new ArrayList<>();

        // Record activity
        while (this.isRecording()) {
            int sample;
            sample = moduleForwarder.forward(SINGLE_SAMPLE);
            timestamps.add(System.currentTimeMillis());
            samples.add(sample);
        }
//        Log.d("SINK2", "Phase 1.");

        // Post task
        if (this.usingManchesterEncoding()){
            // Min and max
            int MIN = 1; //Math.max(1, Collections.min(samples));
            int MAX = 5; //Collections.max(samples)-1;

            // smooth the curve
            int beforeSmoothing = samples.indexOf(MIN);
            for (int i = beforeSmoothing; i < samples.size(); i++) {
                if (i > beforeSmoothing) {
                    if (timestamps.get(i) - timestamps.get(beforeSmoothing) <= 60) {
                        for (int j = beforeSmoothing; j < i; j++)
                            if (samples.get(i) == MIN)
                                samples.set(j, MIN);
                            else if (samples.get(i) == MAX)
                                samples.set(j, MAX);
                    }
                    beforeSmoothing = i;
                }
            }

            // Compute Moving Average curve
            ArrayList<Float> averages = new ArrayList<>();
            for (int i = average_size; i < samples.size(); i++) {
                ArrayList<Integer> movingAverageWindow = new ArrayList<>();
                float average;

                for (int j = i - average_size; j < i; j++)
                    movingAverageWindow.add(samples.get(j));

                average = arithmeticAverageArray(movingAverageWindow);
                averages.add(average);

                movingAverageWindow.clear();
            }

            // Discretize
            ArrayList<Integer> rbits = new ArrayList<>();
            float bit_threshold1 = 2.9f;
            float bit_threshold2 = 3.1f;
            int last_decision = MAX;
            for (int i = 0; i < averages.size(); i++) {
                if (averages.get(i) >= bit_threshold1 && averages.get(i) <= bit_threshold2) {
                    rbits.add(last_decision);
                } else {
                    if (averages.get(i) > bit_threshold2) {
                        rbits.add(MAX);
                    } else if (averages.get(i) < bit_threshold1) {
                        rbits.add(MIN);
                    }
                    last_decision = rbits.get(rbits.size()-1);
                }
            }

            // Decode
            long lastTimestamp = timestamps.get(average_size);
            for (int i = 1; i < rbits.size(); i++) {
                int idx = average_size + i;
                if (!rbits.get(i).equals(rbits.get(i-1))) {
                    long time = timestamps.get(idx-1) - lastTimestamp;
                    int nSameBits = Math.max(1, Math.round(time / (wait*1.0f)));
                    nSameBits = Math.min(2, nSameBits);
                    for (int j = 0; j < nSameBits; j++)
                        logBits.add((rbits.get(i-1) == MAX) ? 0 : 1);
                    lastTimestamp = timestamps.get(idx);
                }
            }

            // Retrieve single bits
            {
                int i = 0;
                while (i < logBits.size()-1) {
                    if (logBits.get(i) == 0 && logBits.get(i+1) == 1) {
                        bits.add(0);
                        i+=2;
                    } else if (logBits.get(i) == 1 && logBits.get(i+1) == 0) {
                        bits.add(1);
                        i+=2;
                    } else {
                        i++;
                    }
                }
            }

            // Retrieve preamble
            int count = 0;
            boolean found = false;
            for (int i = 0; i < bits.size()-1 && !found; i++) {
                if (bits.get(i) == 0) {
                    count++;
                    if (count == PREAMBLE_SIZE) {
                        bits.subList(0, i).clear();
                        found = true;
                    }
                } else                           count = 0;
            }
            Log.d("SINK2", "Phase 2. Found: " + found);

            // Decode payload
            if (found) {
                int size = bits.size();
                for (int i = 0; i < BYTE - (size%BYTE); i++)     bits.add(0);
                while (bits.size() > 0) {
                    // Retrieve next char
                    int dec = binaryToDec();
                    publishProgress(dec);
//                Log.d("SINK2", "" + dec);
                }
            }

            Log.d("SINK2", "Phase 3.");

        } else {
            // Compute moving average curve
            ArrayList<Float> averages = new ArrayList<>();
            for (int i = average_size; i < samples.size(); i++) {
                ArrayList<Integer> movingAverageWindow = new ArrayList<>();
                float average;

                for (int j = i - average_size; j < i; j++)
                    movingAverageWindow.add(samples.get(j));

                average = arithmeticAverageArray(movingAverageWindow);
                averages.add(average);

                movingAverageWindow.clear();
            }
//        Log.d("SINK2", "Phase 2.");

            // Find first low peak
            int peakIdx = 0;
            boolean peakInterval = false, found = false;
            for (int i = average_size; i < samples.size() && !found; i++) {
                int average_idx = i - average_size;
                if (!peakInterval && averages.get(average_idx) < PEAK) {
                    peakInterval = true;
                } else if (peakInterval) {
                    if (averages.get(average_idx) > averages.get(average_idx - 1)) {
                        peakInterval = false;
                        found = true;
                        peakIdx = i - 1;
                    }
                }
            }
//        Log.d("SINK2", "Phase 3.");

            // Extract useful points
            ArrayList<Integer> points = new ArrayList<>();
            long nextTimestamp = timestamps.get(peakIdx) + wait;
            points.add(peakIdx);
            for (int i = peakIdx; i < timestamps.size(); i++) {
                long instant = timestamps.get(i);
                if (instant > nextTimestamp) {
                    long diff_left = nextTimestamp - timestamps.get(i - 1);
                    long diff_right = timestamps.get(i) - nextTimestamp;
                    int new_point = (diff_left < diff_right) ? i - 1 : i;
                    points.add(new_point);
                    nextTimestamp += wait;
                }
            }
//        Log.d("SINK2", "Phase 4.");

            // Compute threshold
            SimpleRegression simpleRegression = new SimpleRegression();
            for (int i = peakIdx; i < averages.size(); i++)
                simpleRegression.addData(timestamps.get(i), averages.get(i));
//        Log.d("SINK2", "Phase 5.");

            // Collect bits
            boolean synchronization = true, message = false;
            int consecutive_zeros = 0;
            for (int i = 0; i < points.size(); i++) {
                float pointValue = averages.get(points.get(i) - average_size);

                if (synchronization)
                    bit_threshold = simpleRegression.predict(timestamps.get(points.get(i)));

                if (pointValue < bit_threshold) {
                    bits.add(1);
                    if (synchronization) consecutive_zeros = 0;
                } else {
                    bits.add(0);
                    if (synchronization) consecutive_zeros++;
                }

                if (synchronization && consecutive_zeros == PREAMBLE_SIZE) {
                    bits.clear();
                    synchronization = false;
                    message = true;
                    bit_threshold = simpleRegression.predict(timestamps.get(points.get(i - PREAMBLE_SIZE)));
                }
            }
//        Log.d("SINK2", "Phase 6.");

            // Decode payload
            if (message) {
                int size = bits.size();
                for (int i = BYTE; i < size; i += BYTE) {
                    // Retrieve next char
                    int dec = binaryToDec();
                    publishProgress(dec);
//                Log.d("SINK2", "" + dec);
                }
            }
            Log.d("SINK2", "Phase 7.");
        }
//        Log.d("SINK2", "Phase 7.");

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
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);
        EditText editText3 = ll.findViewById(R.id.editText3);

        if (values[0] > 0)
            editText3.append(String.valueOf(Character.toChars(values[0])[0]));
    }

    private boolean usingManchesterEncoding() {
        return this.manchester;
    }

    void useManchesterEncoding (boolean enable) {
        this.manchester = enable;
    }

    private static float weightedAverageArray(ArrayList<Integer> arr) {
        float sum = 0.0f;
        int w = 0;
        for(int i = 0; i < arr.size(); i++) {
            w += (i+1);
            sum += arr.get(i) * (i+1);
        }
        return  sum / w;
    }

    private static float arithmeticAverageArray(ArrayList arr) {
        float sum = 0.0f;

        for(int i = 0; i < arr.size(); i++) {
            if (arr.get(i) instanceof Integer) {
                sum += (Integer) arr.get(i);
            } else if (arr.get(i) instanceof Float) {
                sum += (Float) arr.get(i);
            }
        }
        return  sum / arr.size();
    }

    public float variance(ArrayList<Float> arr) {
        float m = arithmeticAverageArray(arr);
        float sommaScartiQuad = 0;
        for(int i = 0; i < arr.size(); i++)
            sommaScartiQuad += (arr.get(i) -m) * (arr.get(i) -m);
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
