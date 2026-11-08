"""Control searches through the public site, before and after an index change.

  python control_search.py fetch QUERIES.tsv OUT.json [BASE_URL]
      QUERIES.tsv: journal <TAB> term <TAB> type(Post|Comment|empty) <TAB> year(or empty)
      Calls BASE_URL/search (default https://getlj.com) once per line, 2 s apart.
      Only the site's own index and database are read; LiveJournal is not contacted.

  python control_search.py compare BEFORE.json AFTER.json
      Every result found before must be found after, with the same title and
      highlighted text. When the "after" page is full (400), only results
      newer than its oldest entry are required. New results (documents added
      in between) are counted but are not an error. Exit code 0 when OK.
"""
import json
import sys
import time
import urllib.parse
import urllib.request

PAGE = 400


def fetch(queries, out, base="https://getlj.com"):
    results = []
    with open(queries, encoding="utf-8") as f:
        lines = [l.rstrip("\n") for l in f if l.strip()]
    for i, line in enumerate(lines):
        journal, term, typ, year = (line.split("\t") + ["", "", "", ""])[:4]
        params = {"term": term, "journal": journal, "type": typ, "year": year}
        url = base + "/search?" + urllib.parse.urlencode(params)
        entry = {"q": line}
        try:
            with urllib.request.urlopen(url, timeout=120) as r:
                entry["status"] = r.status
                entry["results"] = json.loads(r.read().decode("utf-8"))
        except Exception as e:  # keep going, the comparison reports it
            entry["status"] = "error: %s" % e
            entry["results"] = None
        results.append(entry)
        print(f"{i + 1}/{len(lines)} {entry['status']} "
              f"{'-' if entry['results'] is None else len(entry['results'])}", flush=True)
        time.sleep(2)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=1)


def key(r):
    return f"{r['type']}:{r['url']}"


def compare(before, after):
    with open(before, encoding="utf-8") as f:
        b = json.load(f)
    with open(after, encoding="utf-8") as f:
        a = json.load(f)
    assert [x["q"] for x in b] == [x["q"] for x in a], "different query lists"
    problems = gained_total = checked = 0
    for qb, qa in zip(b, a):
        if qb["results"] is None or qa["results"] is None:
            print("  ERROR in request:", qb["q"], qb["status"], qa["status"])
            problems += 1
            continue
        before_items = {key(r): r for r in qb["results"]}
        after_items = {key(r): r for r in qa["results"]}
        required = before_items
        if len(qa["results"]) >= PAGE:
            oldest = min(r["date"] for r in qa["results"])
            required = {k: r for k, r in before_items.items() if r["date"] > oldest}
        lost = [k for k in required if k not in after_items]
        changed = [k for k in required if k in after_items and
                   (after_items[k]["text"] != required[k]["text"] or after_items[k]["title"] != required[k]["title"])]
        gained_total += len(set(after_items) - set(before_items))
        checked += len(required)
        if lost or changed:
            problems += 1
            print("  PROBLEM:", qb["q"].replace("\t", " | "), "lost", lost[:3], "changed", changed[:3])
    print(f"queries={len(b)} withProblems={problems} resultsChecked={checked} newResults={gained_total}")
    print("RESULT:", "OK" if problems == 0 else "PROBLEMS")
    return 0 if problems == 0 else 1


if __name__ == "__main__":
    if sys.argv[1] == "fetch":
        fetch(*sys.argv[2:])
    elif sys.argv[1] == "compare":
        sys.exit(compare(sys.argv[2], sys.argv[3]))
    else:
        raise SystemExit(__doc__)
