package com.livejournal.karino2.subtitle2;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.CompositeFilterOperator;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.datastore.Transaction;
import com.jsonengine.common.JEAccessDeniedException;
import com.jsonengine.common.JEConflictException;
import com.jsonengine.model.JEDoc;
import com.jsonengine.service.query.QueryRequest;
import com.jsonengine.service.query.QueryService;

import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;
import twitter4j.internal.logging.Logger;

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
 
    private static final Logger log = Logger.getLogger(TweetSrtServlet.class);
    
    
    private void tweets() {
        TwitterBot bot1 = new TwitterBot(twitter);
        bot1.tweets();        
    }
    
    public void checkMentions() {
        TwitterBot bot1 = new TwitterBot(twitter);
        bot1.checkMentions();
        
    }







    public void service(ServletRequest req, ServletResponse resp)
            throws ServletException, IOException {
        HttpServletRequest httpReq = (HttpServletRequest) req;
        if(httpReq.getQueryString().contains("tweets")) {            
            tweets();            
        }else {
            checkMentions();
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
