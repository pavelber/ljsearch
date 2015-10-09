package org.ljsearch.lucene

import org.apache.lucene.document.DateTools

/**
 * Created by Pavel on 10/8/2015.
 */
class DateHelper {
    static String toString(Date d){
        return DateTools.timeToString(d.getTime(), DateTools.Resolution.MINUTE)
    }
}
