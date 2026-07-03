#!/usr/bin/env python3
"""把 ECDICT 基础版 CSV 过滤成精简离线词典 sqlite, 打进 app assets。

用法:
    python3 tools/build_dict.py <ecdict.csv> <lemma.en.txt> <输出.db>

数据源 (MIT 协议): https://github.com/skywind3000/ECDICT
    - ecdict.csv    基础版 ~34 万词 (word,phonetic,definition,translation,pos,
                    collins,oxford,tag,bnc,frq,exchange,detail,audio)
    - lemma.en.txt  词形还原表 (lemma/freq -> 各变形)

过滤规则 (保留任一命中):
    - tag 非空 (中考/高考/四六级/考研/托福/雅思/GRE 词表)
    - collins 星级 > 0 或 oxford 核心词 = 1
    - BNC 或当代语料词频排名 1..SLOT (常用词兜底)
剩下的生僻词交给在线词典兜底, 不占 APK 体积。
"""

import csv
import sqlite3
import sys

FREQ_SLOT = 40000  # 词频排名阈值


def main() -> None:
    if len(sys.argv) != 4:
        print(__doc__)
        sys.exit(1)
    src_csv, src_lemma, out_db = sys.argv[1:4]

    conn = sqlite3.connect(out_db)
    cur = conn.cursor()
    cur.executescript(
        """
        DROP TABLE IF EXISTS word;
        DROP TABLE IF EXISTS lemma;
        DROP TABLE IF EXISTS meta;
        CREATE TABLE word (
            word TEXT PRIMARY KEY COLLATE NOCASE,
            phonetic TEXT NOT NULL DEFAULT '',
            translation TEXT NOT NULL DEFAULT '',
            definition TEXT NOT NULL DEFAULT '',
            pos TEXT NOT NULL DEFAULT '',
            collins INTEGER NOT NULL DEFAULT 0,
            oxford INTEGER NOT NULL DEFAULT 0,
            tag TEXT NOT NULL DEFAULT '',
            frq INTEGER NOT NULL DEFAULT 0,
            exchange TEXT NOT NULL DEFAULT ''
        );
        CREATE TABLE lemma (
            form TEXT PRIMARY KEY COLLATE NOCASE,
            lemma TEXT NOT NULL
        );
        CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
        """
    )

    kept = 0
    total = 0
    kept_words = set()
    csv.field_size_limit(1 << 24)
    with open(src_csv, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        batch = []
        for row in reader:
            total += 1
            word = (row["word"] or "").strip()
            if not word or len(word) > 48:
                continue
            translation = (row["translation"] or "").strip()
            if not translation:
                continue  # 没中文释义的条目对本 app 没价值
            tag = (row["tag"] or "").strip()
            collins = int(row["collins"] or 0)
            oxford = int(row["oxford"] or 0)
            bnc = int(row["bnc"] or 0)
            frq = int(row["frq"] or 0)
            hot = (0 < bnc <= FREQ_SLOT) or (0 < frq <= FREQ_SLOT)
            if not (tag or collins > 0 or oxford > 0 or hot):
                continue
            batch.append(
                (
                    word,
                    (row["phonetic"] or "").strip(),
                    translation,
                    (row["definition"] or "").strip(),
                    (row["pos"] or "").strip(),
                    collins,
                    oxford,
                    tag,
                    frq or bnc,
                    (row["exchange"] or "").strip(),
                )
            )
            kept_words.add(word.lower())
            kept += 1
            if len(batch) >= 5000:
                cur.executemany("INSERT OR REPLACE INTO word VALUES (?,?,?,?,?,?,?,?,?,?)", batch)
                batch = []
        if batch:
            cur.executemany("INSERT OR REPLACE INTO word VALUES (?,?,?,?,?,?,?,?,?,?)", batch)

    # 词形还原: 只收「变形和原形都是词表内或原形在词表内」的映射, 供查词回退
    lemma_rows = []
    with open(src_lemma, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith(";"):
                continue
            head, _, forms = line.partition("->")
            lemma = head.split("/")[0].strip().lower()
            if lemma not in kept_words:
                continue
            for form in forms.split(","):
                form = form.strip().lower()
                if form and form != lemma and form not in kept_words:
                    lemma_rows.append((form, lemma))
    cur.executemany("INSERT OR IGNORE INTO lemma VALUES (?,?)", lemma_rows)

    cur.execute("INSERT INTO meta VALUES ('source', 'ECDICT (MIT) + BNC lemma')")
    cur.execute("INSERT INTO meta VALUES ('schema', '1')")
    cur.execute(f"INSERT INTO meta VALUES ('words', '{kept}')")
    conn.commit()
    cur.execute("VACUUM")
    conn.close()
    print(f"总条目 {total}, 保留 {kept}, 词形映射 {len(lemma_rows)} → {out_db}")


if __name__ == "__main__":
    main()
