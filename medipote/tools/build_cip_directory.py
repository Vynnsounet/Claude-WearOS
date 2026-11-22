#!/usr/bin/env python3
"""Builds src/main/assets/cip.tsv.gz: the CIP13 code of every medicine box on sale in France, with its name.

Source: Base de données publique des médicaments (ANSM, HAS, UNCAM), Licence Ouverte 2.0 (Etalab).
  https://base-donnees-publique.medicaments.gouv.fr/telechargement
Usage: python3 tools/build_cip_directory.py CIS_bdpm.txt CIS_CIP_bdpm.txt [date]
"""
import gzip
import sys


def lines(path):
    """The files come in UTF-8 or in Windows-1252 depending on the day: try both."""
    raw = open(path, "rb").read()
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError:
        text = raw.decode("cp1252")
    return text.splitlines()


def main(cis_path, cip_path, date):
    names = {}
    if True:
        for line in lines(cis_path):
            c = line.rstrip("\n").split("\t")
            # 0 CIS, 1 name, 4 marketing authorisation status ("Autorisation active")
            if len(c) > 6 and c[4].strip().lower() == "autorisation active":
                names[c[0]] = " ".join(c[1].split())
    rows = {}
    if True:
        for line in lines(cip_path):
            c = line.rstrip("\n").split("\t")
            # 0 CIS, 3 presentation status, 4 marketing declaration, 6 CIP13
            if len(c) > 6 and c[0] in names and c[3].strip().lower() == "présentation active" and "commercialisation" in c[4].lower():
                cip = c[6].strip()
                if len(cip) == 13 and cip.isdigit():
                    rows[cip] = names[c[0]]
    out = "src/main/assets/cip.tsv.gz"
    with gzip.open(out, "wt", encoding="utf-8", compresslevel=9) as g:
        g.write(f"# BDPM {date} - Licence Ouverte 2.0 - {len(rows)} boites\n")
        for cip in sorted(rows):
            g.write(f"{cip}\t{rows[cip]}\n")
    print(len(rows), "boxes ->", out)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else "")
