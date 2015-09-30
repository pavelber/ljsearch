package org.ljsearch.katkov.lj;


import junit.framework.TestCase;
import org.ljsearch.katkov.lj.comments.Comment;
import org.ljsearch.katkov.lj.xmlrpc.results.BlogEntry;

import java.util.Calendar;
import java.util.Date;

public class ConvenientClientTest extends TestCase {
    ConvenientClient ljConvenientClient;
    private static final String LOGIN = "ljapi";
    private static final String PASSWORD = "secret1";
    private Calendar calendar;


    protected void setUp() throws Exception {
        System.getProperties().put("proxySet", "true");
        System.getProperties().put("proxyPort", "8888");
        System.getProperties().put("proxyHost", "localhost");
        super.setUp();
        ljConvenientClient = ClientsFactory.getLJConvenientClient();
        calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, 2006);
        calendar.set(Calendar.MONTH, Calendar.JANUARY);
        calendar.set(Calendar.DAY_OF_MONTH, 2);
        ljConvenientClient.login(LOGIN, PASSWORD, 0);
    }


    public void testGetBlogEntriesOn() throws Exception {
        BlogEntry[] blogEntriesOn = ljConvenientClient.getBlogEntriesOn(calendar.getTime(),null, 0);
        assertEquals(6, blogEntriesOn.length);
    }

    public void testGetBlogEntry() throws Exception {
        BlogEntry blogEntry = ljConvenientClient.getBlogEntry(3, 0);
        assertNotNull(blogEntry);
    }

    public void testGetBlogEntriesBefore() throws Exception {
        BlogEntry[] blogEntriesBefore = ljConvenientClient.getBlogEntriesBefore(new Date(),null, 0);
        assertEquals(6, blogEntriesBefore.length);
    }

    public void testGetMostRecentBlogEntries() throws Exception {
        BlogEntry[] blogEntries = ljConvenientClient.getMostRecentBlogEntries(3, 0);
        assertEquals(3, blogEntries.length);
    }

    public void testGetAllComments() throws Exception {
        Comment[] comments = ljConvenientClient.getAllComments(0);
        assertEquals(5, comments.length);
    }

    public void testGetCommentsOn() throws Exception {
        Comment[] comments = ljConvenientClient.getCommentsOn(4, 0);
        assertEquals(5, comments.length);
    }

    public void testUpdateBlogEntry() throws Exception {
        BlogEntry blogEntry = ljConvenientClient.getBlogEntry(3, 0);
        assertNotNull(blogEntry);
        ljConvenientClient.updateBlogEntry(blogEntry.getItemid(), blogEntry.getDate(), "11111111111", "2222222222222", 0);
    }

    public void testAddBlogEntry() throws Exception {
        ljConvenientClient.addBlogEntry(new Date(), "test " + System.currentTimeMillis() + "\n\u0420\u0423\u0421\u0421\u041a\u0418\u0415" + "", "some new subject", 0);
    }

    public void testAddFriend() throws Exception {
        ljConvenientClient.addFriend("katren", 0);
    }

    public void testRemoveFriends() throws Exception {
        ljConvenientClient.removeFriends("katren", 0);
    }
}