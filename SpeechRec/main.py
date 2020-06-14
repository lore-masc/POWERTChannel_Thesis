# coding: utf-8

__author__ = 'Lorenzo Masciullo'

import argparse
import os
from pathlib import Path

from thop import profile
from torch import nn
from torch.utils.data import DataLoader
from torch.utils.data.sampler import WeightedRandomSampler
from torchvision.models.quantization import shufflenet_v2_x0_5, mobilenet_v2, resnet18
from torchvision.transforms import Compose
from tqdm import *

from speech_commands_dataset import *
from transforms import *

'''
Input arguments
    batch_size: Size of a mini-batch
    root: The root folder of audio
    use_gpu: GPU where you want to train your network
'''


def get_data(batch_size, root, use_gpu=True):
    data_aug_transform = Compose([ChangeAmplitude(), ChangeSpeedAndPitchAudio(), FixAudioLength(), ToSTFT(),
                                  StretchAudioOnSTFT(), TimeshiftAudioOnSTFT(), FixSTFTDimension()])
    bg_dataset = BackgroundNoiseDataset(root + "train/_background_noise_", data_aug_transform)
    add_bg_noise = AddBackgroundNoiseOnSTFT(bg_dataset)

    train_feature_transform = Compose([ToMelSpectrogramFromSTFT(n_mels=40), DeleteSTFT(),
                                       ToTensor('mel_spectrogram', 'input')])
    train_dataset = SpeechCommandsDataset(root + "train",
                                          Compose([LoadAudio(), data_aug_transform,
                                                   add_bg_noise, train_feature_transform]))

    valid_feature_transform = Compose([ToMelSpectrogram(n_mels=40), ToTensor('mel_spectrogram', 'input')])
    valid_dataset = SpeechCommandsDataset(root + "test",
                                          Compose([LoadAudio(), FixAudioLength(), valid_feature_transform]))

    weights = train_dataset.make_weights_for_balanced_classes()
    sampler = WeightedRandomSampler(weights, len(weights))
    train_dataloader = DataLoader(train_dataset, batch_size=batch_size, sampler=sampler,
                                  pin_memory=use_gpu, num_workers=6)
    valid_dataloader = DataLoader(valid_dataset, batch_size=batch_size, shuffle=False,
                                  pin_memory=use_gpu, num_workers=6)

    return train_dataloader, valid_dataloader


'''
Input arguments
    model: String name of the model
'''


def get_model_weights(model):
    model_path = 'model/'
    if model == 'shufflenet':
        model_path = 'model/weights_shufflenet_v2_05.pth'
    elif model == 'mobilenet':
        model_path = 'model/weights_mobilenet_v2.pth'
    elif model == 'resnet':
        model_path = 'model/weights_resnet_18.pth'
    return model_path


'''
Input arguments
    model_path: String path of the loaded model
'''


def prepare_model(model):
    if model == 'shufflenet':
        net = shufflenet_v2_x0_5(num_classes=len(CLASSES), quantize=False)
        net.conv1[0] = nn.Conv2d(1, 24, 3, 2, 1, bias=False)
    elif model == 'mobilenet':
        net = mobilenet_v2(num_classes=len(CLASSES), quantize=False, width_mult=0.03125)
        net.features[0][0] = nn.Conv2d(1, 8, kernel_size=(3, 3), stride=(2, 2), padding=(1, 1), bias=False)
    elif model == 'resnet':
        net = resnet18(num_classes=len(CLASSES), quantize=False)
        net.conv1 = nn.Conv2d(1, 64, kernel_size=7, stride=2, padding=3, bias=False)
    return net


'''
Input arguments
    model_path: String path of the loaded model
'''


def initialize_net(model):
    net = prepare_model(model=model)
    model_path = get_model_weights(model=model)

    if Path(model_path).exists():
        state_dict = torch.load(model_path)
        net.load_state_dict(state_dict, strict=False)

    return net


'''
Input arguments
    model: Model used for the test
    lr: Learning rate for training speed
    wd: Weight decay co-efficient for regularization of weights
    momentum: Momentum for SGD optimizers
'''


def get_optimizer(model, lr, wd, momentum):
    optimizer = torch.optim.SGD(model.parameters(), lr=lr, momentum=momentum, weight_decay=wd)

    return optimizer


def get_cost_function():
    cost_function = torch.nn.CrossEntropyLoss()
    return cost_function


