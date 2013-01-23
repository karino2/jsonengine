package com.livejournal.karino2.subtitle2;

import java.io.BufferedWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.jsonengine.common.JEAccessDeniedException;


public class DownloadServlet extends HttpServlet {

    /**
     * auto generated
     */
    private static final long serialVersionUID = -3658982315546588772L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String srtId = req.getParameter("srtId");
        
        Subtitle subtitle = new Subtitle("bot1");
        try {
            List<Text> txts = subtitle.getTextsBySrtId(srtId);
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            SimpleDateFormat timeStampFormat = new SimpleDateFormat("yyyyMMdd_HHmmssSS");
            String filename = timeStampFormat.format(new Date()) + ".srt";
            resp.setHeader( "Content-Disposition", "attachment; filename=" + filename );

            BufferedWriter bw = new BufferedWriter(resp.getWriter());
            for(Text txt : txts) {
                bw.write(String.valueOf(txt.getIndex()));
                bw.newLine();
                bw.write(txt.getHeader());
                bw.newLine();
                bw.write(txt.getTarget());
                bw.newLine();
                bw.newLine();

            }
            bw.flush();
            bw.close();
        } catch (JEAccessDeniedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
    }

}
