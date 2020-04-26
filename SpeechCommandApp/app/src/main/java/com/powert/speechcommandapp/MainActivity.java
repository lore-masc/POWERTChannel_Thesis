package com.powert.speechcommandapp;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Environment;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import java.util.ArrayList;
import java.util.Arrays;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSION_CODE = 1;

    private static final String MODEL_FILENAME = "mobile_densenet_22_12.pt";
    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private static final String RECORD_FILE_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/recorded_audio.wav";

    WavTransform wavTransform;

    Module module = null;
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
            module = Module.load(assetFilePath(this, MODEL_FILENAME));
            Log.d(LOG_TAG, "success open model");
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error reading assets", e);
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

    public void onRecordNew(View view) {
        TextView btn = (TextView) view;
        String   btn_text = btn.getText().toString();

        switch(btn_text){
            case "Record new":
                Log.d(LOG_TAG, "Start Recording");
                this.wavTransform.startRecording();
                btn_text = "Stop";
                btn.setText(btn_text);
                break;
            case "Stop":
                Log.d(LOG_TAG, "Stop Recording");
                this.wavTransform.stopRecording();
                btn_text = "Record new";
                btn.setText(btn_text);
                break;
        }
    }

    public void onStartRecognition(View view) throws IOException {
        startRecognition();
    }

    public void startRecognition() throws IOException {
        Log.d(LOG_TAG, "Start recognition");

        String className = "";

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
            Tensor outputTensor = module.forward(IValue.from(inputTensor)).toTensor();
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
            className = CLASSES[maxScoreIdx];
        } catch (WavFileException e) {
            e.printStackTrace();
        }

        Log.d(LOG_TAG, className);
        EditText editText = findViewById(R.id.editText);
        editText.append(className + " ");
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