'''
Input arguments
    net: Model used for the test
    data_loader: Data used for the test
    optimizer: Optimizer algorithm 
    cost_function: Cost function in order to estimate loss
    device: Name of device to perform operations
'''


def train(net, data_loader, optimizer, cost_function, device='cuda:0'):
    samples = 0.
    cumulative_loss = 0.
    cumulative_accuracy = 0.

    net.train()  # Strictly needed if network contains layers which has different behaviours between train and test

    pbar = tqdm(data_loader, unit="audios", unit_scale=data_loader.batch_size, position=0, leave=True)
    for batch in pbar:
        inputs = batch['input']
        inputs = torch.unsqueeze(inputs, 1)
        targets = batch['target']

        # Load data into GPU
        inputs = inputs.to(device)
        targets = targets.to(device)

        # Forward pass
        outputs = net(inputs)

        # Apply the loss
        loss = cost_function(outputs, targets)

        # Reset the optimizer

        # Backward pass
        loss.backward()

        # Update parameters
        optimizer.step()

        optimizer.zero_grad()

        # Better print something, no?
        samples += inputs.shape[0]
        cumulative_loss += loss.item()
        _, predicted = outputs.max(1)
        cumulative_accuracy += predicted.eq(targets).sum().item()

        # Free cuda memory
        torch.cuda.empty_cache()

    return cumulative_loss / samples, cumulative_accuracy / samples * 100


'''
Input arguments
    net: Model used for the test
    data_loader: Data used for the test
    cost_function: Cost function in order to estimate loss
    device: Name of device to perform operations
'''


def test(net, data_loader, cost_function, device='cuda:0', num_batches=0):
    samples = 0.
    cumulative_loss = 0.
    cumulative_accuracy = 0.

    net.eval()  # Strictly needed if network contains layers which has different behaviours between train and test
    done_batches = 0

    with torch.no_grad():
        pbar = tqdm(data_loader, unit="audios", unit_scale=data_loader.batch_size, position=0, leave=True)
        for batch in pbar:
            inputs = batch['input']
            inputs = torch.unsqueeze(inputs, 1)
            targets = batch['target']

            # Load data into GPU
            inputs = inputs.to(device)
            targets = targets.to(device)

            # Forward pass
            outputs = net(inputs)

            # Apply the loss
            loss = cost_function(outputs, targets)
            done_batches += 1

            # Better print something
            samples += inputs.shape[0]
            cumulative_loss += loss.item()  # Note: the .item() is needed to extract scalars from tensors
            _, predicted = outputs.max(1)
            cumulative_accuracy += predicted.eq(targets).sum().item()

            # Free cuda memory
            torch.cuda.empty_cache()

            if 0 < num_batches <= done_batches:
                return cumulative_loss / samples, cumulative_accuracy / samples * 100

    return cumulative_loss / samples, cumulative_accuracy / samples * 100


'''
Input arguments
    input_path: String path of the input file to predict
    use_gpu: Boolean value to use gpu
'''


def predict(input_path, net, use_gpu=True):
    print("Predicting " + input_path + "...")
    device = 'cuda:0' if use_gpu else 'cpu'

    feature_transform = Compose([ToMelSpectrogram(n_mels=40), ToTensor('mel_spectrogram', 'input')])
    transform = Compose([LoadAudio(), FixAudioLength(), feature_transform])

    audio = transform({'path': input_path})
    audio_loader = DataLoader(audio, batch_size=1, shuffle=False,
                              pin_memory=use_gpu, num_workers=2)

    net.eval()  # Strictly needed if network contains layers which has different behaviours between train and test

    audio = audio_loader.dataset['input']
    audio = torch.unsqueeze(audio, 1)
    audio = audio.view(1, 1, 40, 32)

    # Load data into GPU
    audio = audio.to(device)

    # Forward into network
    output = net.forward(audio)
    class_index = output.data.max(1, keepdim=True)[1]

    torch.cuda.empty_cache()
    return CLASSES[class_index]


'''
Input arguments
    batch_size: Size of a mini-batch
    use_gpu: GPU where you want to train your network
    learning_rate: Learning rate for training speed
    weight_decay: Weight decay co-efficient for regularization of weights
    momentum: Momentum for SGD optimizer
    epochs: Number of epochs for training the network
    root: The root folder of audio
    save: Boolean value for overwrite the trained model
    perform_training: Boolean value to perform the training process 
'''


