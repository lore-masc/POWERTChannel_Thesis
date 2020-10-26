package com.powert.sink;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

public class Task extends AsyncTask<Void, String, Void> {
    private static long wait;                               // [ms]
    private static final long READ_CORES = 20;              // [ms]
    private static float bit_threshold;
    private static final float PEAK = 0.8f;
    private static final int BYTE = 8;
    private static final int PREAMBLE_SIZE = 5;
    private static final int MAN_AVERAGE_WIN = 3;
    private int sessions;
    private static float overhead_workload;
    private static int average_size;
    private boolean manchester;
    private ArrayList<Long> logTimestamps;
    private ArrayList<Integer> logBits;
    private ArrayList<Float> logSamples;
    private ArrayList<Float> logAverages;
    private ArrayList<Integer> bits;
    private ArrayList<Integer> majority;

    @SuppressLint("StaticFieldLeak")
    LinearLayout ll;
    @SuppressLint("StaticFieldLeak")
    Context context;
    boolean recording;
    boolean cls;


    Task (LinearLayout ll, Context context, int sessions) {
        this.ll = ll;
        this.context = context;
        this.manchester = false;
        this.cls = false;
        this.sessions = sessions;
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
        ArrayList<Float> averages = new ArrayList<>();

        this.logTimestamps = new ArrayList<>();
        this.logSamples = new ArrayList<>();
        this.logBits = new ArrayList<>();
        this.logAverages = new ArrayList<>();
        this.majority = new ArrayList<>();

        if (this.usingManchesterEncoding()) {
            long last_measure = 0;
            int count = 0;
//            float sum = 0;
//            boolean was_positive = true;

            while (this.isRecording()) {
                Log.d("SINK", "" + bits);
                float sampled_workload;

                // waiting first peak
                do {
                    sampled_workload = Math.max(0, Utils.readCore(READ_CORES));
                    if (start && sampled_workload - overhead_workload >= PEAK) {
//                    Log.d("SINK", "MEASURED: " + sampled_workload);
                        last_measure = System.currentTimeMillis();
                        start = false;
                    }
                } while (start && this.isRecording());

                samples.add(sampled_workload);

                if (samples.size() > MAN_AVERAGE_WIN) {
                    samples.remove(0);
                    float actual_av = weightedAverageArray(samples);
                    float actual_diff = 0;
                    averages.add(actual_av);

                    if (averages.size() > 2) {
                        averages.remove(0);
                        actual_diff = averages.get(1) - averages.get(0);

                        long now = System.currentTimeMillis();

                        this.logAverages.add(actual_av);
                        this.logTimestamps.add(now);
                        this.logSamples.add(sampled_workload);

                        float percent_time = (now - last_measure) / (2.0f * wait);

//                        if ((was_positive && actual_diff < 0) || (!was_positive && actual_diff >= 0))    sum = 0;
//                        was_positive = actual_diff >= 0;
//                        sum += actual_diff;

                        if (percent_time >= 0.84f) {
    //                        Log.d("SINK", "Time: " + (now - last_measure) / (2.0*wait) + " - Diff: " + actual_diff + " - " +  Math.abs(actual_diff) + " >= " + bit_threshold + "? " + (Math.abs(actual_diff) >= bit_threshold) + " *");
                            if (Math.abs(actual_diff) >= bit_threshold)
                                Log.d("SINK", "Time: " + (now - last_measure) / (2.0 * wait) + " - Diff: " + actual_diff + " **");
                            else
                                Log.d("SINK", "Time: " + (now - last_measure) / (2.0 * wait) + " - Diff: " + actual_diff);

                            if (actual_diff >= bit_threshold) {
                                if (this.majority.size() < this.sessions)  this.majority.add(0);
                                this.logBits.add(0);
                                last_measure = now;
                            } else if (actual_diff <= -bit_threshold) {
                                if (this.majority.size() < this.sessions)  this.majority.add(1);
                                this.logBits.add(1);
                                last_measure = now;
                            } else {
                                this.logBits.add(-1);
                            }

                            if (this.majority.size() == this.sessions) {
                                int elected = vote();
                                bits.add(elected);
                                Log.d("SINK", "MAJORITY VOTE ON: " + this.majority);
                                this.majority.clear();
                                if (synchronization && elected == 0) {
                                    count++;
                                    if (count >= PREAMBLE_SIZE) {
                                        bits.clear();
                                        mode = this.context.getResources().getString(R.string.mode2);
                                        synchronization = false;
                                    }
                                } else if (synchronization && elected == 1) {
                                    count = 0;
                                }
                            }

                        } else {
                            this.logBits.add(-1);
                            Log.d("SINK", "Time: " + (now - last_measure) / (2.0 * wait) + " - Diff: " + actual_diff);
                        }
                    } else {
                        this.logBits.add(-1);
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
            if (!synchronization && bits.size() > 0) {
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
                    sampled_workload = Math.max(0, Utils.readCore(READ_CORES));
                    if (start && sampled_workload - overhead_workload >= PEAK) {
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
                    averages.add(actual_workload);
                    this.logSamples.add(sampled_workload);
                    this.logTimestamps.add(System.currentTimeMillis());
                    this.logAverages.add(actual_workload);

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
                        this.logBits.add(0);
                        zero_bit = false;
                    } else if (actual_workload < bit_threshold && !zero_bit) {
                        consecutive_bits = Math.max(1, Math.round((last_measure - start_range) / (wait * 1.0f)));
//                    Log.d("SINK", "Entrato nello 0 dopo " + (last_measure - start_range) + " ms = " + Math.round((last_measure - start_range) / (wait * 1.0f)));
                        start_range = System.currentTimeMillis();
                        for (int i = 0; i < consecutive_bits; i++) bits.add(1);
                        this.logBits.add(1);
                        zero_bit = true;
                    } else {
                        this.logBits.add(-1);
                    }

                    // get message
                    if (!synchronization && bits.size() >= BYTE) {
                        int dec = binaryToDec();
                        publishProgress(mode, String.valueOf(actual_workload), String.valueOf(dec), "");
                    } else if (!synchronization && Math.round((last_measure - start_range) / (wait * 1.0f)) > BYTE + (BYTE - bits.size())) {
                        // corner case of remaining zero-bits
                        for (int i = 0; i < BYTE - bits.size(); i++) {
                            bits.add(0);
                        }
                    }
                } else {
                    this.logBits.add(-1);
                    this.logAverages.add(0f);
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
        File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/Sink1.csv");
        if (file.exists())
            file.delete();
        FileOutputStream fileOutputStream;
        try {
            fileOutputStream = new FileOutputStream(file, true);
            OutputStreamWriter writer = new OutputStreamWriter(fileOutputStream);
            String header = "Timestamps, samplings, average, bits\n";
            writer.write(header);

            for (int i = 0; i < this.logTimestamps.size(); i++) {
                writer.write(this.logTimestamps.get(i) + ", " +  this.logSamples.get(i) + ", " + this.logAverages.get(i) + ", " + this.logBits.get(i));
                writer.write("\n");
            }
            writer.close();
            fileOutputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

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

        if (values.length >= 5 && !values[4].equals(""))
            textView13.setText("Last evaluated threshold: " + values[4]);

        if (!values[2].equals("")) {
            if (values[2].equals("-1")) {
                this.cls = true;
            } else {
                if (this.cls) {
                    editText3.setText("");
                    this.cls = false;
                }
                editText3.append(String.valueOf(Character.toChars(Integer.parseInt(values[2]))[0]));
            }
        }

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
        this.bit_threshold = (this.usingManchesterEncoding()) ? 0.18f : 0.46f;
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

    private int binaryToDec() {
        int dec = 0;
        int size = bits.size();

        for (int i = 0; i < Math.min(BYTE, size); i++) {
            dec += (bits.get(i) == 1) ? Math.pow(2, BYTE - (i + 1)) : 0;
        }
        Log.d("SINK", "" + bits);
        Log.d("Sink", "" + String.valueOf(Character.toChars(dec)));

        for (int i = 0; i < Math.min(BYTE, size); i++)
            bits.remove(0);
        return dec;
    }

    private int vote() {
        int zeros = 0, ones = 0;
        int election;
        for ( int bit : this.majority )
                if (bit == 1) ones++;
                else zeros++;

        if (ones > zeros)
            election = 1;
        else
            election = 0;
        return election;
    }

}
