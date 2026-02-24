package com.data;

public class Clicks {

    int eventID;
    long timestamp;
    int xPosition; // position of the click within the x-coordinate of the screen
    int yPosition; // position of the click within the y-coordinate of the screen
    String clickedElement;

    public Clicks(int eventID, long timestamp, int xPosition, int yPosition, String clickedElement) {
        this.eventID = eventID;
        this.timestamp = timestamp;
        this.xPosition = xPosition;
        this.yPosition = yPosition;
        this.clickedElement = clickedElement;
    }

    public int getEventID() {
        return eventID;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getxPosition() {
        return xPosition;
    }

    public int getyPosition() {
        return yPosition;
    }

    public String getClickedElement() {
        return clickedElement;
    }

    public String toString()
    {
        return "eventID: "+eventID+", " +
                "timestamp: "+timestamp+", " +
                "xPosition: "+xPosition+", " +
                "yPosition: "+yPosition+", " +
                "clickedElement: "+clickedElement+", ";
    }
}
