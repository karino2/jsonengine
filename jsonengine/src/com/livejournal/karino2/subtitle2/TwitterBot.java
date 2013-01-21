package com.livejournal.karino2.subtitle2;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;
import twitter4j.internal.logging.Logger;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.datastore.Query.CompositeFilterOperator;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.jsonengine.common.JEAccessDeniedException;
import com.jsonengine.common.JEConflictException;
import com.jsonengine.model.JEDoc;

public class TwitterBot {
    SubtitleServer server = new SubtitleServer();
    
    String account = "bot1";
    Pattern textIdPat;
    Pattern replyHeaderPat;
    
    Twitter twitter;
    public TwitterBot(Twitter twit) {
        twitter = twit;
        textIdPat = Pattern.compile("^([0-9][0-9]*):");
        replyHeaderPat = Pattern.compile("^@[0-9a-zA-Z]* (.*)$");
    }
    
    private static final Logger log = Logger.getLogger(TwitterBot.class);
    
    public JEDoc getFirstSrt() throws JEAccessDeniedException {
        List<JEDoc> srtList = server.getRawSrts();
        if(srtList.isEmpty())
        {
            log.debug("srt is empty");
            return null;
        }
        return srtList.get(0);        
    }
    
    private String srtId = null;
    
    public String getSrtId() throws JEAccessDeniedException {
        if(srtId == null) {
            JEDoc srt = getFirstSrt();
            if(srt == null)
                return null;            
            srtId = srt.getDocId();
        }
        return srtId;
    }
    
    public AreaMap getFirstAreaMap() throws JEAccessDeniedException {
        String srtId = getSrtId();
        if(srtId == null)
            return null;
        return server.getAreaMap(srtId);
    }
    
    AreaMap areaMap = null;
    public AreaMap getAreaMap() throws JEAccessDeniedException {
        if(areaMap == null) {
            areaMap = getFirstAreaMap();
        }
        return areaMap;
    }
    
    public void freeMyArea() throws JEConflictException, JEAccessDeniedException {
        AreaMap areaMap = getAreaMap();
        
        int myArea = areaMap.findMyArea(account, -1);
        if(myArea == -1) {
            return;
        }
        
        areaMap.freeArea(myArea);
        server.updateAreaMap(areaMap);
    }

    int areaIndex = -1;
    public boolean bookArea() throws JEAccessDeniedException, JEConflictException {
        freeMyArea();
        AreaMap areaMap = getAreaMap();
        areaIndex = areaMap.findEmptyArea(-1, account);
        if(areaIndex == -1)
        {
            log.debug("area is full");
            return false;
        }
        
        areaMap.assignArea(areaIndex, account);
        server.updateAreaMap(areaMap);
        return true;
    }

    public void doneArea(int areaIndex) throws JEAccessDeniedException, JEConflictException {
        AreaMap areaMap = getAreaMap();
        areaMap.doneArea(areaIndex);
        server.updateAreaMap(areaMap);        
    }

    // set to empty, but for normal case, it is for free booked area.
    public void freeArea(int areaIndex) throws JEAccessDeniedException, JEConflictException {
        AreaMap areaMap = getAreaMap();
        areaMap.freeArea(areaIndex);
        server.updateAreaMap(areaMap);        
    }
    
    public void submitTarget(int textId, String target) throws JEAccessDeniedException, JEConflictException {
        Text text = getTextById(getSrtId(), textId);
        text.putTarget(target);
        server.updateText(text);
    }
    
    private Text getTextById(String srtId2, int textId) throws JEAccessDeniedException {
        List<JEDoc> orderTexts = server.getRawTexts(srtId2);
        for(JEDoc jeDoc : orderTexts) {
            Text txt = new Text(jeDoc);
            if(txt.getIndex() == textId)
                return txt;
        }
        return null;
    }

    public Range getAreaRange() throws JEAccessDeniedException {
        return getAreaMap().getRange(areaIndex);
    }

    public List<Text> getAreaTextsWithHeaderFooter() throws JEAccessDeniedException {
        List<JEDoc> orderdTexts = server.getRawTexts(getSrtId());
        AreaMap areaMap = getAreaMap();
        return areaMap.getAreaTextWithHeaderFooter(orderdTexts, areaIndex);
    }
    

    // for checkMentions
    
    public void checkMentions() {
        DatastoreService service = DatastoreServiceFactory.getDatastoreService();
        try {
            ResponseList<Status> mentions = twitter.getMentionsTimeline();
            List<Status> notYetRepliedMentions = filterAlreadyReplied(service, mentions);
            
            for(Status mention : notYetRepliedMentions) {
                if(isReply(mention)) {
                    if(commitReply(service, mention)) {
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
    
    
    private boolean commitReply(DatastoreService service, Status mention) throws TwitterException, JEAccessDeniedException, JEConflictException {
        Status original = getOriginal(mention);
        int id = getTextId(original);
        if(id == -1)
            return false;
 
        Transaction transaction = service.beginTransaction();
        try {
            String message = chopMention(mention.getText());
            submitTarget(id, message);
            storeToDB(service, transaction, mention);
            
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
    
    HashMap<Long, Status> writtenStatus = new HashMap<Long, Status>();
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
            Status mention) throws JEAccessDeniedException {
        Entity post = new Entity("Post");
        post.setProperty("postUser", mention.getUser().getScreenName());
        post.setProperty("postText", mention.getText());
        post.setProperty("srtId", getSrtId());
        post.setProperty("postDate", new Date());
        service.put(transaction, post);
    }
    
    
    private List<Status> filterAlreadyReplied(DatastoreService service, ResponseList<Status> mentions) throws JEAccessDeniedException {
        ArrayList<Status> filteredVals = new ArrayList<Status>();
        for(Status reply : mentions) {
            Query query = new Query("Post");
            query.setFilter(
                CompositeFilterOperator.and(
                    FilterOperator.EQUAL.of("postUser", reply.getUser().getScreenName()),
                    FilterOperator.EQUAL.of("postText", reply.getText()),
                    FilterOperator.EQUAL.of("srtId", getSrtId())
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
    
    
    // tweets
    private void updateStatus(String body) throws TwitterException {
        Status stats = twitter.updateStatus(body);
        writtenStatus.put(stats.getId(), stats);
    }
    public void tweets() {
        try {
            if(!bookArea()) 
                return;
            
            List<Text> texts = getAreaTextsWithHeaderFooter();
            Range range = getAreaRange();
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
    
    
    // twitter instance creation.
    
    public static Twitter createTwitterInstance() {
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
        
        TwitterFactory factory = new TwitterFactory(cb.build());
        return factory.getInstance();
    }

}
