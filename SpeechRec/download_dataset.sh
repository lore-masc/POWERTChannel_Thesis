#!/usr/bin/env bash
wget -nc http://download.tensorflow.org/data/speech_commands_v0.01.tar.gz
mkdir -p data/audio/
tar -xzvf speech_commands_v0.01.tar.gz -C data/audio/