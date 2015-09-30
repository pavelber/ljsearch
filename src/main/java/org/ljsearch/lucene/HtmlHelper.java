package org.ljsearch.lucene;


import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

final class HtmlHelper {
    public static Collection<String> extractLinks(String html, String seed) {

        Document document = Jsoup.parse(html, seed);
        Set<String> linksSet = new HashSet<String>();
        for (Element link : document.select("a[href]")) {
            String strLink = link.attr("abs:href").trim().toLowerCase();
            if (!strLink.isEmpty())
                linksSet.add(strLink);
        }

        return Collections.unmodifiableCollection(linksSet);
    }

    public static String download(String link) throws IOException,
            InterruptedException {

        IOException ioe;
        int retry = 5;

        do {
            try {

				/* Crawling a real web site should have politeness > 5s */

                Thread.sleep(1);
                Document bDoc = Jsoup.connect(link).userAgent("Mozilla")
                        .timeout(30000).get();
                return bDoc.html();
            } catch (IOException ex) {
                ioe = ex;
            }
        } while (--retry > 0);

        throw ioe;
    }

    public static String extractTitle(String html) {
        Document doc = Jsoup.parse(html);
        Elements elements = doc.select("table table div table font");
        if (null != elements && !elements.isEmpty())
            return elements.first().text();
        return null;
    }

    public static String extractContent(String html) {
        Document doc = Jsoup.parse(html);
        Elements elements = doc.select("table table div table div");
        if (null != elements && !elements.isEmpty())
            return elements.first().text();
        return doc.select("table table div table").first().text();
    }
}