package org.ljsearch.katkov.lj;

import junit.framework.TestCase;

import java.util.Arrays;

import org.ljsearch.katkov.lj.xmlrpc.arguments.*;
import org.ljsearch.katkov.lj.xmlrpc.results.*;

/**
 * That class is not real UnitTest calss as there are no assert statemnet
 * It mere serves as a test client
 */
public class XMLRPCClientImplTest extends TestCase {
    XMLRPCClient ljClient;
    private static final String LOGIN = "ljapi";
    private static final String PASSWORD = "secret1";


    protected void setUp() throws Exception {
        super.setUp();
        ljClient = new XMLRPCClientImpl();
        System.getProperties().put("proxySet", "true");
        System.getProperties().put("proxyPort", "8888");
        System.getProperties().put("proxyHost", "localhost");

    }

    public void testCheckfriends() throws Exception {
        CheckFriendsArgument argument = new CheckFriendsArgument();
        argument.setUsername(LOGIN);
        argument.setHpassword(PASSWORD);
        FriendCheckResult checkResult = ljClient.checkfriends(argument, 0);
        System.out.println("checkResult = " + checkResult);
    }

    public void testConsolecommand() throws Exception {
        ConsoleCommandArgument argument = new ConsoleCommandArgument();
        argument.setUsername(LOGIN);
        argument.setHpassword(PASSWORD);
        argument.setCommands(new String[]{"help"});
        ConsoleCommandResult result = ljClient.consolecommand(argument, 0);
        System.out.println("result = " + result);

    }

    public void testEditevent() throws Exception {
        EditEventArgument argument = new EditEventArgument();
        argument.setUsername(LOGIN);
        argument.setHpassword(PASSWORD);
        argument.setEvent("Lorem Ipsum");
        argument.setSubject("Sbj:Lorem Ipsum");
        argument.setItemId(1);
        int result = ljClient.editevent(argument, 0);
        System.out.println("result = " + result);
    }

    public void testGetEvents() throws Exception {
        GetEventsArgument argument = new GetEventsArgument();
        argument.setUsername(LOGIN);
        argument.setHpassword(PASSWORD);
        argument.setSelecttype(GetEventsArgument.Type.LASTN);
        BlogEntry[] list = ljClient.getevents(argument, 0);
        System.out.println("list = " + Arrays.asList(list));
    }

    public void testLogin() throws Exception {
        LoginArgument argument = new LoginArgument();
        argument.setUsername(LOGIN);
        argument.setHpassword(PASSWORD);
        UserData userData = ljClient.login(argument, 0);
        System.out.println("userData = " + userData);
    }

    public void testGetdaycounts() throws Exception {
        GetDayCountsArgument argument = new GetDayCountsArgument();
        argument.setUsername(LOGIN);
        argument.setHpassword(PASSWORD);
        DayCount[] list = ljClient.getdaycounts(argument, 0);
        System.out.println("list = " + Arrays.asList(list));

    }

    public void testGetfriends() throws Exception {
        GetFriendsArgument argument = new GetFriendsArgument();
        argument.setUsername(LOGIN);
        argument.setHpassword(PASSWORD);
        argument.setIncludeFriendOf(true);
        argument.setIncludeGroups(true);
        FriendsResult friendsResult = ljClient.getfriends(argument, 0);
        System.out.println("friendsResult = " + friendsResult);
    }

    public void testGetfriendgroups() throws Exception {
        BaseArgument argument = new BaseArgument();
        argument.setUsername(LOGIN);
        argument.setHpassword(PASSWORD);
        FriendGroup[] friendGroups = ljClient.getfriendgroups(argument, 0);
        System.out.println("friendGroups = " + Arrays.asList(friendGroups));

    }

    public void testPostevent() throws Exception {
        PostEventArgument argument = new PostEventArgument();
        argument.setUsername(LOGIN);
        argument.setHpassword(PASSWORD);
        argument.setEvent("Lorem Ipsum  " + System.currentTimeMillis());
        argument.setSubject("Sbj:Lorem Ipsum");
        argument.setYear(2006);
        argument.setMon(1);
        argument.setDay(2);
        argument.setHour(0);
        argument.setMin(0);
        PostResult postResult = ljClient.postevent(argument, 0);
        System.out.println("postResult = " + postResult);

    }

    public void testSessiongenerate() throws Exception {
        SessionGenerateArgument argument = new SessionGenerateArgument();
        argument.setUsername(LOGIN);
        argument.setHpassword(PASSWORD);
        String session = ljClient.sessiongenerate(argument, 0);
        System.out.println("session = " + session);
    }

    public void testSessionexpire() throws Exception {
        SessionExpireArgument argument = new SessionExpireArgument();
        argument.setUsername(LOGIN);
        argument.setHpassword(PASSWORD);
        argument.setExpire(new String[]{"dde"});
        ljClient.sessionexpire(argument, 0);
    }

    public void testSyncitems() throws Exception {
        SyncItemsArgument argument = new SyncItemsArgument();
        argument.setUsername(LOGIN);
        argument.setHpassword(PASSWORD);
        SyncResult syncResult = ljClient.syncitems(argument, 0);
        System.out.println("syncResult = " + syncResult);
    }

    public void testEditfriends() throws Exception {
        EditFriendsArgument argument = new EditFriendsArgument();
        argument.setUsername(LOGIN);
        argument.setHpassword(PASSWORD);
        argument.add(new EditFriendsArgument.NewFriend[]{new EditFriendsArgument.NewFriend("vasia", "", "", 0)});
        ljClient.editfriends(argument, 0);

        argument = new EditFriendsArgument();
        argument.setUsername(LOGIN);
        argument.setHpassword(PASSWORD);
        argument.delete(new String[]{"vasia"});
        ljClient.editfriends(argument, 0);
    }

    public void testFriendof() throws Exception {
        GetFriendOfArgument argument = new GetFriendOfArgument();
        argument.setUsername(LOGIN);
        argument.setHpassword(PASSWORD);
        Friend[] friends = ljClient.friendof(argument, 0);
        System.out.println("friends = " + Arrays.asList(friends));
    }

    public void testGetchallenge() throws Exception {
        Challenge challenge = ljClient.getchallenge(0);
        System.out.println("challenge = " + challenge);
    }

    public void testEditfriendgroups() throws Exception {
        EditFriendGroupsArgument argument = new EditFriendGroupsArgument();
        argument.setUsername(LOGIN);
        argument.setHpassword(PASSWORD);
        argument.set(new EditFriendGroupsArgument.Group[]{new EditFriendGroupsArgument.Group("allfriends", 0, true)});
        ljClient.editfriendgroups(argument, 0);
    }
}