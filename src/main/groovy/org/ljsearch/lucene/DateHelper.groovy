package org.ljsearch.lucene

import org.apache.lucene.document.DateTools


class DateHelper {
    static String toString(Date d){
        return DateTools.timeToString(d.getTime(), DateTools.Resolution.MINUTE)
    }
}
