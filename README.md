# POWERT Channel Master Thesis Project

In this repository, there is all the material of my thesis project in Computer Science.
This project wants to realize a Proof of Concept (PoC) of a new class of covert channels discovered recently.

The project is an implementation of [*POWERT Channels: A Novel Class of Covert Communication
Exploiting Power Management Vulnerabilities*](https://ieeexplore.ieee.org/document/8675190), by S. Karen Khatamifard, Longfei Wang, Amitabh Das, Selcuk Kose and Ulya R. Karpuzcu.

## Steps

- **Resources consuption**. Training and preparation of two model versions. 
- **Source-LI Android app**. Developing an app works as a sender of the bitstream using the quantized network (L) and Idle state.
- **Source-HL Android app**. Developing an app works as a sender of the bitstream using the normal (H) and the quantized (L) networks.
- **Sink1 Android app**. Developing an app in order to retrieve and decode bits using the CPU workload. It operates in an Online mode.
- **Sink2 Android app**. Developing an app in order to retrieve and decode bits using the FP unit running the first layer of the normal network. It operates in an Offline mode.

## Author
[Lorenzo Masciullo](https://www.linkedin.com/in/lorenzo-masciullo-b963b1114/).