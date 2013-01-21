package com.livejournal.karino2.subtitle2;

import java.util.ArrayList;
import java.util.List;

import com.jsonengine.model.JEDoc;

public class TextList {
    String srtId;
    List<JEDoc> orderedTexts;
    
    String getSrtId() { return srtId;}
    
    List<JEDoc> getRawTexts() {
        return orderedTexts;
    }
    
    public TextList(String srtId, List<JEDoc> textDocs) {
        orderedTexts = textDocs;
        this.srtId = srtId;
    }
    
    List<Text> texts;
    public List<Text> getTexts() {
        if(texts != null)
            return texts;
        texts = new ArrayList<Text>();
        for(JEDoc doc : orderedTexts) {
            texts.add(new Text(doc));
        }
        return texts;
    }
    
    
    public boolean equalSrtId(String srtId) {
        return this.srtId.equals(srtId);
    }
}
