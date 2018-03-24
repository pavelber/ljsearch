package org.ljsearch

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId

/**
 * Created by Pavel on 11/17/2015.
 */
class DateUtils {
    def static formats = [
            new SimpleDateFormat("MMMMMMMMM d yyyy, HH:mm:ss Z"),
            new SimpleDateFormat("MMMMMMMMM dd yyyy, HH:mm:ss Z"),
            new SimpleDateFormat("d MMM, yyyy HH:mm (Z)", Locale.forLanguageTag("ru")),
            new SimpleDateFormat("yyyy-MM-dd hh:mm aa (Z)"),
            new SimpleDateFormat("d-MMM-yyyy hh:mm aa", Locale.forLanguageTag("ru")),
            new SimpleDateFormat("dd-MMM-yyyy hh:mm aa (Z)"),
            new SimpleDateFormat("MMM. dd, yyyy hh:mm aa (Z)"),
            new SimpleDateFormat("d MMM, yyyy HH:mm:ss (Z)", Locale.forLanguageTag("ru")),
            new SimpleDateFormat("MMMMMMMMM dd, yyyy hh:mm aa (Z)"),
            new SimpleDateFormat("MMMMMMMMM, dd, yyyy hh:mm (Z)",Locale.forLanguageTag("ru")),
            new SimpleDateFormat("d MMM, yyyy HH:mm:ss", Locale.forLanguageTag("ru"))
    ]

    public static final LocalDate START_DATE = LocalDate.of(2001, 1, 1)
    public static final int DELAY = 20 * 60 * 1000

    static Date parseDate(String s) {
        for (DateFormat f : formats) {
            try {
                return f.parse(s);
            }
            catch (Exception e) {

            }
        }
        for (DateFormat f : formats) {
            try {
                return f.parse(s.replaceAll("сент","сен").
                        replaceAll("st|nd|rd|th", "").replaceAll("Augu","August"))
            }
            catch (Exception e) {

            }
        }

        throw new RuntimeException("Upraseable:$s")
    }


    static Date toDate(LocalDate maxDate) {
     return Date.from(maxDate.atStartOfDay(ZoneId.systemDefault()).toInstant())
 }

    static LocalDate fromDate(Date input) {
     input.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
 }
}
