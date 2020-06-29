package com.powert.source;

import android.content.Context;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ModuleForwarder {
    public enum VERSION {LOW, HIGH};
    private static final String LOW_MODEL = "mobile_shufflenet_v2_05_quantized.pt";
    private static final String HIGH_MODEL = "mobile_shufflenet_v2_05.pt";
    long[] shape = {1, 1, 40, 32};
    private Module module_low;
    private Module module_high;
    private IValue iValue;

    public ModuleForwarder(Context context) throws IOException {
        this.module_low = Module.load(assetFilePath(context, LOW_MODEL));
        this.module_high = Module.load(assetFilePath(context, HIGH_MODEL));
//        PyTorchAndroid.setNumThreads(1);
    }

    public void prepare(float[] input) {
        Tensor inputTensor = Tensor.fromBlob(input, shape);
        this.iValue = IValue.from(inputTensor);
    }

    public void prepare(int[] input) {
        Tensor inputTensor = Tensor.fromBlob(input, shape);
        this.iValue = IValue.from(inputTensor);
    }

    public float[] forward(VERSION version) {
        Tensor outputTensor;
        if (version == VERSION.LOW) {
            outputTensor = module_low.forward(this.iValue).toTensor();
        } else {
            outputTensor = module_high.forward(this.iValue).toTensor();
        }
        return outputTensor.getDataAsFloatArray();
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
