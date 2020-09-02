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
|1|LI|1000|100%|100%|
|1|LI|500|100%|100%|
|1|LI|300|100%|100%|
|1|LI|200|100%|92% [100%]<sub>3</sub>|
|1|LI|100|97% [100%]<sub>3</sub>|87% [88%]<sub>3</sub> [92%]<sub>5</sub>|
|1|LI|50|93% [89%]<sub>3</sub> [94%]<sub>5</sub>|85% [78%]<sub>3</sub> [81%]<sub>5</sub>|

|Sink|Source|Time for a bit (ms)|(Standard code) Accuracy|(Manchester code) Accuracy|
|--|--|--|--|--|
|2|LI|1000|100%|100%|
|2|LI|500|100%|100%|
|2|LI|300|98% [99%]<sub>3</sub> [100%]<sub>5</sub>|100%|
|2|LI|200|97% [100%]<sub>3</sub>|100%|
|2|LI|100|91% [94%]<sub>3</sub> [94%]<sub>5</sub>|84% [94%]<sub>3</sub> [84%]<sub>5</sub>|
|2|LI|50|88% [81%]<sub>3</sub> [?%]<sub>5</sub>|78% [68%]<sub>3</sub> [70%]<sub>5</sub>|

|Sink|Source|Time for a bit (ms)|(Standard code) Accuracy|(Manchester code) Accuracy|
|--|--|--|--|--|
|1|HL|1000|100%|100%|
|1|HL|500|100%|100%|
|1|HL|300|100%|100%|
|1|HL|200|90% [89%]<sub>3</sub> [91%]<sub>5</sub>|100%|
|1|HL|100|81% [88%]<sub>3</sub> [88%]<sub>5</sub>|86% [89%]<sub>3</sub> [91%]<sub>5</sub>|
|1|HL|50|84%|92%|

|Sink|Source|Time for a bit (ms)|(Standard code) Accuracy|(Manchester code) Accuracy|
|--|--|--|--|--|
|2|HL|1000|96% [93%]<sub>3</sub>|100%|
|2|HL|500|94% [100%]<sub>3</sub>|100%|
|2|HL|300|92% [91%]<sub>3</sub> [88%]<sub>5</sub>|96% [100%]<sub>3</sub>|
|2|HL|200|92% [91%]<sub>3</sub> [95%]<sub>5</sub>|96% [87%]<sub>3</sub> [87%]<sub>5</sub>|
|2|HL|100|88% [83%]<sub>3</sub> [88%]<sub>5</sub>|86% [89%]<sub>3</sub> [?%]<sub>5</sub>|
|2|HL|50|71%|78%|

## Results on 256 bits

|Sink|Source|Time for a bit (ms)|(Standard code) Accuracy|(Manchester code) Accuracy|
|--|--|--|--|--|
|1|LI|200|100%|94%|
|1|LI|100|91%|89%|
|1|LI|50|89%|84%|

|Sink|Source|Time for a bit (ms)|(Standard code) Accuracy|(Manchester code) Accuracy|
|--|--|--|--|--|
|2|LI|200|91%|92%|
|2|LI|100|88%|93%|
|2|LI|50|77%|82%|

|Sink|Source|Time for a bit (ms)|(Standard code) Accuracy|(Manchester code) Accuracy|
|--|--|--|--|--|
|1|HL|200|90%|93%|
|1|HL|100|87%|89%|
|1|HL|50|77%|91%|

|Sink|Source|Time for a bit (ms)|(Standard code) Accuracy|(Manchester code) Accuracy|
|--|--|--|--|--|
|2|HL|200|90%|88%|
|2|HL|100|88%|90%|
|2|HL|50|88%|82%|