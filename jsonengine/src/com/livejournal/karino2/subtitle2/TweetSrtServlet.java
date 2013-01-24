package com.livejournal.karino2.subtitle2;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import twitter4j.Twitter;
import twitter4j.TwitterException;

public class TweetSrtServlet implements Servlet {

    ServletConfig config;
    
    public void destroy() {
        config = null;
    }

    public ServletConfig getServletConfig() {
        return config;
    }

    public String getServletInfo() {
        // TODO Auto-generated method stub
        return null;
    }
    Twitter twitter;

    public void init(ServletConfig sc) throws ServletException {
        config = sc;
        twitter  = TwitterBot.createTwitterInstance();
    }
 


    public void service(ServletRequest req, ServletResponse resp)
            throws ServletException, IOException {
        HttpServletRequest httpReq = (HttpServletRequest) req;
        
        TwitterBot bot1 = new TwitterBot(twitter);

        String cmd = httpReq.getParameter("cmd");
        if(cmd.contains("tweets")) {     
            bot1.tweets();
        }else  if (cmd.contains("area")){
            bot1.tweetWholeArea();
        }else if (cmd.contains("text")) {
            // /cron/tweetsrt?cmd=text&textId=3
            int textId = Integer.parseInt(httpReq.getParameter("textId"));
            bot1.tweetOneText(textId);
        }else {
            bot1.checkMentions();
        }
                
        resp.setContentType("text/plain");
        PrintWriter writer = resp.getWriter();
        
        writer.print("done");
        writer.flush();
        writer.close();
        // testTweet(resp);
    }
    

    public void testTweet(ServletResponse resp) throws IOException {
        resp.setContentType("text/plain");
        PrintWriter writer = resp.getWriter();
        Date dt = new Date();
        try {
            twitter.updateStatus("from servelet: " + dt.toString());
        } catch (TwitterException e) {
            // writer.print("exception: " + e.getMessage());
            writer.print("exception!");
            e.printStackTrace();
        }        
        writer.print("done");
        writer.flush();
        writer.close();
    }

}
