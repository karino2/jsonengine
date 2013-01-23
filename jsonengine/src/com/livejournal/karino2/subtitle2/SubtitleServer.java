package com.livejournal.karino2.subtitle2;

import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.List;

import org.slim3.datastore.Datastore;


import com.google.appengine.api.datastore.Transaction;
import com.jsonengine.common.JEAccessDeniedException;
import com.jsonengine.common.JEConflictException;
import com.jsonengine.model.JEDoc;
import com.jsonengine.service.query.QueryFilter;
import com.jsonengine.service.query.QueryRequest;
import com.jsonengine.service.query.QueryService;

public class SubtitleServer {
    QueryService service = new QueryService();
    
    public List<JEDoc> getRawSrts() throws JEAccessDeniedException {
        QueryRequest qReq = new QueryRequest();
        qReq.setDocType("srt");
        
        return service.queryAsJEDocList(qReq);
    }
    
    public JEDoc getRawAreaMap(String srtId) throws JEAccessDeniedException {
        QueryRequest qReq = new QueryRequest();
        qReq.setDocType("areaMap");
        QueryFilter.addCondFilter(qReq, "srtId", "eq", srtId);
        
        List<JEDoc> results = service.queryAsJEDocList(qReq);
        if(results.isEmpty())
            throw new IndexOutOfBoundsException("no such areaMap of srtId");
        return results.get(0);
    }
    
    public AreaMap getAreaMap(String srtId) throws JEAccessDeniedException {
        return new AreaMap(getRawAreaMap(srtId));
    }
    
    
    public static void sort(List<JEDoc> jsonTexts) {
        Collections.sort(jsonTexts, new Comparator<JEDoc>() {

            public int compare(JEDoc lhs, JEDoc rhs) {
                Text txtLeft = new Text(lhs);
                Text txtRight = new Text(rhs);
                return txtLeft.getIndex()- txtRight.getIndex();
            }
        });
    }
    
    TextList orderedTextsCache;
    public void resetCache() {
        orderedTextsCache = null;        
    }
    
    private boolean isCacheValid(String srtId) {
        return orderedTextsCache != null && orderedTextsCache.equalSrtId(srtId);
    }
    
    public void deleteTextsBySrtId(Transaction tx, String srtId) throws JEAccessDeniedException {
        QueryRequest qReq = getTextBySrtIdQuery(srtId);
        Datastore.delete(service.queryAsModelQuery(qReq).asKeyList());        
    }
    
    public TextList getTexts(String srtId) throws JEAccessDeniedException {
        if(isCacheValid(srtId))
            return orderedTextsCache;
        
        QueryRequest qReq = getTextBySrtIdQuery(srtId);
        
        List<JEDoc> txts = service.queryAsJEDocList(qReq);
        sort(txts);
        
        orderedTextsCache = new TextList(srtId, txts);
        return orderedTextsCache;
    }

    public QueryRequest getTextBySrtIdQuery(String srtId) {
        QueryRequest qReq = new QueryRequest();
        qReq.setDocType("text");
        QueryFilter.addCondFilter(qReq, "srtId", "eq", srtId);
        return qReq;
    }
    
    public List<JEDoc> getRawTexts(String srtId) throws JEAccessDeniedException {
        return getTexts(srtId).getRawTexts();
    }

    public void updateJEDoc(JEDoc jeDoc) throws JEConflictException {
        final Transaction tx = Datastore.beginTransaction();
        try {
            Datastore.put(tx, jeDoc);
            Datastore.commit(tx);
        } catch (ConcurrentModificationException e) {
            throw new JEConflictException(e);
        }
        
    }
    
    public void updateAreaMap(AreaMap areaMap) throws JEConflictException {
        updateJEDoc(areaMap.getJEDoc());
    }
    
    public void updateText(Text text) throws JEConflictException {
        updateJEDoc(text.getJEDoc());
    }

    public JEDoc getRawSrtById(String srtId2) throws JEAccessDeniedException {
        List<JEDoc> srts = getRawSrts();
        for(JEDoc srt : srts) {
            if(srt.getDocId().equals(srtId2))
                return srt;
        }
        return null;
    }
}
