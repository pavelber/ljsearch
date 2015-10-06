package org.ljsearch.lucene;


import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.xml.builders.BooleanQueryBuilder;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.codehaus.groovy.util.StringUtil;

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

    public static Query generate(String words, String journal, String poster) throws ParseException {
        Query content = generate(words);
        if (StringUtils.isEmpty(journal)&&StringUtils.isEmpty(poster)){
            return content;
        }

        BooleanQuery.Builder builder = new BooleanQuery.Builder().add(content, BooleanClause.Occur.MUST);
        if (!StringUtils.isEmpty(journal)){
           Query qf = new TermQuery(new Term(LuceneBinding.JOURNAL_FIELD, journal));
            builder = builder.add(qf, BooleanClause.Occur.MUST);
        }


        if (!StringUtils.isEmpty(poster)){
            Query qp = new TermQuery(new Term(LuceneBinding.POSTER_FIELD, poster));
            builder = builder.add(qp, BooleanClause.Occur.MUST);
        }


        return builder.build();

    }
}