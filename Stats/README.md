# Statistics for accuracy measurement

This Python script compares the content of the two specified files and gets you a percentage of the similarity among them.

## Usage

You can consult the helper with -h. 
```bash
python stats.py -f1 filename1 -f2 filename2
```

## Results on 48 bits

The bits of the word "powert" have been used to achieve the following scores.
The following tests have been executed in a virtual environment. The Manchester code tests are doubled in terms of time, so 2000ms are required to send a bit instead of 1000ms for the standard version.

|Sink|Source|Time for a bit (ms)|(Standard code) Accuracy|(Manchester code) Accuracy|
|--|--|--|--|--|
|Online|LI|1000|100%|100%|
|Online|LI|500|100%|100%|
|Online|LI|300|100%|100%|
|Online|LI|200|100%|92% [100%]<sub>3</sub>|
|Online|LI|100|97% [100%]<sub>3</sub>|87% [88%]<sub>3</sub> [92%]<sub>5</sub>|
|Online|LI|50|93% [89%]<sub>3</sub> [94%]<sub>5</sub>|85% [78%]<sub>3</sub> [81%]<sub>5</sub>|

|Sink|Source|Time for a bit (ms)|(Standard code) Accuracy|(Manchester code) Accuracy|
|--|--|--|--|--|
|Offline|LI|1000|100%|100%|
|Offline|LI|500|100%|100%|
|Offline|LI|300|98% [99%]<sub>3</sub> [100%]<sub>5</sub>|100%|
|Offline|LI|200|97% [100%]<sub>3</sub>|100%|
|Offline|LI|100|91% [94%]<sub>3</sub> [94%]<sub>5</sub>|84% [94%]<sub>3</sub> [84%]<sub>5</sub>|
|Offline|LI|50|88% [81%]<sub>3</sub>|78% [68%]<sub>3</sub> [70%]<sub>5</sub>|

|Sink|Source|Time for a bit (ms)|(Standard code) Accuracy|(Manchester code) Accuracy|
|--|--|--|--|--|
|Online|HL|1000|100%|100%|
|Online|HL|500|100%|100%|
|Online|HL|300|100%|100%|
|Online|HL|200|90% [89%]<sub>3</sub> [91%]<sub>5</sub>|100%|
|Online|HL|100|81% [88%]<sub>3</sub> [88%]<sub>5</sub>|86% [89%]<sub>3</sub> [91%]<sub>5</sub>|
|Online|HL|50|84%|92%|

|Sink|Source|Time for a bit (ms)|(Standard code) Accuracy|(Manchester code) Accuracy|
|--|--|--|--|--|
|Offline|HL|1000|96% [93%]<sub>3</sub>|100%|
|Offline|HL|500|94% [100%]<sub>3</sub>|100%|
|Offline|HL|300|92% [91%]<sub>3</sub> [88%]<sub>5</sub>|96% [100%]<sub>3</sub>|
|Offline|HL|200|92% [91%]<sub>3</sub> [95%]<sub>5</sub>|96% [87%]<sub>3</sub> [87%]<sub>5</sub>|
|Offline|HL|100|88% [83%]<sub>3</sub> [88%]<sub>5</sub>|86% [89%]<sub>3</sub> [85%]<sub>5</sub>|
|Offline|HL|50|71%|78%|

## Results on 256 bits

|Sink|Source|Time for a bit (ms)|(Standard code) Accuracy|(Manchester code) Accuracy|
|--|--|--|--|--|
|Online|LI|300|100%|99% [100%]<sub>3</sub>|
|Online|LI|200|100%|94% [100%]<sub>3</sub>|
|Online|LI|100|91% [92%]<sub>3</sub> [93%]<sub>5</sub>|89% [90%]<sub>3</sub> [90%]<sub>5</sub>|
|Online|LI|50|89% [91%]<sub>3</sub> [89%]<sub>5</sub>|84% [80%]<sub>3</sub> [79%]<sub>5</sub>|

|Sink|Source|Time for a bit (ms)|(Standard code) Accuracy|(Manchester code) Accuracy|
|--|--|--|--|--|
|Offline|LI|300|96% [94%]<sub>3</sub> [%]<sub>5</sub>|100%|
|Offline|LI|200|91% [94%]<sub>3</sub> [93%]<sub>5</sub>|100%|
|Offline|LI|100|88% [85%]<sub>3</sub> [89%]<sub>5</sub>|86% [85%]<sub>3</sub> [91%]<sub>5</sub>|
|Offline|LI|50|77% [86%]<sub>3</sub> [83%]<sub>5</sub>|81% [78%]<sub>3</sub> [75%]<sub>5</sub>|

|Sink|Source|Time for a bit (ms)|(Standard code) Accuracy|(Manchester code) Accuracy|
|--|--|--|--|--|
|Online|HL|300|93% [90%]<sub>3</sub> [90%]<sub>5</sub>|95% [94%]<sub>3</sub> [97%]<sub>5</sub>|
|Online|HL|200|90% [89%]<sub>3</sub> [91%]<sub>5</sub>|93% [90%]<sub>3</sub> [91%]<sub>5</sub>|
|Online|HL|100|87% [87%]<sub>3</sub> [88%]<sub>5</sub>|89% [87%]<sub>3</sub> [90%]<sub>5</sub>|
|Online|HL|50|77% [85%]<sub>3</sub> [76%]<sub>5</sub>|91% [87%]<sub>3</sub>|

|Sink|Source|Time for a bit (ms)|(Standard code) Accuracy|(Manchester code) Accuracy|
|--|--|--|--|--|
|Offline|HL|300|91% [89%]<sub>3</sub> [88%]<sub>5</sub>|92% [100%]<sub>3</sub>|
|Offline|HL|200|90% [90%]<sub>3</sub> [88%]<sub>5</sub>|88% [96%]<sub>3</sub> [96%]<sub>5</sub>|
|Offline|HL|100|88% [88%]<sub>3</sub> [88%]<sub>5</sub>|88% [90%]<sub>3</sub> [90%]<sub>5</sub>|
|Offline|HL|50|88% [83%]<sub>3</sub> [72%]<sub>5</sub>|87% [88%]<sub>3</sub> [86%]<sub>5</sub>|