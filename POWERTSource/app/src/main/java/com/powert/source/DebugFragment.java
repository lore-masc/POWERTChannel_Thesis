package com.powert.source;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.powert.source.utility.Wav.WavFileException;
import com.powert.source.utility.Wav.WavTransform;
import com.powert.source.utility.mfcc.MFCC;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Random;

public class DebugFragment extends Fragment {
    private ModuleForwarder moduleForwarder;
    private static final String LOG_TAG = DebugFragment.class.getSimpleName();
    private static final String RECORD_FILE_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/recorded_audio.wav";

    WavTransform wavTransform;

    String className = "";
    private String CLASSES[] = {"unknown", "silence", "yes", "no",
            "up", "down", "left", "right", "on",
            "off", "stop", "go"};

    private long forward_time = 0;

    public DebugFragment () {

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final LinearLayout ll = (LinearLayout) inflater.inflate(R.layout.debug_fragment, container, false);

        Switch switch2 = ll.findViewById(R.id.switch2);
        switch2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean save = ((Switch) ll.findViewById(R.id.switch2)).isChecked();
                TextView textView = ll.findViewById(R.id.textView);

                if (save)
                    textView.setVisibility(View.VISIBLE);
                else
                    textView.setVisibility(View.INVISIBLE);
            }
        });

        Switch switch4 = ll.findViewById(R.id.switch4);
        switch4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean checked = ((Switch) ll.findViewById(R.id.switch4)).isChecked();
                if (checked)
                    ll.findViewById(R.id.spinner).setEnabled(false);
                else
                    ll.findViewById(R.id.spinner).setEnabled(true);
            }
        });

        ImageButton imageButton = ll.findViewById(R.id.record_btn_off);
        imageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ImageButton btn = (ImageButton) v;
                String btn_state = btn.getTag().toString();

                switch(btn_state){
                    case "0":
//                Log.d(LOG_TAG, "Start Recording");
                        wavTransform.startRecording();
                        btn.setTag(1);
                        btn.setBackgroundTintList(ContextCompat.getColorStateList(getActivity().getApplicationContext(), R.color.black));
                        btn.setImageTintList(ContextCompat.getColorStateList(getActivity().getApplicationContext(), R.color.red));
                        break;
                    case "1":
//                Log.d(LOG_TAG, "Stop Recording");
                        wavTransform.stopRecording();
                        btn.setTag(0);
                        btn.setBackgroundTintList(ContextCompat.getColorStateList(getActivity().getApplicationContext(), R.color.gray));
                        btn.setImageTintList(ContextCompat.getColorStateList(getActivity().getApplicationContext(), R.color.white));
                        ll.findViewById(R.id.start_btn).performClick();
                        break;
                }
            }
        });

        Button start_btn = ll.findViewById(R.id.start_btn);
        start_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final ProgressBar progressBar = ll.findViewById(R.id.progressBar);

                @SuppressLint("StaticFieldLeak")
                AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void> () {
                    @Override
                    protected Void doInBackground(Void... voids) {
                        startRecognition();
                        return null;
                    }

                    @Override
                    protected void onPreExecute() {
                        super.onPreExecute();
                        progressBar.setVisibility(View.VISIBLE);
                    }

                    @Override
                    protected void onPostExecute(Void v) {
                        super.onPostExecute(v);
                        boolean save = ((Switch) ll.findViewById(R.id.switch2)).isChecked();

                        EditText editText = ll.findViewById(R.id.editText);
                        editText.append(className + " ");
                        progressBar.setVisibility(View.GONE);

                        TextView textView7 = ll.findViewById(R.id.textView7);
                        textView7.setText("Forward time: " + forward_time + " ms");

                        if (!save)
                            new File(RECORD_FILE_PATH).delete();
                    }
                };
                task.execute();
            }
        });

        try {
            TextView save_message = ll.findViewById(R.id.textView);
            save_message.setText(save_message.getText() + " " + RECORD_FILE_PATH);

            this.wavTransform = new WavTransform(RECORD_FILE_PATH);
        } catch (IOException | WavFileException e) {
            e.printStackTrace();
        }

        try {
            this.moduleForwarder = new ModuleForwarder(getContext());
        } catch (IOException e) {
            e.printStackTrace();
            String message = "Error reading assets during models opening";
            Toast.makeText(getActivity().getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        }


        // Inflate the layout for this fragment
        return ll;
    }

    public void startRecognition() {
//        Log.d(LOG_TAG, "Start recognition");

        Random random = new Random();
        long selected_id;
        ModuleForwarder.VERSION selected_module = null;
        Switch switch3 = getActivity().findViewById(R.id.switch3);
        Switch switch4 = getActivity().findViewById(R.id.switch4);

        try {
            double[] buffer = this.wavTransform.wavToDoubleArray(0,16000);
            //MFCC java library.
            MFCC mfccConvert = new MFCC();
            double[][] melSpectrogram = mfccConvert.melSpectrogram(buffer);
            double[][] powerToDb = mfccConvert.powerToDb(melSpectrogram);

            double[][][] unsqueezePowerToDb = new double[40][1][32];
            for (int i = 0; i < unsqueezePowerToDb.length; i++) {
                System.arraycopy(powerToDb[i], 0, unsqueezePowerToDb[i][0], 0, powerToDb[i].length);
            }

            float[] mfccInput = new float[40*32];
            for (int i = 0 ; i < unsqueezePowerToDb.length; i++)
                for (int j = 0; j < unsqueezePowerToDb[i][0].length; j++)
                    mfccInput[i*unsqueezePowerToDb[i][0].length + j] = (float) unsqueezePowerToDb[i][0][j];

            Log.d(LOG_TAG, "MFCC Input======> " + Arrays.toString(mfccInput));
//            Log.d(LOG_TAG, "MFCC Input======> " + mfccInput.length);

            moduleForwarder.prepare(mfccInput);
            float[] scores = null;

            Spinner spinner = getView().findViewById(R.id.spinner);
            selected_id = spinner.getSelectedItemId();

            int times;
            long timer, start_time, end_time;
            if (switch3.isChecked())
                times = Integer.parseInt(String.valueOf(((EditText) getView().findViewById(R.id.editText2)).getText()));
            else
                times = 1;

            if (switch4.isChecked())
                for (int i = 0; i < times; i++) {
                    timer = Integer.parseInt(String.valueOf(((EditText) getView().findViewById(R.id.editText3)).getText()));
                    selected_module = (i % 2 == 0) ? ModuleForwarder.VERSION.LOW : ModuleForwarder.VERSION.HIGH;
                    while (timer >= 0) {
                        start_time = System.currentTimeMillis();
                        scores = moduleForwarder.forward(selected_module);

                        // Wait given millis between each iteration
                        try {
                            String editText4_txt = ((EditText) getView().findViewById(R.id.editText4)).getText().toString();
                            int delay = Integer.parseInt(editText4_txt);
                            if (delay > 0)
                                Thread.sleep(delay);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
//                        Log.d(LOG_TAG, "Iteration: " + i + " - Residual time: " + timer + " - Version: " + ((i % 2 == 0) ? "Low" : "High"));
                        end_time = System.currentTimeMillis();
                        this.forward_time = (end_time - start_time);
                        timer -= (int) this.forward_time;
                    }
                }
            else
                for (int i = 0; i < times; i++) {
                    long module_idx;
                    if (selected_id == 2)
                        module_idx = random.nextInt(2);
                    else
                        module_idx = selected_id;

                    selected_module = (module_idx == 1) ? ModuleForwarder.VERSION.HIGH : ModuleForwarder.VERSION.LOW;
                    // Log.d(LOG_TAG, "time #" + times + " => " + module_idx);

                    start_time = System.currentTimeMillis();
                    scores = moduleForwarder.forward(selected_module);
                    end_time = System.currentTimeMillis();
                    this.forward_time = end_time - start_time;

                    // Wait given millis between each iteration
                    try {
                        String editText4_txt = ((EditText) getView().findViewById(R.id.editText4)).getText().toString();
                        int delay = Integer.parseInt(editText4_txt);
                        if (delay > 0)
                            Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            Log.d(LOG_TAG, "Output tensor======> " + Arrays.toString(scores));

            float maxScore = -Float.MAX_VALUE;
            int maxScoreIdx = -1;
            for (int i = 0; i < scores.length; i++) {
                if (scores[i] > maxScore) {
                    maxScore = scores[i];
                    maxScoreIdx = i;
                }
            }
            this.className = CLASSES[maxScoreIdx];

//            Log.d(LOG_TAG, className);
        } catch (IOException | WavFileException e) {
            e.printStackTrace();
        }
    }

    public static String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }
}
