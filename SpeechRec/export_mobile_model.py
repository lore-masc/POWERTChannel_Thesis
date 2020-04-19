from pathlib import Path

import torch

device = 'cuda:0'
model = 'model/weights_dn_22_12'
if Path(model).exists():
    net = torch.load(model)

net.eval()
dsize = (32, 1, 40, 32)
input_tensor = torch.randn(dsize).to(device)

script_model = torch.jit.trace(net, input_tensor)
script_model.save("model/mobile_densenet_22_12.pt")