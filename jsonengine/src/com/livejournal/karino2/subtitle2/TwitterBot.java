package com.livejournal.karino2.subtitle2;

import java.util.List;

import twitter4j.internal.logging.Logger;

import com.jsonengine.common.JEAccessDeniedException;
import com.jsonengine.common.JEConflictException;
import com.jsonengine.model.JEDoc;

public class TwitterBot {
    SubtitleServer server = new SubtitleServer();
    
    String account = "bot1";
    
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
}
