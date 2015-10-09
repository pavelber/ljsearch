package org.ljsearch.lucene

import org.apache.lucene.store.Directory
import org.apache.lucene.store.RAMDirectory
import spock.lang.Specification


/**
 * Created by Pavel on 10/8/2015.
 */
class IndexerTest extends Specification {

    public static final String TITLE2 = "titte заголовок"
    public static final String TITLE1 = "сады"
    public static final String POTREBITEL_IL = "potrebitel_il"
    public static final String TOURISM_IL = "tourism_il"
    public static final String JAVAX_SLR = "javax_slr"
    public static final String ELCY_ = "elcy_"

    LuceneIndexer indexer = new LuceneIndexer()
    LuceneSearcher seacher = new LuceneSearcher()

    def setup() {
        Directory index = new RAMDirectory();
        indexer.init(index)

        indexer.add(TITLE2, "мыши включили apache", POTREBITEL_IL, JAVAX_SLR, "http://2", new Date(2010, 11, 1));
        indexer.add(TITLE1, "мышь гуляла в саду", TOURISM_IL, ELCY_, "http://1", new Date(2015, 10, 8));
        indexer.optimizeAndClose()

        seacher.init(index)

    }

    def "test empty"() {
        when:
        def res = seacher.search(null, null, null, null, null)
        then:
        res.size() == 0
    }

    def "test text"() {
        when:
        def res = seacher.search(null, null, "нету", null, null)
        then:
        res.size() == 0

        when:
        res = seacher.search(null, null, "мыши", null, null)
        then:
        res.size() == 2

         when:
        res = seacher.search(null, null, "мышь сад", null, null)
        then:
        res.size() == 1

        when:
        res = seacher.search(null, null, "гуляли", null, null)
        then:
        res.size() == 1
        res[0].title == TITLE1

        when:
        res = seacher.search(null, null, "гулял", null, null)
        then:
        res.size() == 1
        res[0].title == TITLE1

        when:
        res = seacher.search(null, null, "заголовок", null, null)
        then:
        res.size() == 1
        res[0].title == TITLE2

        when:
        res = seacher.search(null, null, "apache", null, null)
        then:
        res.size() == 1
        res[0].title == TITLE2

        when:
        res = seacher.search(null, null, "apaches", null, null)
        then:
        res.size() == 1
        res[0].title == TITLE2
     }

     def "test journal"(){
         when:
         def res = seacher.search(POTREBITEL_IL, null, "apaches", null, null)
         then:
         res.size() == 1
         res[0].title == TITLE2

         when:
         res = seacher.search(TOURISM_IL, null, "apaches", null, null)
         then:
         res.size() == 0
     }

    def "test poster"(){
        when:
        def res = seacher.search(POTREBITEL_IL, JAVAX_SLR, "apaches", null, null)
        then:
        res.size() == 1
        res[0].title == TITLE2

         when:
        res = seacher.search(POTREBITEL_IL, ELCY_, "apaches", null, null)
        then:
        res.size() == 0

        when:
        res = seacher.search(null, JAVAX_SLR, "apaches", null, null)
        then:
        res.size() == 1
        res[0].title == TITLE2
    }

   def "test dates"(){
        when:
        def res = seacher.search(POTREBITEL_IL, JAVAX_SLR, "apaches", new Date(2000,1,1), null)
        then:
        res.size() == 1
        res[0].title == TITLE2

         when:
        res = seacher.search(POTREBITEL_IL, JAVAX_SLR, "apaches", new Date(2000,1,1), new Date(2015,1,1))
        then:
        res.size() == 1
        res[0].title == TITLE2

       when:
       res = seacher.search(null, null, "мыши", new Date(2000,1,1), new Date(2016,1,1))
       then:
       res.size() == 2

       when:
       res = seacher.search(null, null, "мыши", new Date(2014,1,1), new Date(2016,1,1))
       then:
       res.size() == 1

       when:
       res = seacher.search(null, null, "мыши", new Date(2014,1,1), new Date(2015,1,1))
       then:
       res.size() == 0
    }


}