package com.livejournal.karino2.subtitle2;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import twitter4j.internal.logging.Logger;

import com.jsonengine.common.JEAccessDeniedException;
import com.jsonengine.common.JEConflictException;

public class DeleteServlet extends HttpServlet {

    /**
     * 
     */
    private static final long serialVersionUID = -8617829799925089577L;
    
    private static final Logger log = Logger.getLogger(DeleteServlet.class);

    
    // TODO: web.xml, add UI jsp.
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String srtId = req.getParameter("srtId");
        
        Subtitle subtitle = new Subtitle("bot1");
        try {
            subtitle.deleteWholeSrt(srtId);
        } catch (JEConflictException e) {
            log.info("jeconflict exception");
        } catch (JEAccessDeniedException e) {
            log.info("jeaccessdenied exception");
        }
        
        resp.setContentType("text/plain");
        resp.getWriter().println("done");
    }
}
