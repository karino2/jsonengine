package com.livejournal.karino2.subtitle2;

import java.math.BigDecimal;

import com.jsonengine.model.JEDoc;

public class JEDocWrapper {

    protected JEDoc jsonObj;

    public JEDocWrapper(JEDoc json) {
        jsonObj = json;
    }
    
    public JEDoc getJEDoc() {
        return jsonObj;
    }

    public String getDocId() {
        return jsonObj.getDocId();
    }

    protected Object get(String name) {
        return jsonObj.getDocValues().get(name);
    }
    
    protected int getInt(String name) {
        BigDecimal bd = (BigDecimal)get(name);
        return bd.intValue();        
    }
    
    protected void put(String name, Object val) {
        jsonObj.getDocValues().put(name, val);
    }

}