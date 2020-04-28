package com.powert.speechcommandapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.powert.speechcommandapp.utility.Wav.WavFileException;
import com.powert.speechcommandapp.utility.Wav.WavTransform;
import com.powert.speechcommandapp.utility.mfcc.MFCC;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Random;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSION_CODE = 1;

    private static final String LOW_MODEL = "mobile_densenet_22_12.pt";
    private static final String HIGH_MODEL = "mobile_densenet_250_24.pt";
    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private static final String RECORD_FILE_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/recorded_audio.wav";

    WavTransform wavTransform;

    String className = "";
    Module module_low = null;
    Module module_high = null;
    private String CLASSES[] = {"unknown", "silence", "yes", "no",
            "up", "down", "left", "right", "on",
            "off", "stop", "go"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            this.wavTransform = new WavTransform(RECORD_FILE_PATH);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (WavFileException e) {
            e.printStackTrace();
        }

        try {
            module_low = Module.load(assetFilePath(this, LOW_MODEL));
            Log.d(LOG_TAG, "Low model opens with success");
        } catch (IOException e) {
            String message = "Error reading assets during low model opening";
            Log.e(LOG_TAG, message, e);
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            finish();
        }

        try {
            module_high = Module.load(assetFilePath(this, HIGH_MODEL));
            Log.d(LOG_TAG, "High model opens with success");
        } catch (IOException e) {
            String message = "Error reading assets during high model opening";
            Log.e(LOG_TAG, message, e);
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            finish();
        }

        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermission();
        }
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

    public void onSaveCheck(View view) {
        boolean save = ((Switch) findViewById(R.id.switch2)).isChecked();
        TextView textView = findViewById(R.id.textView);

        if (save)
            textView.setVisibility(View.VISIBLE);
        else
            textView.setVisibility(View.INVISIBLE);
    }

    public void onRecordNew(View view) {
        ImageButton btn = (ImageButton) view;
        String btn_state = btn.getTag().toString();

        switch(btn_state){
            case "0":
                Log.d(LOG_TAG, "Start Recording");
                this.wavTransform.startRecording();
                btn.setTag(1);
                btn.setBackgroundTintList(ContextCompat.getColorStateList(getApplicationContext(), R.color.black));
                btn.setImageTintList(ContextCompat.getColorStateList(getApplicationContext(), R.color.red));
                break;
            case "1":
                Log.d(LOG_TAG, "Stop Recording");
                this.wavTransform.stopRecording();
                btn.setTag(0);
                btn.setBackgroundTintList(ContextCompat.getColorStateList(getApplicationContext(), R.color.gray));
                btn.setImageTintList(ContextCompat.getColorStateList(getApplicationContext(), R.color.white));
                findViewById(R.id.start_btn).performClick();
                break;
        }
    }

    @SuppressLint("StaticFieldLeak")
    public void onStartRecognition(View view) {
        boolean save = ((Switch) findViewById(R.id.switch2)).isChecked();
        final ProgressBar progressBar = findViewById(R.id.progressBar);

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
                EditText editText = findViewById(R.id.editText);
                editText.append(className + " ");
                progressBar.setVisibility(View.GONE);
            }
        };
        task.execute();

        if (!save)
            new File(RECORD_FILE_PATH).delete();
    }

    public void startRecognition() {
        Log.d(LOG_TAG, "Start recognition");

        Random random = new Random();
        long selected_id;
        Module selected_module;
        Switch switch3 = findViewById(R.id.switch3);

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
            Log.d(LOG_TAG, "MFCC Input======> " + mfccInput.length);
            long[] shape = {1, 1, 40, 32};
            Tensor inputTensor = Tensor.fromBlob(mfccInput, shape);
            Tensor outputTensor = null;

            Spinner spinner = findViewById(R.id.spinner);
            selected_id = spinner.getSelectedItemId();

            int times;
            if (switch3.isChecked())
                times = Integer.parseInt(String.valueOf(((EditText) findViewById(R.id.editText2)).getText()));
            else
                times = 1;

            for (int i = 0; i < times; i++) {
                long module_idx;
                if (selected_id == 2)
                    module_idx = random.nextInt(2);
                else
                    module_idx = selected_id;

                selected_module = (module_idx == 1) ? this.module_high : this.module_low;
                Log.d(LOG_TAG, "time #" + times + " => " + module_idx);
                outputTensor = selected_module.forward(IValue.from(inputTensor)).toTensor();
            }

            float[] scores = outputTensor.getDataAsFloatArray();

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

            Log.d(LOG_TAG, className);
        } catch (IOException | WavFileException e) {
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void requestPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.RECORD_AUDIO)) {
            Toast.makeText(this, "Requires RECORD_AUDIO permission", Toast.LENGTH_SHORT).show();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                    },
                    REQUEST_PERMISSION_CODE);
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
