# Statistics for accuracy measurement

This Python script compares the content of the two specified files and gets you a percentage of the similarity among them.

## Usage

You can consult the helper with -h. 
```bash
python stats.py -f1 filename1 -f2 filename2
```

## Results

The bits of the word "powert" have been used to achieve the following scores.

|Sink|Source|Time for a bit (ms)|Accuracy|
|--|--|--|--|
|1|LI|1000|100%|
|1|LI|500|100%|
|1|LI|300|100%|
|1|LI|200|100%|
|1|LI|100|100%|
|1|LI|50|0%|

|Sink|Source|Time for a bit (ms)|Accuracy|
|--|--|--|--|
|2|LI|1000|100%|
|2|LI|500|100%|
|2|LI|300|98%|
|2|LI|200|92%|
|2|LI|100|80%|
|2|LI|50|60%|

|Sink|Source|Time for a bit (ms)|Accuracy|
|--|--|--|--|
|1|HL|1000|100%|
|1|HL|500|100%|
|1|HL|300|100%|
|1|HL|200|98%|
|1|HL|100|81%|
|1|HL|50|0%|

|Sink|Source|Time for a bit (ms)|Accuracy|
|--|--|--|--|
|2|HL|1000|96%|
|2|HL|500|94%|
|2|HL|300|92%|
|2|HL|200|92%|
|2|HL|100|88%|
|2|HL|50|71%|