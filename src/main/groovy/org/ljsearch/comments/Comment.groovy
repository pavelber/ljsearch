package org.ljsearch.comments

import groovy.transform.Canonical

import java.text.DateFormat
import java.text.SimpleDateFormat

/**
 * Created by Pavel on 10/27/2015.
 */
@Canonical
class Comment {
    def static formats = [
            new SimpleDateFormat("MMMMMMM d yyyy, HH:mm:ss Z"),
            new SimpleDateFormat("d MMM, yyyy HH:mm (Z)", Locale.forLanguageTag("ru")),
            new SimpleDateFormat("yyyy-MM-dd hh:mm aa (Z)"),
            new SimpleDateFormat("d MMM, yyyy HH:mm:ss (Z)", Locale.forLanguageTag("ru")),
            new SimpleDateFormat("d MMM, yyyy HH:mm:ss", Locale.forLanguageTag("ru"))
    ]
    String link, text, user, subject
    Date date

    private static Date parseDate(String s) {
        for (DateFormat f : formats) {
            try {
                return f.parse(s)
            }
            catch (Exception e) {

            }
        }
        throw new RuntimeException("Upraseable")
    }

    void setDate(final String date) {
        this.date = parseDate(date)
    }


    @Override
    public String toString() {
        return "Comment{" +
                "link='" + link + '\'' +
                ", text='" + text + '\'' +
                ", user='" + user + '\'' +
                ", subject='" + subject + '\'' +
                ", date=" + date +
                '}';
    }
}
