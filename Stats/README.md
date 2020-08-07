# Statistics for accuracy measurement

This Python script compares the content of the two specified files and gets you a percentage of the similarity among them.

## Usage

You can consult the helper with -h. 
```bash
python stats.py -f1 filename1 -f2 filename2
```

## Results

The bits of the word "powert" have been used to achieve the following scores.
The following tests have been executed in a virtual environment. The Manchester code tests are doubled in terms of time, so 2000ms are required to send a bit instead of 1000ms for the standard version.

|Sink|Source|Time for a bit (ms)|(Standard code) Accuracy|(Manchester code) Accuracy|
|--|--|--|--|--|
|1|LI|1000|100%|100%|
|1|LI|500|100%|100%|
|1|LI|300|100%|100%|
|1|LI|200|100%|100%|
|1|LI|100|100%|100%|
|1|LI|50|88%|92%|

|Sink|Source|Time for a bit (ms)|(Standard code) Accuracy|(Manchester code) Accuracy|
|--|--|--|--|--|
|2|LI|1000|100%|92%|
|2|LI|500|100%|92%|
|2|LI|300|98%|92%|
|2|LI|200|97%|92%|
|2|LI|100|91%|87%|
|2|LI|50|88%|78%|

|Sink|Source|Time for a bit (ms)|(Standard code) Accuracy|(Manchester code) Accuracy|
|--|--|--|--|--|
|1|HL|1000|100%|98%|
|1|HL|500|100%|88%|
|1|HL|300|100%|90%|
|1|HL|200|98%|90%|
|1|HL|100|81%|86%|
|1|HL|50|84%|92%|

|Sink|Source|Time for a bit (ms)|(Standard code) Accuracy|(Manchester code) Accuracy|
|--|--|--|--|--|
|2|HL|1000|96%|92%|
|2|HL|500|94%|87%|
|2|HL|300|92%|91%|
|2|HL|200|92%|92%|
|2|HL|100|88%|86%|
|2|HL|50|71%|85%|