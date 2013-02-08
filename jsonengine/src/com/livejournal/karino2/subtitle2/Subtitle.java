package com.livejournal.karino2.subtitle2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.List;

import javax.annotation.Nullable;

import twitter4j.internal.logging.Logger;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.datastore.TransactionOptions;
import com.google.appengine.labs.repackaged.com.google.common.base.Predicate;
import com.jsonengine.common.JEAccessDeniedException;
import com.jsonengine.common.JEConflictException;
import com.jsonengine.controller.task.DeleteSrtTaskController;
import com.jsonengine.model.JEDoc;

public class Subtitle {
    SubtitleServer server = new SubtitleServer();
    
    String account;
    
    public Subtitle(String accountName) {
        account = accountName;
    }
    
    private static final Logger log = Logger.getLogger(Subtitle.class);
        
    private String srtId = null;
    
    private JEDoc getPreferedSrt() throws JEAccessDeniedException {
        AreaMap am = getAreaMap();
        if(am == null)
            return null;
        String sid = am.getSrtId();
        return getSrtById(sid);
    }
    
    private JEDoc getSrtById(String sid) throws JEAccessDeniedException {
        List<JEDoc> srtList = server.getRawSrts();
        if(srtList.isEmpty())
        {
            log.info("srt is empty");
            return null;
        }
        for(JEDoc srt : srtList) {
            if(sid.equals(srt.getDocId()))
                    return srt;
            
        }
        return null;
    }

    public String getSrtUrl() throws JEAccessDeniedException {
        JEDoc srt = getPreferedSrt();
        if(srt == null)
            return null;
        JEDocWrapper wrapper = new JEDocWrapper(srt);
        return (String)wrapper.get("srtUrl");
    }
    
    public String getSrtId() throws JEAccessDeniedException {
        if(srtId == null) {
            JEDoc srt = getPreferedSrt();
            if(srt == null)
                return null;            
            srtId = srt.getDocId();
        }
        return srtId;
    }
        
    private AreaMap getPreferebleAreaMap() throws JEAccessDeniedException {
        List<AreaMap> maps = server.getAreaMapList();
        List<AreaMap> mine = filterMine(maps);
        if(!mine.isEmpty())
            return mine.get(0);
        
        List<AreaMap> wildWest = filterWildWest(maps);
        if(!wildWest.isEmpty())
            return wildWest.get(0);
        
        List<AreaMap> oldFirstMaps = filterOldTouchedFirst(maps);
        if(!oldFirstMaps.isEmpty())
            return oldFirstMaps.get(0);
        return null;
    }

    private List<AreaMap> filterOldTouchedFirst(List<AreaMap> maps) {
        List<AreaMap> hasEmpties = AreaMap.filter(maps, new Predicate<AreaMap>() {
                    public boolean apply(@Nullable AreaMap map) {
                        return map.hasEmpty();
                    }});
        if(hasEmpties.isEmpty())
            return hasEmpties;
        Collections.sort(hasEmpties, new Comparator<AreaMap>() {
            public int compare(AreaMap lhs, AreaMap rhs) {
                return - (int)(lhs.getLatestUpdateTime() - rhs.getLatestUpdateTime());
            }
            
        });
        return hasEmpties;
    }

    private List<AreaMap> filterWildWest(List<AreaMap> maps) {
        return AreaMap.filter(maps, new Predicate<AreaMap>() {

            public boolean apply(@Nullable AreaMap map) {
                return map.isWildWest();
            }});
    }

    public List<AreaMap> filterMine(List<AreaMap> maps) {
        return AreaMap.filter(maps, new Predicate<AreaMap>() {

            public boolean apply(@Nullable AreaMap map) {
                return -1 != map.findMyArea(account, -1);
            }});
    }
    
    AreaMap areaMap = null;
    public AreaMap getAreaMap() throws JEAccessDeniedException {
        if(areaMap == null) {
            areaMap = getPreferebleAreaMap();
        }
        return areaMap;
    }
    
    public void freeMyArea() throws JEConflictException, JEAccessDeniedException {
        AreaMap areaMap = getAreaMap();
        if(areaMap == null)
            return;
        
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
        if(areaMap == null) {
            log.info("area is full");
            return false;
        }
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
        if(areaMap == null)
            return;
        areaMap.doneArea(areaIndex);
        server.updateAreaMap(areaMap);        
    }

    // set to empty, but for normal case, it is for free booked area.
    public void freeArea(int areaIndex) throws JEAccessDeniedException, JEConflictException {
        AreaMap areaMap = getAreaMap();
        if(areaMap == null)
            return;
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
    
    public List<Text> getTextsBySrtId(String srtId2) throws JEAccessDeniedException {
        List<JEDoc> orderdDocs = server.getRawTexts(srtId2);
        ArrayList<Text> res = new ArrayList<Text>();
        for(JEDoc doc : orderdDocs) {
            res.add(new Text(doc));
        }
        return res;
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

    public List<Text> getCurrentTexts() throws JEAccessDeniedException {
        return server.getTexts(getSrtId()).getTexts();
    }

    public Text getTarget(int hour, List<Text> texts) throws JEAccessDeniedException {
        // hour=0, localIndex = 1
        // hour=19, localIndex = 20
        // hour must be 1<= hour <= 19 for this method.
        // areaIndex start from 1.
        if(hour < 1 || hour > 19) {
            throw new RuntimeException("getTarget called not inside valid hour range: " + hour);
        }
        
        int textIndex = getAreaMap().localIndexToTextIndex(areaIndex, hour+1);
        for(Text txt: texts) {
            if(txt.getIndex() == textIndex)
                return txt;
        }
        
        log.info("outside: text index=" + textIndex + " areaIndex=" + areaIndex + " hour=" + hour + ", first=" + texts.get(0).getIndex() + ", last=" + texts.get(texts.size()-1).getIndex());
        // outside.
        return null;
    }

    public void deleteWholeSrt(String srtId2) throws JEConflictException, JEAccessDeniedException {
        DeleteSrtTaskController.addDeleteSrtTask(srtId2);
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        final Transaction tx = datastore.beginTransaction(TransactionOptions.Builder.withXG(true));
        try {
            deleteAreaMap(datastore, tx, srtId2);
            deleteSrtById(datastore, tx, srtId2);
            tx.commit();
        } catch(ConcurrentModificationException e) {
            throw new JEConflictException(e);
        }
        
    }


    private void deleteSrtById(DatastoreService datastore, Transaction tx, String srtId2) throws JEAccessDeniedException {
        JEDoc doc = server.getRawSrtById(srtId2);
        datastore.delete(tx, doc.getKey());
    }

    private void deleteAreaMap(DatastoreService datastore, Transaction tx, String srtId2) throws JEAccessDeniedException, JEConflictException {
        AreaMap map = server.getAreaMap(srtId2);
        datastore.delete(tx, map.getJEDoc().getKey());        
    }
}
