package com.livejournal.karino2.subtitle2;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

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

    public void init(ServletConfig sc) throws ServletException {
        config = sc;
    }
 

    private boolean isCustom(String cmd) {
        return (cmd.equals("text") || cmd.equals("area"));            
    }
    
    private String botName(HttpServletRequest req) {
        if(req.getParameterMap().containsKey("botName") &&
                req.getParameter("botName").equals("bot2"))
            return "bot2";
        return "bot1";
    }

    public void service(ServletRequest req, ServletResponse resp)
            throws ServletException, IOException {
        HttpServletRequest httpReq = (HttpServletRequest) req;
        

        String cmd = httpReq.getParameter("cmd");
        if(isCustom(cmd)) {
            String name = botName(httpReq);
            TwitterBot bot1 = TwitterBot.createBot(name);
            if (cmd.equals("area")){
                bot1.tweetWholeArea();
            }else if (cmd.equals("text")) {
                // /cron/tweetsrt?cmd=text&textId=3
                int textId = Integer.parseInt(httpReq.getParameter("textId"));
                bot1.tweetOneText(textId);
            }
                 
        } else {
            oneBotCronCommand(cmd, TwitterBot.createBot("bot1"));
            oneBotCronCommand(cmd, TwitterBot.createBot("bot2"));            
        }
        
                
        resp.setContentType("text/plain");
        PrintWriter writer = resp.getWriter();
        
        writer.print("done");
        writer.flush();
        writer.close();
    }

    public void oneBotCronCommand(String cmd, TwitterBot bot) {
        if(cmd.contains("tweets")) {     
            bot.tweets();
        } else {
            bot.checkMentions();
        }
    }
    
}
