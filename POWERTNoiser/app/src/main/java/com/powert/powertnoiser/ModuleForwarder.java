package com.powert.powertnoiser;

import android.content.Context;
import android.os.Process;
import android.util.Log;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.PyTorchAndroid;
import org.pytorch.Tensor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ModuleForwarder {
    private static final String MODEL = "mobile_shufflenet_v2_05firstLayer.pt";
    long[] shape = {1, 1, 40, 32};
    private Module module;
    private IValue iValue;

    public ModuleForwarder(Context context) throws IOException {
        this.module = Module.load(assetFilePath(context, MODEL));
        PyTorchAndroid.setNumThreads(10);
    }

    public void prepare(float[] input) {
        Tensor inputTensor = Tensor.fromBlob(input, shape);
        this.iValue = IValue.from(inputTensor);
    }

    public void forward(long sample_time) {
        android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < sample_time ) {
//            long p1 = System.currentTimeMillis();
            module.forward(this.iValue).toTensor();
//            Log.d("SINK2", "time: " + (System.currentTimeMillis() - p1));
        }
    }

    private static String assetFilePath(Context context, String assetName) throws IOException {
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
