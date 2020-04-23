from pathlib import Path
import torch
from torch.utils.data import DataLoader
from torchvision.transforms import Compose
from model import DenseNet
from speech_commands_dataset import CLASSES
from transforms import ToMelSpectrogram, ToTensor, LoadAudio, FixAudioLength

device = torch.device("cpu")

input_path = 'data/input/recorded_audio.wav'
import_model_path = 'model/weights_dn_22_12.pth'
export_model_path = 'model/mobile_densenet_22_12.pt'

net = DenseNet(depth=22, growthRate=12, compressionRate=2, num_classes=len(CLASSES), in_channels=1)
if Path(import_model_path).exists():
    # forcing all GPU tensors to be in CPU while loading
    state_dict = torch.load(import_model_path, map_location=lambda storage, loc: storage)
    net.load_state_dict(state_dict, strict=False)

dsize = (1, 1, 40, 32)

feature_transform = Compose([ToMelSpectrogram(n_mels=40), ToTensor('mel_spectrogram', 'input')])
transform = Compose([LoadAudio(), FixAudioLength(), feature_transform])

audio = transform({'path': input_path})
audio_loader = DataLoader(audio, batch_size=1, shuffle=False,
                          pin_memory=False, num_workers=2)
audio = audio_loader.dataset['input']
audio = torch.unsqueeze(audio, 1)
audio = audio.view(1, 1, 40, 32)

net = net.to(device)
net.cpu()
net.eval()
traced_script_module = torch.jit.trace(net, audio)
traced_script_module.save(export_model_path)
