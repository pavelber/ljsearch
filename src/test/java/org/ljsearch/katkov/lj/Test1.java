package org.ljsearch.katkov.lj;

import org.ljsearch.katkov.lj.comments.Comment;
import org.ljsearch.katkov.lj.http.HTTPClientImpl;
import org.ljsearch.katkov.lj.http.UserProfile;
import org.ljsearch.katkov.lj.xmlrpc.arguments.GetEventsArgument;
import org.ljsearch.katkov.lj.xmlrpc.results.BlogEntry;

import java.util.Calendar;

public class Test1 {
    public static void main1(String[] args) {
        HTTPClientImpl client = new HTTPClientImpl();
        UserProfile userProfiles = client.getUserProfiles("javax_slr", 1000);
        System.out.println("userProfiles = " + userProfiles);
    }

    public static void main2(String[] args) {
        ConvenientClient ljConvenientClient = ClientsFactory.getLJConvenientClient();
        ljConvenientClient.login("javax_slr", "URAFesA47p", 10000);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, 2006);
        calendar.set(Calendar.MONTH, Calendar.OCTOBER);
        calendar.set(Calendar.DAY_OF_MONTH, 2);
        BlogEntry[] blogEntriesBefore = ljConvenientClient.getBlogEntriesBefore(calendar.getTime(), "tourism_il", Integer.MAX_VALUE);
        for (BlogEntry b : blogEntriesBefore) {
            System.out.println(b.getBody());
            Comment[] commentsOn = ljConvenientClient.getCommentsOn(b.getItemid(), Integer.MAX_VALUE);
            for (Comment comment : commentsOn) {
                System.out.println("\t" + comment.getBody());
            }
        }
    }

    public static void main(String[] args) {
        XMLRPCClient ljClient = new XMLRPCClientImpl();
        GetEventsArgument argument = new GetEventsArgument();
        argument.setUsername("javax_slr");
        argument.setHpassword("URAFesA47p");
        argument.setSelecttype(GetEventsArgument.Type.LASTN);
        argument.setHowmany(10);
        argument.setUsejournal("tourism_il");
        BlogEntry[] syncResult = ljClient.getevents(argument, 0);
        System.out.println("syncResult = " + syncResult);
    }
}
