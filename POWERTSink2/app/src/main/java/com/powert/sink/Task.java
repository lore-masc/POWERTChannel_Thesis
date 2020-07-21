package com.powert.sink;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

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
        ArrayList<Integer> bits = new ArrayList<>();
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
            // Compute Movement Average curve
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
            Log.d("SINK2", "Phase 2.");

            // Compute local variances
            // variances start after 2*average_size items
            ArrayList<Float> variances = new ArrayList<>();
            for (int i = average_size; i < averages.size(); i++) {
                ArrayList<Float> varianceWindow = new ArrayList<>();
                float variance;

                for (int j = i - average_size; j < i; j++)
                    varianceWindow.add(averages.get(j));
                variance = variance(varianceWindow);
                variances.add(variance);

                varianceWindow.clear();
            }
            Log.d("SINK2", "Phase 3.");

            // Extract useful points
            ArrayList<Integer> points = new ArrayList<>();
            long nextTimestamp = 0;
            for (int i = 0; i < variances.size(); i++) {
                int idx = i + 2*average_size;
                long instant = timestamps.get(idx);
                if (variances.get(i) > 0.2 && instant > nextTimestamp) {
                    long diff_left = nextTimestamp - timestamps.get(idx - 1);
                    long diff_right = instant - nextTimestamp;
                    int new_point = (diff_left < diff_right) ? idx - 1 : idx;
                    points.add(new_point);
                    nextTimestamp = timestamps.get(new_point) + 2*wait;
                }
            }
            Log.d("SINK2", "Phase 4.");

            // Collect bits
            for (int i = 0; i < points.size(); i++) {
                int idx = points.get(i);
                float previousSample = averages.get(idx-1);
                float sample = averages.get(idx);
                float subsequentSample = averages.get(idx+1);

                // Up edge
                if (previousSample <= sample && sample <= subsequentSample) {
                    bits.add(0);
                }
                // Down edge
                if (previousSample >= sample && sample >= subsequentSample) {
                    bits.add(1);
                }
            }
            Log.d("SINK2", "Phase 5.");

            // Find preamble
            int zeros = 0;
            boolean synch = false;
            for (int i = 0; i < bits.size() && !synch; i++) {
                if (bits.get(i) == 0) zeros++;
                else                  zeros = 0;

                if (zeros == PREAMBLE_SIZE) {
                    bits.subList(0, i).clear();
                    synch = true;
                }
            }
            Log.d("SINK2", "Phase 6.");

            // Decode payload
            int size;

            // Complete last byte
            do {
                bits.add(0);
                size = bits.size();
            } while (size % BYTE != 0);

            for (int i = BYTE; i < size; i += BYTE) {
                // Retrieve next char
                int dec = 0;
                for (int b = 0; b < BYTE; b++)
                    dec += (bits.get(b) == 1) ? Math.pow(2, BYTE - (b + 1)) : 0;

                // Remove used bits
                bits.subList(0, BYTE).clear();
                publishProgress(dec);
//                Log.d("SINK2", "" + dec);
            }
            Log.d("SINK2", "Phase 7.");

        } else {
            // Compute Movement Average curve
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

                if (synchronization && consecutive_zeros == PREAMBLE_SIZE) {
                    bits.clear();
                    synchronization = false;
                    message = true;
                    bit_threshold = simpleRegression.predict(timestamps.get(points.get(i - PREAMBLE_SIZE)));
                }

                if (pointValue < bit_threshold) {
                    bits.add(1);
                    if (synchronization) consecutive_zeros = 0;
                } else {
                    bits.add(0);
                    if (synchronization) consecutive_zeros++;
                }
            }
//        Log.d("SINK2", "Phase 6.");

            // Decode payload
            if (message) {
                int size = bits.size();
                for (int i = BYTE; i < size; i += BYTE) {
                    // Retrieve next char
                    int dec = 0;
                    for (int b = 0; b < BYTE; b++)
                        dec += (bits.get(b) == 1) ? Math.pow(2, BYTE - (b + 1)) : 0;

                    // Remove used bits
                    bits.subList(0, BYTE).clear();
                    publishProgress(dec);
//                Log.d("SINK2", "" + dec);
                }
            }
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
}
