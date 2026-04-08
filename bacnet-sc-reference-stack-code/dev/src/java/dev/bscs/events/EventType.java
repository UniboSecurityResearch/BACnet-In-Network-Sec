// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.events;

/**
 * A class used to make constant (static final) "event types" for comparison against by event handlers. Event types are
 * not dynamically created. They are constants that are compared with '==' operator.  The private String inside is only
 * for toString() for debugging reasons.
 *
 * An event generating class makes an EventType like this:
 *
 *     public static final EventType EVENT_MAINTENANCE = new EventType("maintenance");
 *
 * And an event listener class compares them like this:
 *
 *     @Override public void handleEvent(Object source, EventType eventType, Object... args) {
 *         if (eventType == EventLoop.EVENT_MAINTENANCE) ...
 *
 * @author drobin
 */
public class EventType {
    private String eventType;
    public  EventType(String eventType) { this.eventType = eventType; }
    public  String toString() { return eventType; }
}
