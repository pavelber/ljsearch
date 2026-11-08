"""Compare the outputs of IndexTool for an old and a rebuilt index.

usage: python compare.py OLD_PREFIX NEW_PREFIX
  expects OLD_PREFIX.dump, .dump.duplicates, .dump.journals, .search and the same for NEW_PREFIX

Exit code 0 only when nothing was lost:
  * every unique item (type:journal:url) of the old index is in the new one with the
    same journal, type, date and stored-field hash of its last copy;
  * no item appeared that was not in the old index;
  * for every control query the new index finds the same items, except older
    versions of edited items (only the last version is kept), and nothing new;
  * every item of an old result page is still found by the new index.
"""
import sys


def strip_eol(line):
    """IndexTool runs on Windows too, where println ends lines with \\r\\n."""
    if line.endswith("\r\n"):
        return line[:-2]
    return line[:-1] if line.endswith("\n") else line


def read_dump(path):
    items = {}
    # newline="\n": post texts contain bare \r, which must not end a line
    with open(path, encoding="utf-8", newline="\n") as f:
        for line in f:
            key, rest = strip_eol(line).split("\t", 1)
            items[key] = rest
    return items


def read_lines(path):
    with open(path, encoding="utf-8", newline="\n") as f:
        return [strip_eol(l) for l in f]


def read_search(path):
    result = []
    lines = read_lines(path)
    for i in range(0, len(lines), 3):
        q = lines[i].split("\t", 1)[1]
        all_ids = lines[i + 1].split("\t")
        ids = set(all_ids[2].split(" ")) if len(all_ids) > 2 and all_ids[2] else set()
        page = lines[i + 2].split("\t", 2)
        entries = [e for e in page[2].split("\u0002") if e] if len(page) > 2 else []
        result.append((q, ids, entries))
    return result


def main(old, new):
    ok = True
    o, n = read_dump(old + ".dump"), read_dump(new + ".dump")
    missing = sorted(set(o) - set(n))
    extra = sorted(set(n) - set(o))
    changed = sorted(k for k in set(o) & set(n) if o[k] != n[k])
    print(f"items: old={len(o)} new={len(n)} missing={len(missing)} extra={len(extra)} changed={len(changed)}")
    for k in (missing + extra + changed)[:20]:
        print("  ", k, o.get(k), "->", n.get(k))
    ok &= not missing and not extra and not changed

    oj, nj = read_lines(old + ".dump.journals"), read_lines(new + ".dump.journals")
    print(f"per-journal counts identical: {oj == nj} ({len(oj)} journals)")
    ok &= oj == nj

    # same journal, different content: an edited post/comment; the rebuilt index keeps
    # only the last version, so words found only in the older version may stop matching it
    edited = {l.split("	")[0] for l in read_lines(old + ".dump.duplicates") if l.endswith("DIFFERENT")}
    os_, ns = read_search(old + ".search"), read_search(new + ".search")
    assert [q for q, _, _ in os_] == [q for q, _, _ in ns], "different query lists"
    bad_all = bad_page = lost_edited = lost_beyond_page = saturated = 0
    for (q, oi, op), (_, ni, np_) in zip(os_, ns):
        lost, gained = oi - ni, ni - oi
        lost_edited += len(lost & edited)
        unexplained = (lost - edited) | gained
        if unexplained:
            bad_all += 1
            if bad_all <= 10:
                print("  ALL differs:", q, "lost", sorted(lost - edited)[:5], "gained", sorted(gained)[:5])
        if len(np_) >= 400:
            saturated += 1
        # page entries: "type:url|date|title|citation"; an entry that left the page is
        # fine when the item is still found (it moved past the 400 cut-off or its date changed)
        found = {i.split(":", 1)[0] + ":" + i.split(":", 2)[2] for i in ni}
        new_page = set(np_)
        lost_page = [e for e in op if e not in new_page]
        journal = q.split("\t", 1)[0]

        def edited_item(entry):
            t, url = entry.split("|", 1)[0].split(":", 1)
            return f"{t}:{journal}:{url}" in lost & edited

        not_found = [e for e in lost_page if e.split("|", 1)[0] not in found]
        really_lost = [e for e in not_found if not edited_item(e)]
        lost_beyond_page += len(lost_page) - len(not_found)
        if really_lost:
            bad_page += 1
            if bad_page <= 10:
                print("  PAGE lost and not found:", q, really_lost[:3])
    print(f"queries={len(os_)} unexplainedMatchDifferences={bad_all} lostOlderVersionsOfEdited={lost_edited} "
          f"pagesWithItemsNotFound={bad_page} pageEntriesStillFound={lost_beyond_page} fullPages(400)={saturated}")
    ok &= bad_all == 0 and bad_page == 0
    print("RESULT:", "OK" if ok else "PROBLEMS")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
