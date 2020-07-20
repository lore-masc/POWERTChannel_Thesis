package com.powert.source;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.IOException;
import java.util.Random;

public class PocFragment extends Fragment {
    LinearLayout ll;

    public PocFragment () {

    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.ll = (LinearLayout) inflater.inflate(R.layout.poc_fragment, container, false);

        try {
            final PowertChannelManager powertChannelManager = new PowertChannelManager(ll, getContext());
            Button button = ll.findViewById(R.id.button);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final EditText editText5 = ll.findViewById(R.id.editText5);
                    final Button button = ll.findViewById(R.id.button);
                    final Button button2 = ll.findViewById(R.id.button2);
                    final Editable editText5_editable = editText5.getText();
                    final Switch switch1 = ll.findViewById(R.id.switch1);

                    @SuppressLint("StaticFieldLeak")
                    AsyncTask<Void, Integer, Void> task = new AsyncTask<Void, Integer, Void>() {
                        @Override
                        protected void onPreExecute() {
                            super.onPreExecute();
                            editText5.setEnabled(false);
                            button.setEnabled(false);
                            button2.setEnabled(false);
                            powertChannelManager.useManchesterEncoding(switch1.isChecked());
                        }

                        @Override
                        protected Void doInBackground(Void... voids) {
                            String message = editText5_editable.toString();
                            powertChannelManager.sendPackage(message);
                            return null;
                        }

                        @Override
                        protected void onPostExecute(Void v) {
                            super.onPostExecute(v);
                            Toast.makeText(getActivity().getApplicationContext(), "Message sended", Toast.LENGTH_SHORT).show();
                            editText5.setEnabled(true);
                            button.setEnabled(true);
                            button2.setEnabled(true);
                        }
                    };
                    task.execute();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            String message = "Error reading assets during models opening";
            Toast.makeText(getActivity().getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        }

        Button button2 = ll.findViewById(R.id.button2);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText editText5 = ll.findViewById(R.id.editText5);
                Random random = new Random();
                for(int i = 0; i < 64; i++)
                    editText5.append(Integer.toString(random.nextInt(2)));
            }
        });

        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                final Switch switch1 = ll.findViewById(R.id.switch1);
                updateRequiredTime(switch1.isChecked());
            }
        };

        final EditText editText5 = ll.findViewById(R.id.editText5);
        editText5.addTextChangedListener(textWatcher);

        final EditText editTextNumber = ll.findViewById(R.id.editTextNumber);
        editTextNumber.addTextChangedListener(textWatcher);

        final Switch switch1 = ll.findViewById(R.id.switch1);
        switch1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateRequiredTime(isChecked);
            }
        });

        return ll;
    }

    void updateRequiredTime(boolean isChecked) {
        EditText editText5 = ll.findViewById(R.id.editText5);
        TextView textView8 = ll.findViewById(R.id.textView8);
        String time = ((EditText) ll.findViewById(R.id.editTextNumber)).getText().toString();

        if (!time.equals(""))
            PowertChannelManager.TIME = Long.parseLong(time);

        int bits = PowertChannelManager.getBitArray(editText5.getText().toString()).length;

        long time_required =
                PowertChannelManager.LONG_STREAM_SIZE*PowertChannelManager.TIME
                        + PowertChannelManager.PREAMBLE_SIZE*PowertChannelManager.TIME
                        + bits*PowertChannelManager.TIME;

        if (isChecked)    time_required *= 2;

        textView8.setText("Estimated time: " + time_required + " ms");
    }
}