def main(batch_size=128,
         use_gpu=True,
         model='shufflenet',
         learning_rate=0.001,
         weight_decay=0.000001,
         momentum=0.9,
         epochs=50,
         root="data/",
         save=True,
         perform_training=True):
    if use_gpu:
        device = 'cuda:0'
        torch.cuda.empty_cache()
    else:
        device = 'cpu'

    net = initialize_net(model=model).to(device)

    # Op Counter
    dsize = (32, 1, 40, 32)
    r_input = torch.randn(dsize).to(device)
    macs, params = profile(net, inputs=(r_input,), verbose=False)
    print("\n%s\t| %s" % ("Params(M)", "FLOPs(G)"))
    print("%.2f\t\t| %.2f" % (params / (1000 ** 2), macs / (1000 ** 3)))
    print()

    if not perform_training:
        input_audios = [predict(input_path=os.path.join(root, r), net=net, use_gpu=use_gpu) for r in os.listdir(root)]
        print(input_audios)
        return
    else:
        if root is '':
            print("You need to specify the dataset path")
            return
        train_loader, test_loader = get_data(batch_size=batch_size,
                                             root=root, use_gpu=use_gpu)

        print("{")
        optimizer = get_optimizer(net, learning_rate, weight_decay, momentum)

        cost_function = get_cost_function()

        # train_loss, train_accuracy = test(net, train_loader, cost_function, device)
        test_loss, test_accuracy = test(net, test_loader, cost_function, device)
        print('\nTest before training')
        # print('\t{"Training loss": %.5f, "Training accuracy": %.2f},' % (train_loss, train_accuracy))
        print('\t{"Test loss": %.5f, "Test accuracy": %.2f},' % (test_loss, test_accuracy))
        # print('-----------------------------------------------------')

        train_loss_curve = []
        train_accuracy_curve = []
        test_loss_curve = []
        test_accuracy_curve = []

        print('\t"training": [')

        for e in range(epochs):
            train_loss, train_accuracy = train(net, train_loader, optimizer, cost_function, device)
            test_loss, test_accuracy = test(net, test_loader, cost_function, device)
            print('\n\t\tEpoch: {:d}'.format(e + 1))
            print('\t\t\t{"Training loss": %.5f, "Training accuracy": %.2f},' % (train_loss, train_accuracy))
            print('\t\t\t{"Test loss": %.5f, "Test accuracy": %.2f},' % (test_loss, test_accuracy))
            # print('-----------------------------------------------------')

            train_loss_curve.append(train_loss)
            train_accuracy_curve.append(train_accuracy)
            test_loss_curve.append(test_loss)
            test_accuracy_curve.append(test_accuracy)

            # saving model
            if save:
                print("Saving weights")
                model_path = get_model_weights(model=model)
                torch.save(net.state_dict(), model_path)

        train_loss, train_accuracy = test(net, train_loader, cost_function, device)
        test_loss, test_accuracy = test(net, test_loader, cost_function, device)
        print('\n\t\tTest after training')
        print('\t\t{"Training loss": %.5f, "Training accuracy": %.2f},' % (train_loss, train_accuracy))
        print('\t\t{"Test loss": %.5f, "Test accuracy": %.2f}' % (test_loss, test_accuracy))
        # print('-----------------------------------------------------')

        print('\t],')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Detect speech command by neural network')
    parser.add_argument('-d', '--dataset', action='store', dest='dataset', default='data/',
                        choices=['data/', 'data/input/'],
                        help='Write the dataset relative path only if you have to perform training; '
                             'Specify the inputs folder otherwise.')
    parser.add_argument('-m', '--model', action='store', dest='model', required=True,
                        choices=['shufflenet', 'mobilenet', 'resnet'],
                        help='True if you want perform quantization conversion.')
    parser.add_argument('-e', '--epochs', action='store', dest='epochs', type=int,
                        help='Specify the number of epochs to run during the train.', default=10)
    parser.add_argument('-b', '--batch', action='store', dest='batch_size', type=int, default=32,
                        help='Specify the size of a single batch.')
    parser.add_argument('-t', '--training', action='store_true', dest='training',
                        help='Set option in order to perform training.')
    parser.add_argument('-s', '--save', action='store_true', dest='save',
                        help='Replace old weights after the training.')
    parser.add_argument('-g', '--gpu', action='store_true', dest='gpu', default=False,
                        help='True if you want perform operations with a gpu device. CPU will be used otherwise.')

    args = parser.parse_args()

    main(root=args.dataset, model=args.model, epochs=args.epochs, batch_size=args.batch_size,
         perform_training=args.training,
         save=args.save, use_gpu=args.gpu)
