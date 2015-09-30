/*
 * Copyright (c) 2006, Igor Katkov
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided 
 * that the following conditions are met:
 *
 *    * Redistributions of source code must retain the above copyright notice, this list of conditions 
 *       and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above copyright notice, this list of conditions 
 *       and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *    * The name of the author may not be used may not be used to endorse or 
 *       promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, 
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR 
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS 
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, 
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT 
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; 
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
 * THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.ljsearch.katkov.lj.http;


import junit.framework.TestCase;
import org.ljsearch.katkov.lj.HTTPClient;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HTTPClientImplTest extends TestCase {
    private static Logger logger1 = Logger.getLogger(HTTPClient.class.getName());
    private static Logger logger2 = Logger.getLogger(ProcessProfileCommand.class.getName());
    private static ConsoleHandler handler = new ConsoleHandler();


    public void testGetUserProfiles() throws Exception {
        handler.setLevel(Level.ALL);
        logger1.addHandler(handler);
        logger1.setLevel(Level.ALL);
        logger2.addHandler(handler);
        logger2.setLevel(Level.ALL);

        HTTPClientImpl client = new HTTPClientImpl();
        UserProfile userProfiles = client.getUserProfiles("katren", 1000);
        System.out.println("userProfiles = " + userProfiles);
        userProfiles = client.getUserProfiles("2004_vybory_ua", 1000);
        System.out.println("userProfiles = " + userProfiles);
//        System.out.println("Arrays.asList(userProfiles) = " + Arrays.asList(userProfiles));
//        userProfiles = client.getCommunityProfiles(new String[]{"2004_vybory_ua", "2004_vybory_ua", "2004_vybory_ua"}, 1000);
//        System.out.println("Arrays.asList(userProfiles) = " + Arrays.asList(userProfiles));
    }
}