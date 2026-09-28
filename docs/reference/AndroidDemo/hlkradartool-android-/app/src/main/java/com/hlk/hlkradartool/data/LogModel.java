package com.hlk.hlkradartool.data;

import java.io.Serializable;

public class LogModel implements Serializable {

    public LogState state;
    public String message;
    public int iProgress;

    public LogModel(LogState logState,String strMsg,int iProgress) {
        this.state = logState;
        this.message = strMsg;
        this.iProgress = iProgress;
    }

    public LogState getState() {
        return state;
    }

    public void setState(LogState state) {
        this.state = state;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getiProgress() {
        return iProgress;
    }

    public void setiProgress(int iProgress) {
        this.iProgress = iProgress;
    }



}
