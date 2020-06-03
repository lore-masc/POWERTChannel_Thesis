# Speech Recognition CNN

This Python script implements different versions of convolutional neural networks (CNN) in order to use a different and notable amount of computable resources.

The network will be able to classify, once trained, some vocal command classes. 

## Requirements

Python 3 has been used to develop these scripts.

This project uses [PyTorch](https://pytorch.org/docs/stable/index.html) library in order to implement Neural Network. All Python packages needed are listed in the *requirements.txt* file.

```bash
pip install -r requirements.txt 
```

## Data preparation

Download the [Google Speech Commands](https://subscription.packtpub.com/book/big_data_and_business_intelligence/9781789132212/5/ch05lvl1sec42/google-speech-commands-dataset) dataset executing the bash script.

```bash
bash download_dataset.sh
```
Split the whole dataset between the trainset, validation set, and test set. All sets will be stored in the *data* directory.

```bash
python split_dataset.py data
```

We **don't need of validation set**, so you can copy that in one of the other sets.

## Usage

You can consult the helper with -h.
```bash
python main.py -h
```

Training with GPU for shufflenet model and replace the model's weights after.
```bash
python main.py -m shufflenet -d data/ -t -e 10 -b 128 -g -s
```

Prediction with GPU for the *.wav* input files.
```bash
python main.py -m shufflenet -d data/input/ -g
```

## Export for mobile applications

You can export the actually trained weights into a PyTorch trace in order to use the network on mobile applications.
You have to specify an example of a tensor as input. You can also generate an example randomly.
In this use case, a double version of the network exists, so it is possible to choose one of them. 
```bash
python export_mobile_model.py -m shufflenet -d data/input/recorded_audio.wav
```

In the end, you can find a *.pt* file containing the network trace. You can load it as a pre-trained Module into a mobile Application.

In terms of efficiency and size, a quantized version of the network should be better. 
```bash
python export_mobile_model.py -m shufflenet -d data/input/recorded_audio.wav -q
```

## Code details
### How to transform wav audio files into tensors
Basically, the script makes the following steps:
 - Load audio bytes. The ``librosa.load`` method has been used from [Librosa](https://librosa.github.io/librosa/) library;
 - truncate possible excesses. The ``FixAudioLength()`` method allows resizing all provided samples in a fixed size. If an audio source is shorter, padding of zeros is added;
 - Mel Spectrogram has been produced using ``librosa.feature.melspectrogram`` and ``librosa.power_to_db``.
 - finally, a PyTorch float tensor is created.
 
All used methods are manually implemented and you can find them into ``transforms/transforms_wav.py`` script.  
All these steps are resumed into the following code snippet taken from the ``predict`` function of the ``main.py`` script.
```python
feature_transform = Compose([ToMelSpectrogram(n_mels=40), ToTensor('mel_spectrogram', 'input')])
transform = Compose([LoadAudio(), FixAudioLength(), feature_transform])

audio = transform({'path': input_path})
audio_loader = DataLoader(audio, batch_size=1, shuffle=False,
                          pin_memory=use_gpu, num_workers=2)
```

### How to count operations in network
[Thop](https://github.com/Lyken17/pytorch-OpCounter) library has been used in order to keep track of the amount of float and integer operations to execute during a forward.
These parameters will be used in order to classify the heaviness of the networks.

A random tensor has been loaded on the network in order to count the operations. The function ``profile`` returns FLoating OPerations (express in Giga) and multiply-accumulate operations (express in Mega).   
```python
dsize = (32, 1, 40, 32)
r_input = torch.randn(dsize).to(device)
macs, params = profile(net, inputs=(r_input,), verbose=False)
print("\n%s\t| %s" % ("Params(M)", "FLOPs(G)"))
print("%.2f\t\t| %.2f" % (params / (1000 ** 2), macs / (1000 ** 3)))
```

Results:
|Name 		|Params(M)	| FLOPs(G)|
|--|--|--|
|ResNet_18	| 11.18		| 1.69|
|MobileNetV2	| 0.06		| 0.02|
|ShufflenetV2 0.5x   | 0.35 | 0.04|

### How to store and to load trained weights
If requested by input, the ``main.py`` script can save the trained weights in a indicate *.pth* file.
```python
torch.save(net.state_dict(), model_path)
```

The loading of existing weights can be done as follow.
```python
state_dict = torch.load(model_path)
net.load_state_dict(state_dict, strict=False)
```

### How to export network trace
The following code snippet produces a *.pt* file that can be loaded in a mobile app project.
```python
net.cpu()
net.eval()
traced_script_module = torch.jit.trace(net, audio)
traced_script_module.save(export_model_path)
```

### How to export a quantized version
In the case of neural networks equipped with convolutional modules, the better quantization is the *Post Training Static Quantization*. 
You can refer to the official documentation of PyTorch [here](https://pytorch.org/tutorials/advanced/static_quantization_tutorial.html#post-training-static-quantization).
Now, we can summarize the main steps that characterize a quantization procedure.
- You have some quantization engines available in the system. There are two main engines: *fbgemm* and *qnnpack*. The last one was chosen in this case.
**If you use Windows systems** or, for any reason, you cannot take advantage of any quantization engine, then you can use Google Colab.
- You have to develop a quantizable version of the model. Torchvision already provides the main possible quantizable models in its [repository](https://github.com/pytorch/vision/tree/master/torchvision/models/quantization).
A single channel input is required for this occasion, so the first convolutional layer of the network has been replaced. You can see it in ``_resnet`` function, available [here](https://github.com/lore-masc/POWERTChannel_Thesis/blob/master/SpeechRec/model/resnet.py#L46). 
- **Warning: Quantizable does not mean Quantized!** Training is done before then the conversion. 
It is advisable to implement an own ``quantize_model`` function, as [here](https://github.com/lore-masc/POWERTChannel_Thesis/blob/master/SpeechRec/model/resnet.py#L23).
- In order to reach an acceptable accuracy in the quantized model, you have to perform a sort of training calibration, as you can see in the same method.
In the calibration, you can pass a few random samples into the network.

Other rules are described in the [Quantization section](https://pytorch.org/docs/stable/quantization.html#quantization-workflows) of the official documentation.

## License
All resources here posted are open-source.