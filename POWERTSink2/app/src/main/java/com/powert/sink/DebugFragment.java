package com.powert.sink;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Random;
import java.util.regex.Pattern;

public class DebugFragment extends Fragment {
    private LinearLayout ll;
    private static final String AppDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/sink/";
    private static final String FILE_PATH = AppDir + "data.csv";
    private String overhead;
    private boolean recording;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ll = (LinearLayout) inflater.inflate(R.layout.debug_fragment, container, false);

        this.recording = false;

        // Create app dir
        File directory = new File(AppDir);
        if (!directory.exists())
            directory.mkdir();

        TextView save_message = ll.findViewById(R.id.textView);
        save_message.setText(save_message.getText() + " " + FILE_PATH);

        Button button = ll.findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final Button btn = (Button) view;
                String btn_state = btn.getTag().toString();

                recording = !recording;
                switch(btn_state) {
                    case "0":
                        @SuppressLint("StaticFieldLeak")
                        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void> () {
                            @RequiresApi(api = Build.VERSION_CODES.O)
                            @Override
                            protected Void doInBackground(Void... voids) {
                                record();
                                return null;
                            }

                            @Override
                            protected void onPreExecute() {
                                btn.setText(getResources().getString(R.string.stop));
                                super.onPreExecute();
                            }

                            @Override
                            protected void onPostExecute(Void v) {
                                super.onPostExecute(v);
                            }
                        };
                        task.execute();
                        break;
                    case "1":
                        btn.setText(getResources().getString(R.string.start));
                        break;
                }
                btn.setTag(1 - Integer.parseInt(btn_state));
            }
        });

        return ll;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void record() {
        File file = new File(FILE_PATH);
        if (file.exists())
            file.delete();

        Random random = new Random();
        float[] input = new float[1280];
        for (int i = 0; i < input.length; i++)
            input[i] = random.nextFloat();

        boolean enable_loads = ((Switch) ll.findViewById(R.id.switch2)).isChecked();

        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file, true);
            OutputStreamWriter writer = new OutputStreamWriter(fileOutputStream);
            ModuleForwarder moduleForwarder = new ModuleForwarder(getContext());
            moduleForwarder.prepare(input);

            String header = "timestamp, times";
            if (enable_loads)
                header += ", cpuloads ";
            writer.write(header + "\n");

            while (this.recording) {
                long start = System.currentTimeMillis();
                long times = moduleForwarder.forward(10);
                String row = "";
                row = start + ", " + times;
                if (enable_loads) {
                    EditText editText = ll.findViewById(R.id.editText2);
                    int wait = Integer.parseInt(editText.getText().toString());

                    float cpu_load =  Utils.readCore(wait);
                    row += ", " + cpu_load;
                }
                writer.write(row + "\n");
            }

            writer.close();
            fileOutputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
