# Speech Recognition CNN

This Python script implements a double version of DenseNet in order to use a different and notable amount of computable resources.

The network will be able to classify, once trained, vocal commands. 

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

Training with GPU for *light* version of the network and replace the model's weights after.
```bash
python main.py -d data/ -t -e 10 -b 128 -v low -g -s
```

Prediction with GPU for the *.wav* input files.
```bash
python main.py -d data/input/ -v low -g
```

## Export for mobile applications

You can export the actually trained weights into a PyTorch trace in order to use the network on mobile applications.
You have to specify an example of a tensor as input. You can also generate an example randomly.
In this use case, a double version of the network exists, so it is possible to choose one of them. 
```bash
python export_mobile_model.py -d data/input/recorded_audio.wav -v high
```

At the end, you can find a *.pth* file containing the network trace.

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
|dn_22_12	| 0.07		| 1.27|
|dn_250_24	| 15.33		| 220.13|

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

## License
All resources here posted are open-source.