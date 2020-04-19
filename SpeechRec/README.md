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

## License
All resources here posted are open-source.