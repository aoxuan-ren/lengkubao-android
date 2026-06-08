# -*- coding: utf-8 -*-
import re
import zipfile
from pathlib import Path

path = Path(r"d:\.文件\创新创业\2026小挑\市赛\专家培训展示PPT（洞察性能3）_含产品介绍.pptx")
z = zipfile.ZipFile(path)
xml = z.read("ppt/slides/slide22.xml").decode("utf-8", "ignore")
texts = re.findall(r"<a:t[^>]*>([^<]*)</a:t>", xml)
full = "\n".join(texts)
Path(r"D:\Lengkubao\project_mi\slide22_full.txt").write_text(full, encoding="utf-8")
print("lines", len(texts))
