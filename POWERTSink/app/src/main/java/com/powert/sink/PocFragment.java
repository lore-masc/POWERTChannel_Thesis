package com.powert.sink;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

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
                String btn_text = button2.getText().toString();

                if (btn_text.equals(getResources().getString(R.string.listen))) {
                    button2.setText(getResources().getString(R.string.stop));
                    task[0] = new Task(ll, getContext());
                    task[0].setRecording(true);
                    task[0].execute();
                } else {
                    button2.setText(getResources().getString(R.string.listen));
                    task[0].setRecording(false);
                    task[0].cancel(true);
                    task[0] = null;
                }
            }
        });

        return ll;
    }
}
