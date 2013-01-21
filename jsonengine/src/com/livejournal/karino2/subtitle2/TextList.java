package com.livejournal.karino2.subtitle2;

import java.util.List;

import com.jsonengine.model.JEDoc;

public class TextList {
    String srtId;
    List<JEDoc> orderedTexts;
    
    String getSrtId() { return srtId;}
    
    List<JEDoc> getTexts() {
        return orderedTexts;
    }
    
    public TextList(String srtId, List<JEDoc> textDocs) {
        orderedTexts = textDocs;
        this.srtId = srtId;
    }
    
    public boolean equalSrtId(String srtId) {
        return this.srtId.equals(srtId);
    }
}
