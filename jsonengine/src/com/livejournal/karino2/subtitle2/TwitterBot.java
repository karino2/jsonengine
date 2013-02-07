package com.livejournal.karino2.subtitle2;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
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
import com.google.appengine.api.datastore.Query.CompositeFilterOperator;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.datastore.Transaction;
import com.jsonengine.common.JEAccessDeniedException;
import com.jsonengine.common.JEConflictException;

public class TwitterBot {
    
    String accountPrefix = "@SubCourBot";
    String account = "bot1";
    Pattern textIdPat;
    Pattern replyHeaderPat;
    
    private static final Logger log = Logger.getLogger(TwitterBot.class);

    Twitter twitter;
    Subtitle subtitle;
    public TwitterBot(Twitter twit) {
        twitter = twit;
        textIdPat = Pattern.compile("^([0-9][0-9]*):");
        replyHeaderPat = Pattern.compile("^@[0-9a-zA-Z]* (.*)$");
        subtitle = new Subtitle(account);
    }
    
    
    public void checkMentions() {
        // log.info("check mention");
        DatastoreService service = DatastoreServiceFactory.getDatastoreService();
        try {
            ResponseList<Status> mentions = twitter.getMentionsTimeline();
            List<Status> notYetRepliedMentions = filterAlreadyReplied(service, mentions);
            
            // log.info("mentions size: " + mentions.size());
            // log.info("not yet reply num: " + notYetRepliedMentions.size());
            for(Status mention : notYetRepliedMentions) {
                try {
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
                } catch (TwitterException e) {
                    log.info("twitter exception: " + e.getMessage());
                } catch (JEAccessDeniedException e) {
                    log.info("JEAccessDenied exception: " + e.getMessage());
                } catch (JEConflictException e) {
                    log.info("JEConflict exception: " + e.getMessage());
                }
                finally {
                    storeToDB(service, mention);
                }
            }
        } catch (TwitterException e1) {
            log.info("twitter exception: " + e1.getMessage());
        } catch (JEAccessDeniedException e1) {
            log.info("JEAccessDenied exception: " + e1.getMessage());
        }

    }
    
    private boolean isDirectMention(Status mention) {
        return mention.getText().startsWith(accountPrefix);
    }
    
    private void handleNonReplyMention(DatastoreService service, Status mention) throws JEAccessDeniedException {
        try
        {
            if(isDirectMention(mention)){
                // log.info("call reply info");
                replyInfo(mention);
            }
        } catch (TwitterException e) {
            log.info("twitter exception: " + e.getMessage());
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
        List<Text> texts = subtitle.getCurrentTexts();
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
        String url = subtitle.getSrtUrl();
        
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
            subtitle.submitTarget(id, message);
            
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

    private void storeToDB(DatastoreService service, Status mention) {
        Transaction transaction = service.beginTransaction();
        Entity entMention = new Entity("HandledMention");
        entMention.setProperty("tweetId", mention.getId());
        entMention.setProperty("botName", account);
        entMention.setProperty("postDate", new Date());
        service.put(transaction, entMention);
        transaction.commit();
    }    
    
    private List<Status> filterAlreadyReplied(DatastoreService service, ResponseList<Status> mentions) throws JEAccessDeniedException {
        ArrayList<Status> filteredVals = new ArrayList<Status>();
        for(Status reply : mentions) {
            boolean isHandled = isAlreadyHandled(service, reply);
    
            if(!isHandled) {
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



    public boolean isAlreadyHandled(DatastoreService service, Status reply)
            throws JEAccessDeniedException {
        Query query = new Query("HandledMention");
        query.setFilter(
            CompositeFilterOperator.and(
                FilterOperator.EQUAL.of("tweetId", reply.getId()),
                FilterOperator.EQUAL.of("botName", account)
                ));
        query.addSort("postDate", SortDirection.DESCENDING);
        if(isMatch(service, query))
            return true;

        
        
        // transition code. we'll remove later.
        Query query2 = new Query("HandledPost");
        query2.setFilter(
            CompositeFilterOperator.and(
                FilterOperator.EQUAL.of("postUser", reply.getUser().getScreenName()),
                FilterOperator.EQUAL.of("postText", reply.getText()),
                FilterOperator.EQUAL.of("botName", account)
                ));
        query2.addSort("postDate", SortDirection.DESCENDING);
        return isMatch(service, query2);        
    }



    public boolean isMatch(DatastoreService service, Query query) {
        PreparedQuery pq = service.prepare(query);
        FetchOptions limit = FetchOptions.Builder.withOffset(0).limit(1);
        List<Entity> results = pq.asList(limit);
        
        return !results.isEmpty();
    }
    
    
    // tweets
    private void updateStatus(String body) throws TwitterException {
        twitter.updateStatus(body);
    }
    
    private void tweetFirstArea() throws JEAccessDeniedException, JEConflictException, TwitterException {
        if(!subtitle.bookAreaRandom()) 
            return;
        
        List<Text> texts = subtitle.getAreaTextsWithHeaderFooter();
        Range range = subtitle.getAreaRange();
        for(Text text : texts) {
            tweetText(range, text);
            if(range.inside(text.getIndex()))
                return;
        }
        
    }
    
    public void tweetWholeArea() {
        try {
            if(!subtitle.bookArea()) {
                log.info("book area fail");
                return;
            }
            List<Text> texts = subtitle.getAreaTextsWithHeaderFooter();

            Range range = subtitle.getAreaRange();
            for(Text txt: texts) {
                try {
                    tweetText(range, txt);
                }catch(TwitterException e) {
                    log.info("twitter exception");
                    
                }
            }
        } catch (JEAccessDeniedException e) {
            log.info("access denied");
        } catch (JEConflictException e) {
            log.info("conflict");
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

            if(!subtitle.bookArea()) {
                log.info("book area fail");
                return;
            }
            
            List<Text> texts = subtitle.getAreaTextsWithHeaderFooter();
            Range range = subtitle.getAreaRange();
            if(hour == 20) {
                tweetFooter(range, texts);
            } else {
                Text txt = subtitle.getTarget(hour, texts);
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



    public void tweetOneText(int textId) {
        try {
            if(!subtitle.bookArea()) {
                log.info("book area fail");
                return;
            }
            List<Text> texts = subtitle.getAreaTextsWithHeaderFooter();

            Range range = subtitle.getAreaRange();
            for(Text txt: texts) {
                try {
                    if(txt.getIndex() == textId)
                        tweetText(range, txt);
                }catch(TwitterException e) {
                    log.info("twitter exception");
                    
                }
            }        

        } catch (JEAccessDeniedException e) {
            log.info("access denied");
        } catch (JEConflictException e) {
            log.info("conflict");
       }
    }

}
