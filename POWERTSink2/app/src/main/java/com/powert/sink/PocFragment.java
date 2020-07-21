package com.powert.sink;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.IOException;

public class PocFragment extends Fragment {

    public PocFragment () {

    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final LinearLayout ll = (LinearLayout) inflater.inflate(R.layout.poc_fragment, container, false);

        final Task[] task = new Task[1];

        final Button button2 = ll.findViewById(R.id.button2);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Switch switch1 = ll.findViewById(R.id.switch1);
                String btn_text = button2.getText().toString();

                if (btn_text.equals(getResources().getString(R.string.listen))) {
                    button2.setText(getResources().getString(R.string.stop));

                    try {
                        task[0] = new Task(ll, getContext());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    task[0].useManchesterEncoding(switch1.isChecked());
                    task[0].setRecording(true);
                    task[0].execute();
                } else {
                    button2.setText(getResources().getString(R.string.listen));
                    task[0].setRecording(false);
                    task[0] = null;
                }
            }
        });

        return ll;
    }
}
