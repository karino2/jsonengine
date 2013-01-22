package com.livejournal.karino2.subtitle2;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.StatusUpdate;
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
import com.sun.tools.corba.se.idl.InvalidArgument;

public class TwitterBot {
    SubtitleServer server = new SubtitleServer();
    
    String accountPrefix = "@SubCourBot";
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
            log.info("srt is empty");
            return null;
        }
        return srtList.get(0);        
    }
    
    private String srtId = null;
    
    public String getSrtUrl() throws JEAccessDeniedException {
        JEDoc srt = getFirstSrt();
        if(srt == null)
            return null;
        JEDocWrapper wrapper = new JEDocWrapper(srt);
        return (String)wrapper.get("srtUrl");
    }
    
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
    public boolean bookAreaRandom() throws JEAccessDeniedException, JEConflictException {
        freeMyArea();
        return bookArea();
    }

    public boolean bookArea() throws JEAccessDeniedException,
            JEConflictException {
        AreaMap areaMap = getAreaMap();
        areaIndex = areaMap.findEmptyArea(-1, account);
        while(areaIndex != -1) {
          
            if(isEmptyTextExist(areaMap, areaIndex)) {
                areaMap.assignArea(areaIndex, account);
                server.updateAreaMap(areaMap);
                return true;
            } else {
                areaMap.doneArea(areaIndex);
            }
                        
            areaIndex = areaMap.findEmptyArea(-1, account);
        }
        
        log.info("area is full");
        return false;
            
        
    }

    private boolean isEmptyTextExist(AreaMap map, int areaIndex2) throws JEAccessDeniedException {
        List<JEDoc> texts = server.getRawTexts(getSrtId());
        Range range = map.getRange(areaIndex2);
        for(JEDoc doc : texts) {
            Text txt = new Text(doc);
            if(range.inside(txt.getIndex()) && !txt.hasTarget()) {
                return true;
            }
        }
        return false;
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
    
    public boolean submitTarget(int textId, String target) throws JEAccessDeniedException, JEConflictException {
        Text text = getTextById(getSrtId(), textId);
        if(text == null)
            return false;
        text.putTarget(target);
        server.updateText(text);
        return true;
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
        // log.info("check mention");
        DatastoreService service = DatastoreServiceFactory.getDatastoreService();
        try {
            ResponseList<Status> mentions = twitter.getMentionsTimeline();
            List<Status> notYetRepliedMentions = filterAlreadyReplied(service, mentions);
            
            // log.info("mentions size: " + mentions.size());
            // log.info("not yet reply num: " + notYetRepliedMentions.size());
            for(Status mention : notYetRepliedMentions) {
                if(isReply(mention)) {
                   //  log.info("is reply");
                    if(commitReply(service, mention)) {
                       //  log.info("commit success");
                        twitter.retweetStatus(mention.getId());
                    }
                        
                } else {
                    // log.info("handle no reply");
                    handleNonReplyMention(service, mention);
                }
            }
            
            
        } catch (TwitterException e) {
            log.info("twitter exception" + e.getMessage());
        } catch (JEAccessDeniedException e) {
            log.info("access denied exception");
        } catch (JEConflictException e) {
            log.info("conflict exception");
        }

    }
    
    
    private void handleNonReplyMention(DatastoreService service, Status mention) throws JEAccessDeniedException {
        Transaction transaction = service.beginTransaction();
        try {
            try
            {
                if(mention.getText().startsWith(accountPrefix)){
                    // log.info("call reply info");
                    replyInfo(mention);
                }
            } catch (TwitterException e) {
                log.info("twitter exception: " + e.getMessage());
            } finally {
                // if twitter exception occur, optimal behaviour is store it as handled.
                // log.info("store to DB");
                storeToDB(service, transaction, mention);
                transaction.commit();
            }
        } finally {
            if (transaction.isActive()) {
                transaction.rollback();
            }
        }
        
    }

    public void replyInfo(Status mention) throws JEAccessDeniedException, TwitterException {
        String msg = mention.getText();
        if(msg.contains("動画") ||
                msg.contains("URL") ||
                msg.contains("url")) {
            replyVideoUrl(mention);
            return;            
        }
        if(msg.contains("stat") ||
                msg.contains("統計")) {
            replyStats(mention);
            return;
        }
        replyHelp(mention);
    }

    private void replyHelp(Status mention) throws TwitterException {
        reply(mention, "http://www47.atwiki.jp/karino2/pages/60.html");
    }

    private void replyStats(Status mention) throws TwitterException, JEAccessDeniedException {
        List<Text> texts = server.getTexts(getSrtId()).getTexts();
        int total = texts.size();
        int comp = 0;
        for(Text txt : texts) {
            if(txt.hasTarget())
                comp++;
        }
        
        StringBuilder blder = new StringBuilder();
        blder.append(comp + "/" + total + " (");        
        blder.append((new DecimalFormat("0.##")).format(100*comp/total));
        blder.append("%");
        blder.append(") completed");         

        reply(mention, blder.toString());
    }

    private void replyVideoUrl(Status mention) throws JEAccessDeniedException, TwitterException {
        String url = getSrtUrl();
        
        reply(mention, url);
    }

    public void reply(Status mention, String msg) throws TwitterException {
        StatusUpdate update = new StatusUpdate("@" + mention.getUser().getScreenName() + " " + msg);
        update.inReplyToStatusId(mention.getId());
        twitter.updateStatus(update);
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
    
    private Status getOriginal(Status mention) throws TwitterException {
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
        Collections.sort(filteredVals, new Comparator<Status>() {

            public int compare(Status lhs, Status rhs) {
                return lhs.getCreatedAt().compareTo(rhs.getCreatedAt());
            }
        });
        
        return filteredVals;
    }
    
    
    // tweets
    private void updateStatus(String body) throws TwitterException {
        twitter.updateStatus(body);
    }
    
    private void tweetFirstArea() throws JEAccessDeniedException, JEConflictException, TwitterException {
        if(!bookAreaRandom()) 
            return;
        
        List<Text> texts = getAreaTextsWithHeaderFooter();
        Range range = getAreaRange();
        for(Text text : texts) {
            tweetText(range, text);
            if(range.inside(text.getIndex()))
                return;
        }
        
    }
    
    public void tweets() {
        try {
            Date dt = new Date();
            int hour = dt.getHours();
            log.info("hour=" + hour);
            if(hour == 0) {
                tweetFirstArea();
                return;
            }
            if(hour >= 21)
                return;

            if(!bookArea()) {
                log.info("book area fail");
                return;
            }
            
            List<Text> texts = getAreaTextsWithHeaderFooter();
            Range range = getAreaRange();
            if(hour == 20) {
                tweetFooter(range, texts);
            } else {
                Text txt = getTarget(areaIndex, hour, texts);
                if(txt != null) {
                    tweetText(range, txt);
                } else {
                    log.info("get target fail");
                }
            }
            
            // bot1.freeMyArea();
            // bot1.bookArea();
            // bot1.doneArea(12);
            // bot1.freeArea(12);
         } catch (JEAccessDeniedException e) {
             log.info("access denied");
         } catch (JEConflictException e) {
             log.info("conflict");
         } catch (TwitterException e) {
             log.info("twitter exception");
        }
    }

    private Text getTarget(int areaIndex2, int hour, List<Text> texts) throws JEAccessDeniedException {
        // hour=0, localIndex = 1
        // hour=19, localIndex = 20
        // hour must be 1<= hour <= 19 for this method.
        // areaIndex start from 1.
        if(hour < 1 || hour > 19) {
            throw new RuntimeException("getTarget called not inside valid hour range: " + hour);
        }
        
        int textIndex = getAreaMap().localIndexToTextIndex(areaIndex2, hour+1);
        for(Text txt: texts) {
            if(txt.getIndex() == textIndex)
                return txt;
        }
        
        log.info("outside: text index=" + textIndex + " areaIndex=" + areaIndex2 + " hour=" + hour + ", first=" + texts.get(0).getIndex() + ", last=" + texts.get(texts.size()-1).getIndex());
        // outside.
        return null;
    }

    private void tweetFooter(Range range, List<Text> texts) throws TwitterException {
        for(Text txt : texts) {
            if(range.getEnd() < txt.getIndex()) {
                tweetText(range, txt);
            }
        }
    }

    public void tweetText(Range range, Text text) throws TwitterException {
        if(range.inside(text.getIndex()))
            updateStatus(text.getFormatedOriginal());
        else
            updateStatus("_ " + text.getOriginal());
        
        if(text.hasTarget())
            updateStatus("_ " + text.getTarget());
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
