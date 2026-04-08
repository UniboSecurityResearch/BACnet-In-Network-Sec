// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.events;

/**
 * A data object that holds the information passed to {@link EventLoop#emit} and processed by {@link EventListener#handleEvent}.
 * @author drobin
 */
public class Event {

    public  Object        source;
    public  EventListener listener;
    public  EventType     eventType;
    public  Object[]      args;

    public Event(Object source, EventListener listener, EventType eventType, Object... args) {
        this.source = source;
        this.listener = listener;
        this.eventType = eventType;
        this.args = args;
    }

    public String toString() {
        if (args.length>0) {
            StringBuilder sb = new StringBuilder();
            sb.append("Event{" + source + " -> " + listener + ": \"" + eventType + "\" ");
            for (Object object: args) {
                sb.append(" ");
                sb.append(object.toString());
            }
            sb.append("}");
            return sb.toString();
        }
        else return "Event{"+source+" -> "+listener+": \""+ eventType +"\" }";
    }
}
