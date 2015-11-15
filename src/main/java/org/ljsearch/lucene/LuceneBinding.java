package org.ljsearch.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.util.Version;

import java.util.HashMap;
import java.util.Map;

public final class LuceneBinding {

    public static final String TITLE_FIELD = "title";
    public static final String CONTENT_FIELD = "content";
    public static final String JOURNAL_FIELD = "journal";
    public static final String POSTER_FIELD = "poster";
    public static final String URL_FIELD = "url";
    public static final String DATE_FIELD = "date";


    public static final String TYPE_FIELD = "contenttype";

    /* Russian */

    public static final String RUS_TITLE_FIELD = "rustitle";
    public static final String RUS_CONTENT_FIELD = "ruscontent";

    /* English */

    public static final String ENG_TITLE_FIELD = "engtitle";
    public static final String ENG_CONTENT_FIELD = "engcontent";


 /* Hebrew */

    public static final String HEB_TITLE_FIELD = "hebtitle";
    public static final String HEB_CONTENT_FIELD = "hebcontent";



    public static Analyzer getAnalyzer() {

        Map<String, Analyzer> analyzers =
                new HashMap<String, Analyzer>();
        analyzers.put(RUS_TITLE_FIELD, new RussianAnalyzer());
        analyzers.put(RUS_CONTENT_FIELD, new RussianAnalyzer());
        analyzers.put(ENG_TITLE_FIELD, new EnglishAnalyzer());
        analyzers.put(ENG_CONTENT_FIELD, new EnglishAnalyzer());

        return new PerFieldAnalyzerWrapper(new StandardAnalyzer(), analyzers);
    }
}