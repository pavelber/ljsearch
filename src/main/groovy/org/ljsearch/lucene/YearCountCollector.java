package org.ljsearch.lucene;

import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.search.SimpleCollector;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Counts matching documents per year of the date field.
 *
 * The date doc values ("yyyyMMddHHmm") are sorted, so in every segment the
 * ordinals of one year form a contiguous range. For each segment we look up
 * the first ordinal of every year once (a few dozen lookupTerm calls), and
 * per hit only read the ordinal and binary-search those boundaries: no term
 * bytes are read and nothing is allocated per document. It runs in the same
 * pass as the result collector, so the query is executed only once.
 */
public class YearCountCollector extends SimpleCollector {

    private final int[] counts = new int[YEARS];
    private static final int FIRST_YEAR = 1990;
    private static final int YEARS = 200;

    private SortedDocValues dates;
    /* starts[i] = first ordinal of year years[i] within the current segment */
    private int[] starts;
    private int[] years;

    @Override
    protected void doSetNextReader(LeafReaderContext context) throws IOException {
        dates = DocValues.getSorted(context.reader(), LuceneBinding.DATE_FIELD);
        int n = dates.getValueCount();
        if (n == 0) {
            starts = new int[0];
            years = new int[0];
            return;
        }
        int min = year(dates.lookupOrd(0));
        int max = year(dates.lookupOrd(n - 1));
        if (min < 0 || max < min) {   // unexpected date format: count nothing in this segment
            starts = new int[0];
            years = new int[0];
            return;
        }
        starts = new int[max - min + 1];
        years = new int[max - min + 1];
        for (int y = min; y <= max; y++) {
            int i = y - min;
            years[i] = y;
            if (y == min) {
                starts[i] = 0;
            } else {
                int ord = dates.lookupTerm(new BytesRef(Integer.toString(y)));
                starts[i] = ord >= 0 ? ord : -ord - 1;   // insertion point = first ordinal >= "yyyy"
            }
        }
    }

    @Override
    public void collect(int doc) {
        int ord = dates.getOrd(doc);
        if (ord < 0 || starts.length == 0) {
            return;
        }
        int lo = 0, hi = starts.length - 1;
        while (lo < hi) {                     // last i with starts[i] <= ord
            int mid = (lo + hi + 1) >>> 1;
            if (starts[mid] <= ord) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        int idx = years[lo] - FIRST_YEAR;
        if (idx >= 0 && idx < YEARS) {
            counts[idx]++;
        }
    }

    @Override
    public boolean needsScores() {
        return false;
    }

    /** year -> number of matching documents, only years with hits */
    public Map<Integer, Integer> getCounts() {
        Map<Integer, Integer> m = new TreeMap<>();
        for (int i = 0; i < YEARS; i++) {
            if (counts[i] > 0) {
                m.put(FIRST_YEAR + i, counts[i]);
            }
        }
        return m;
    }

    private static int year(BytesRef b) {
        if (b.length < 4) {
            return -1;
        }
        int y = 0;
        for (int i = 0; i < 4; i++) {
            int c = b.bytes[b.offset + i] - '0';
            if (c < 0 || c > 9) {
                return -1;
            }
            y = y * 10 + c;
        }
        return y;
    }
}
