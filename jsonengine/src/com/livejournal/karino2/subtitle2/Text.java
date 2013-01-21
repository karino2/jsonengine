package com.livejournal.karino2.subtitle2;

import com.jsonengine.model.JEDoc;

public class Text extends JEDocWrapper {
    public Text(JEDoc jsonText) {
        super(jsonText);
    }
    
    public int getIndex() {
        return getInt("textId");
    }
    
    public String getOriginal() {
        return (String)get("original");
    }
    
    public String getTarget() {
        return (String) get("target");
    }
    
    public String getHeader() {
        return (String) get("header");
    }
    
    public void putTarget(String newTarget) {
        put("target", newTarget);
    }

    public String getFormatedOriginal() {
        return String.format("%d: %s", getIndex(), getOriginal());
    }

    public boolean hasTarget() {
        return (getTarget() != null &&
                !getTarget().equals(""));
    }
}
