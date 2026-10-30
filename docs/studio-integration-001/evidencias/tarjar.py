import glob
import json
import os
import subprocess

PREFIX = "C:" + chr(92) + "Users" + chr(92) + os.environ["USERNAME"]
boxes = {}


def add(k, b):
    boxes.setdefault(k, []).append(b)


for line in open("ocrw.jsonl", encoding="utf-8-sig"):
    d = json.loads(line)
    t = d["text"]
    k = d["file"][:2]
    x0, y0, x1, y1 = [v / 3 for v in d["box"]]
    if t.startswith(PREFIX):
        w = (x1 - x0) / len(t)
        add(k, (int(x0) - 2, int(y0) - 2, int(x0 + len(PREFIX) * w) + 4, int(y1) + 2))
manual = {
    "01": [(126, 242, 229, 258), (98, 292, 201, 308)],
    "04": [(126, 259, 229, 275), (98, 309, 201, 325)],
    "03": [(84, 74, 187, 91)],
    "12": [(84, 74, 187, 91)],
    "17": [(84, 74, 187, 91)],
    "06": [(117, 78, 641, 233)],
    "07": [(70, 113, 163, 131), (70, 164, 163, 182)],
    "10": [(30, 115, 342, 140), (30, 180, 480, 205), (106, 270, 726, 287)],
    "15": [(30, 115, 342, 140), (30, 180, 480, 205), (106, 270, 726, 287)],
    "14": [(0, 984, 1640, 1000)],
}
for k, v in manual.items():
    for b in v:
        add(k, b)
for f in sorted(glob.glob("png-orig/*.png")):
    k = os.path.basename(f)[:2]
    args = [",".join(map(str, b)) for b in boxes.get(k, [])]
    print(k, len(args), end="  ")
    subprocess.run(["java", "Png.java", "redact", f, "png-red/" + os.path.basename(f)] + args, check=True)
print()
