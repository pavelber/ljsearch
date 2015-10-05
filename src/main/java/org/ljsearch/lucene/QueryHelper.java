package org.ljsearch.lucene;


import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;

public final class QueryHelper {

    public static Query generate(String story) throws ParseException {
        QueryParser parser = new MultiFieldQueryParser(
                new String[]{
                        LuceneBinding.TITLE_FIELD,
                        LuceneBinding.CONTENT_FIELD,
                        /* Russian */
                        LuceneBinding.RUS_TITLE_FIELD,
                        LuceneBinding.RUS_CONTENT_FIELD,
                        /* English */
                        LuceneBinding.ENG_TITLE_FIELD,
                        LuceneBinding.ENG_CONTENT_FIELD},
                LuceneBinding.getAnalyzer());

        /* Operator OR is used by default */

        parser.setDefaultOperator(QueryParser.Operator.AND);

        return parser.parse(QueryParser.escape(story));
    }
}