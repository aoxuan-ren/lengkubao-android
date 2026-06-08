# -*- coding: utf-8 -*-
import re
import zipfile
from pathlib import Path

path = Path(r"d:\.文件\创新创业\2026小挑\市赛\专家培训展示PPT（洞察性能3）_含产品介绍.pptx")
z = zipfile.ZipFile(path)
rels = z.read("ppt/_rels/presentation.xml.rels").decode()
pres = z.read("ppt/presentation.xml").decode()

# python-pptx order via sldIdLst
sld_ids = re.findall(r'r:id="(rId\d+)"', pres.split("sldIdLst")[1].split("sldIdLst")[0] if "sldIdLst" in pres else pres)
mapping = {}
for m in re.finditer(r'Id="(rId\d+)"[^>]*Target="([^"]+)"', rels):
    mapping[m.group(1)] = m.group(2)

out = Path(r"D:\Lengkubao\project_mi\slide_order_verify.txt")
with out.open("w", encoding="utf-8") as f:
    block = pres.split("<p:sldIdLst>")[1].split("</p:sldIdLst>")[0]
    rids = re.findall(r'r:id="(rId\d+)"', block)
    for i, rid in enumerate(rids, 1):
        slide_file = mapping.get(rid, "?")
        xml_path = slide_file if slide_file.startswith("ppt/") else "ppt/" + slide_file
        t = z.read(xml_path).decode("utf-8", "ignore")
        tx = "".join(re.findall(r"<a:t[^>]*>([^<]*)</a:t>", t))[:80]
        f.write(f"{i:2d} {slide_file} {tx}\n")

print("slides", len(rids))
