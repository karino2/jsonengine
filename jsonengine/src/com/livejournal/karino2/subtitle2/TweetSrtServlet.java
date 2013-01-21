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

    Pattern textIdPat;
    Pattern replyHeaderPat;
    
    public TweetSrtServlet() {
        textIdPat = Pattern.compile("^([0-9][0-9]*):");
        replyHeaderPat = Pattern.compile("^@[0-9a-zA-Z]* (.*)$");
    }
    
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
    TwitterFactory factory;
    Twitter twitter;

    public void init(ServletConfig sc) throws ServletException {
        config = sc;
        ResourceBundle bundle = ResourceBundle.getBundle("com.livejournal.karino2.subtitle2.account");
        final String CONSUMER_KEY = (String) bundle.getObject("bot1.consumerkey");
        final String CONSUMER_SECRET = (String) bundle.getObject("bot1.consumersecret");
        final String ACCESS_TOKEN = (String) bundle.getObject("bot1.accesstoken");
        final String ACCESS_SECRET = (String) bundle.getObject("bot1.accesssecret");
        
        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb
        .setDebugEnabled(true)
        .setOAuthConsumerKey(CONSUMER_KEY)
        .setOAuthConsumerSecret(CONSUMER_SECRET)
        .setOAuthAccessToken(ACCESS_TOKEN)
        .setOAuthAccessTokenSecret(ACCESS_SECRET);
        
        factory = new TwitterFactory(cb.build());
        twitter  = factory.getInstance();
    }
 
    private static final Logger log = Logger.getLogger(TweetSrtServlet.class);
    
    HashMap<Long, Status> writtenStatus = new HashMap<Long, Status>();
    private void updateStatus(String body) throws TwitterException {
        Status stats = twitter.updateStatus(body);
        writtenStatus.put(stats.getId(), stats);
    }
    
    private void tweets() {
        TwitterBot bot1 = new TwitterBot();
        try {
            if(!bot1.bookArea()) 
                return;
            
            List<Text> texts = bot1.getAreaTextsWithHeaderFooter();
            Range range = bot1.getAreaRange();
            for(Text text : texts) {
                if(range.inside(text.getIndex()))
                    updateStatus(text.getFormatedOriginal());
                else
                    updateStatus(text.getOriginal());
                
                if(text.hasTarget())
                    updateStatus(text.getTarget());
            }
            // bot1.freeMyArea();
            // bot1.bookArea();
            // bot1.doneArea(12);
            // bot1.freeArea(12);
         } catch (JEAccessDeniedException e) {
             log.debug("access denied");
         } catch (JEConflictException e) {
             log.debug("conflict");
         } catch (TwitterException e) {
             log.debug("twitter exception");
        }
         
        
    }
    
    public void checkMentions() {
        TwitterBot bot1 = new TwitterBot();

        DatastoreService service = DatastoreServiceFactory.getDatastoreService();
        try {
            ResponseList<Status> mentions = twitter.getMentionsTimeline();
            List<Status> notYetRepliedMentions = filterAlreadyReplied(service, bot1.getSrtId(), mentions);
            
            for(Status mention : notYetRepliedMentions) {
                if(isReply(mention)) {
                    if(commitReply(service, bot1, mention)) {
                        twitter.retweetStatus(mention.getId());
                    }
                        
                }
            }
            
            
        } catch (TwitterException e) {
            log.debug("twitter exception");
        } catch (JEAccessDeniedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (JEConflictException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
    }

    
    private boolean commitReply(DatastoreService service, TwitterBot bot, Status mention) throws TwitterException, JEAccessDeniedException, JEConflictException {
        Status original = getOriginal(mention);
        int id = getTextId(original);
        if(id == -1)
            return false;
 
        Transaction transaction = service.beginTransaction();
        try {
            String message = chopMention(mention.getText());
            bot.submitTarget(id, message);
            storeToDB(service, transaction, bot.getSrtId(), mention);
            
            transaction.commit();
        } finally {
            if (transaction.isActive()) {
                transaction.rollback();
            }
        }
        return true;
        
    }

    private String chopMention(String text) {
        Matcher m = replyHeaderPat.matcher(text);
        if(!m.find())
            throw new RuntimeException("reply but no account name, is it possible?");
        return m.group(1);
    }

    private int getTextId(Status original) {
        Matcher m = textIdPat.matcher(original.getText());
        if(m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return -1;
    }

    private Status getOriginal(Status mention) throws TwitterException {
        if(writtenStatus.containsKey(mention.getInReplyToStatusId())) {
            return writtenStatus.get(mention.getInReplyToStatusId());
        }
        // may be not mine? dont care.
        return twitter.showStatus(mention.getInReplyToStatusId());
    }

    private boolean isReply(Status mention) {
        return mention.getInReplyToStatusId() != -1L;
    }

    private void storeToDB(DatastoreService service, Transaction transaction,
            String srtId, Status mention) {
        Entity post = new Entity("Post");
        post.setProperty("postUser", mention.getUser().getScreenName());
        post.setProperty("postText", mention.getText());
        post.setProperty("srtId", srtId);
        post.setProperty("postDate", new Date());
        service.put(transaction, post);
    }

    private List<Status> filterAlreadyReplied(DatastoreService service,  String srtId,
            ResponseList<Status> mentions) {
        ArrayList<Status> filteredVals = new ArrayList<Status>();
        for(Status reply : mentions) {
            Query query = new Query("Post");
            query.setFilter(
                CompositeFilterOperator.and(
                    FilterOperator.EQUAL.of("postUser", reply.getUser().getScreenName()),
                    FilterOperator.EQUAL.of("postText", reply.getText()),
                    FilterOperator.EQUAL.of("srtId", srtId)
                    ));
            query.addSort("postDate", SortDirection.DESCENDING);
            
            PreparedQuery pq = service.prepare(query);
            FetchOptions limit = FetchOptions.Builder.withOffset(0).limit(1);
            List<Entity> results = pq.asList(limit);

            if(results.isEmpty()) {
                filteredVals.add(reply);
            }
            
        }
        return filteredVals;
    }


    public void service(ServletRequest req, ServletResponse resp)
            throws ServletException, IOException {
        
        checkMentions();
        
        // tweets();
                
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
