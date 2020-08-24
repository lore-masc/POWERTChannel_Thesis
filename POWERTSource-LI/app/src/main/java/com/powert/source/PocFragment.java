package com.powert.source;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import java.io.IOException;
import java.util.Random;

public class PocFragment extends Fragment {
    LinearLayout ll;
    enum ENCODE_TYPE {CHARACTER, BIT};

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
                    final Spinner spinner2 = ll.findViewById(R.id.spinner2);

                    @SuppressLint("StaticFieldLeak")
                    AsyncTask<Void, Integer, Void> task = new AsyncTask<Void, Integer, Void>() {
                        @Override
                        protected void onPreExecute() {
                            super.onPreExecute();
                            editText5.setEnabled(false);
                            button.setEnabled(false);
                            button2.setEnabled(false);
                            powertChannelManager.useManchesterEncoding(false);
                        }

                        @SuppressLint("WrongThread")
                        @Override
                        protected Void doInBackground(Void... voids) {
                            String message = editText5_editable.toString();
                            ENCODE_TYPE encode_type;
                            switch (spinner2.getSelectedItemPosition()) {
                                case 1:
                                    encode_type = ENCODE_TYPE.BIT;
                                    break;
                                default:
                                    encode_type = ENCODE_TYPE.CHARACTER;
                            }

                            powertChannelManager.sendPackage(message, encode_type);
                            return null;
                        }

                        @Override
                        protected void onPostExecute(Void v) {
                            super.onPostExecute(v);
                            Toast.makeText(getActivity().getApplicationContext(), "Standard enc. terminated", Toast.LENGTH_SHORT).show();
                            editText5.setEnabled(true);
                            button.setEnabled(true);
                            button2.setEnabled(true);
                        }
                    };
                    task.execute();
                }
            });

            Button button3 = ll.findViewById(R.id.button3);
            button3.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final EditText editText5 = ll.findViewById(R.id.editText5);
                    final Button button3 = ll.findViewById(R.id.button3);
                    final Button button2 = ll.findViewById(R.id.button2);
                    final Editable editText5_editable = editText5.getText();
                    final Spinner spinner2 = ll.findViewById(R.id.spinner2);

                    @SuppressLint("StaticFieldLeak")
                    AsyncTask<Void, Integer, Void> task = new AsyncTask<Void, Integer, Void>() {
                        @Override
                        protected void onPreExecute() {
                            super.onPreExecute();
                            editText5.setEnabled(false);
                            button3.setEnabled(false);
                            button2.setEnabled(false);
                            powertChannelManager.useManchesterEncoding(true);
                        }

                        @SuppressLint("WrongThread")
                        @Override
                        protected Void doInBackground(Void... voids) {
                            String message = editText5_editable.toString();
                            ENCODE_TYPE encode_type;
                            switch (spinner2.getSelectedItemPosition()) {
                                case 1:
                                    encode_type = ENCODE_TYPE.BIT;
                                    break;
                                default:
                                    encode_type = ENCODE_TYPE.CHARACTER;
                            }

                            powertChannelManager.sendPackage(message, encode_type);
                            return null;
                        }

                        @Override
                        protected void onPostExecute(Void v) {
                            super.onPostExecute(v);
                            Toast.makeText(getActivity().getApplicationContext(), "Manchester enc. terminated", Toast.LENGTH_SHORT).show();
                            editText5.setEnabled(true);
                            button3.setEnabled(true);
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
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void onClick(View v) {
                final Spinner spinner2 = ll.findViewById(R.id.spinner2);
                EditText editText2 = ll.findViewById(R.id.editTextNumber2);
                EditText editText5 = ll.findViewById(R.id.editText5);
                Random random = new Random();
                editText5.setText("");

                switch (spinner2.getSelectedItemPosition()) {
                    case 1:
                        for(int i = 0; i < Integer.parseInt(editText2.getText().toString()); i++)
                            editText5.append(Integer.toString(random.nextInt(2)));
                        break;
                    default:
                        StringBuilder randomString = new StringBuilder();
                        for (int i = 0; i < Integer.parseInt(editText2.getText().toString()); i++) {
                            int j = 'a' + random.nextInt('z'-'a');
                            char c = (char) j;
                            randomString.append(c);
                        }
                        randomString.toString();
                        editText5.append(randomString);
                }
            }
        });

        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                final Spinner spinner2 = ll.findViewById(R.id.spinner2);
                TextView textView8 = ll.findViewById(R.id.textView8);

                ENCODE_TYPE encode_type;
                switch (spinner2.getSelectedItemPosition()) {
                    case 1:
                        encode_type = ENCODE_TYPE.BIT;
                        break;
                    default:
                        encode_type = ENCODE_TYPE.CHARACTER;
                }
                long standard_time_required = getRequiredTime(false, encode_type);
                long manchester_time_required = getRequiredTime(true, encode_type);
                textView8.setText("Estimated time: " + standard_time_required + "ms (Man: " + manchester_time_required  + "ms)");
            }
        };

        final EditText editText5 = ll.findViewById(R.id.editText5);
        editText5.addTextChangedListener(textWatcher);

        final EditText editTextNumber = ll.findViewById(R.id.editTextNumber);
        editTextNumber.addTextChangedListener(textWatcher);

        final Spinner spinner2 = ll.findViewById(R.id.spinner2);
        spinner2.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                editText5.setText("");
                switch (position) {
                    case 0:
                        editText5.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                        break;
                    case 1:
                        editText5.setInputType(InputType.TYPE_CLASS_NUMBER);
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        return ll;
    }

    long getRequiredTime(boolean isManchester, ENCODE_TYPE encode_type) {
        EditText editText5 = ll.findViewById(R.id.editText5);

        String time = ((EditText) ll.findViewById(R.id.editTextNumber)).getText().toString();

        if (!time.equals(""))
            PowertChannelManager.TIME = Long.parseLong(time);

        int bits;
        switch (encode_type) {
            case BIT:
                bits = PowertChannelManager.bitsToBitArray(editText5.getText().toString()).length;
                break;
            default:
                bits = PowertChannelManager.stringToBitArray(editText5.getText().toString()).length;
        }

        long time_required =
                PowertChannelManager.LONG_STREAM_SIZE*PowertChannelManager.TIME
                        + PowertChannelManager.PREAMBLE_SIZE*PowertChannelManager.TIME
                        + bits*PowertChannelManager.TIME;

        if (isManchester)    time_required *= 2;

        return time_required;
    }
}
