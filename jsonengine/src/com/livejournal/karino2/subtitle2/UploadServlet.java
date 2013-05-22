package com.livejournal.karino2.subtitle2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import twitter4j.internal.logging.Logger;



import com.jsonengine.common.JEAccessDeniedException;
import com.jsonengine.common.JEConflictException;
import com.jsonengine.common.JENotFoundException;
import com.jsonengine.model.JEDoc;

public class UploadServlet extends HttpServlet {

    /**
     * 
     */
    private static final long serialVersionUID = 6380345011825169167L;

    private static final Logger log = Logger.getLogger(UploadServlet.class);
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String title = req.getParameter("title");
        String url = req.getParameter("url");
        String content = req.getParameter("content");
        
        String srtId;
        try {
            srtId = createSrt(title, url);
            
            BufferedReader br = new BufferedReader(new StringReader(content), 8*1024);

            int textId = 0;
            String line = br.readLine();
            
            // skip empty.
            while(line.equals("")) {
                line = br.readLine();
            }
            while(line != null)
            {
                String header = "";
                String original = "";
                textId++;
                
                assert(textId == Integer.valueOf(line));
                
                header = br.readLine();
                
                StringBuffer sb = new StringBuffer();
                line = br.readLine();
                boolean first = true;
                while(line != null && !line.equals("")) {
                    if(first) {
                        first = false;
                    } else {
                        sb.append("\n");
                    }
                    sb.append(line);
                    line = br.readLine();
                }
                
                original = sb.toString();
                
                postText(srtId, textId, header, original);
                
                line = br.readLine();
            }
            
            createAreaMap(srtId, textId);

            resp.sendRedirect("/_admin/index.html");
        } catch (JEConflictException e) {
            log.info("je conflict exception");
            e.printStackTrace();
        } catch (JEAccessDeniedException e) {
            log.info("je access denied exception");
        } catch (JENotFoundException e) {
            log.info("je not found exception");
        }
        
        
        
    }

    private void createAreaMap(String srtId, int textNum) throws JEConflictException, JEAccessDeniedException, JENotFoundException {
        Map<String, Object> jsonMap = new HashMap<String, Object>();
        jsonMap.put("srtId", srtId);
        jsonMap.put("textNum", textNum);
        int areaNum = 1 + (textNum-1)/20;

        for(int i = 0; i < areaNum; i++) {
            ArrayList<String> arr = new ArrayList<String>();
            arr.add("e");
            arr.add(null);
            arr.add(null);

            jsonMap.put("a"+(i+1), arr);
        }
        server.put("areaMap", jsonMap);
    }

    private void postText(String srtId, int textId, String header,
            String original) throws JEConflictException, JEAccessDeniedException, JENotFoundException {
        Map<String, Object> jsonMap = new HashMap<String, Object>();
        jsonMap.put("srtId", srtId);
        jsonMap.put("textId", textId);
        jsonMap.put("header", header);
        jsonMap.put("original", original);
        jsonMap.put("target", "");
        server.put("text", jsonMap);
    }

    SubtitleServer server = new SubtitleServer();

    private String createSrt(String title, String url) throws JEConflictException, JEAccessDeniedException, JENotFoundException {
        Map<String, Object> jsonMap = new HashMap<String, Object>();
        jsonMap.put("srtTitle", title);
        jsonMap.put("srtUrl", url);
        jsonMap.put("status", 0);
        JEDoc srt = server.put("srt", jsonMap);

        return srt.getDocId();
    }
}
