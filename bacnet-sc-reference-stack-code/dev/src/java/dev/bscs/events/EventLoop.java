// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.events;

import dev.bscs.common.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The main processor for a single-threaded "event loop" style programming model, like GUIs, JavaScript, etc. This
 * allows the vast majority of the code to run in a single thread so that it is all mutually thread-safe and does not
 * require synchronization, while still allowing asynchronous events from other threads to be processed without latency
 * or polling. For example, in a protocol stack application, all the datalink, network, and application layers run in
 * the same thread but "network events" like incoming packets from multiple low level listening threads are fired as
 * events on a thread-safe queue and de-queued immediately in the main thread to be processed up and down the stack.
 * Since nothing in the main application blocks, almost everything should be written as state machines that respond to
 * the externally queued events or "maintenance" events.  Any thread can call {@link #emit} and it will create an
 * {@link Event} object from the method's parameters and queue that Event on the FIFO to be dequeued in the main thread.
 * The event loop is waiting on that queue so there is very little latency in responding to new events thus enqueued.
 * In addition to waiting on external <code>emit</code> calls, the EventLoop also maintains an internal  "maintenance"
 * timer thread that fires a maintenance event that will be received by all listeners that registered with
 * {@link #addMaintenance}. This allows state machines that need to do state transition timeouts or cleanups that are
 * based solely on time and not external actions. The application can determine the frequency of the maintenance timer
 * when it calls the {@link #run} method to start the loop.
 * @author drobin
 */
public class EventLoop {

    private static Log log = new Log(EventLoop.class);

    private static final BlockingQueue<Event>  events = new LinkedBlockingDeque<>(10000);
    private static final List<EventListener>   maintenanceListeners = new ArrayList<>();
    private static final AtomicBoolean         stopMain = new AtomicBoolean(false);
    private static final AtomicBoolean         stopMaintenance = new AtomicBoolean(false);
    private static boolean                     haltOnOverflow = true;

    public static  int statsEventQueueSize;
    public static  int statsEventQueueSizeMax;
    public static  int statsMaintenanceSize;
    public static  int statsMaintenanceSizeMax;

    private EventLoop() { }  // private constructor; can't make instance; static singleton only

    public  static final EventType EVENT_MAINTENANCE = new EventType("maintenance"); // send to all registered maintenance listeners

    public static void addMaintenance(EventListener listener)    {
        log.trace("addMaintenance:"+listener);
        synchronized (maintenanceListeners) {
            if (!maintenanceListeners.contains(listener)) maintenanceListeners.add(listener);
            statsMaintenanceSize = maintenanceListeners.size();
            if (statsMaintenanceSize>statsMaintenanceSizeMax) statsMaintenanceSizeMax=statsMaintenanceSize;
        }
    }

    public static void removeMaintenance(EventListener listener) {
        log.trace("removeMaintenance:"+listener);
        synchronized (maintenanceListeners) {
            maintenanceListeners.remove(listener);
            statsMaintenanceSize = maintenanceListeners.size();
            if (statsMaintenanceSize>statsMaintenanceSizeMax) statsMaintenanceSizeMax=statsMaintenanceSize;
        }
    }

    public static  List<EventListener> getMaintenanceListeners() {
        List<EventListener> listeners; // return on a copy
        synchronized (maintenanceListeners) { listeners = new ArrayList<>(maintenanceListeners); }
        return listeners;
    }

    public static void cancelEventsFrom(Object source) {
        log.trace("CANCELFROM:"+source);
        synchronized (events) {
            // for speed reasons, we don't actually remove anything from the list, we just neuter it
            for (Event event : events) if (event.source == source) event.eventType = null;
        }
    }

    public static void cancelEventsTo(EventListener listener) {
        log.trace("CANCELTO:"+listener);
        synchronized (events) {
            // for speed reasons, we don't actually remove anything from the list, we just neuter it
            for (Event event : events) if (event.listener == listener) event.eventType = null;
        }
    }

    public static void emit(Object source, EventListener listener, EventType eventType, Object... args) {
        Event event = new Event(source, listener, eventType, args);
        if (event.eventType != EVENT_MAINTENANCE) log.trace("QUEUE:"+event); // don't pollute log with maintenance
        boolean queued;
        synchronized (events) {
            queued = events.offer(event);
            statsEventQueueSize = events.size();
            if (statsEventQueueSize>statsEventQueueSizeMax) statsEventQueueSizeMax=statsEventQueueSize;
        }
        if (!queued) {
            log.error("EVENT QUEUE OVERFLOW sending " + eventType + " from " + source + " to " + listener); // don't block, drop if full
            if (haltOnOverflow) System.exit(1);
        }
    }

    static public void run(int maintenanceInterval, boolean haltOnOverflow)  {
        EventLoop.haltOnOverflow = haltOnOverflow; // can be used for fail fast testing to crash rather then lose events
        if (maintenanceInterval != 0) { // set up a maintenance thread to kick us every interval
            new Thread(() -> {
                while(!stopMaintenance.compareAndSet(true,false)) {
                    try { Thread.sleep(maintenanceInterval); } catch (InterruptedException e) { log.implementation("EventLoop maintenance thread sleep() interrupted?"); }
                    emit(EventLoop.class,null,EVENT_MAINTENANCE); // listener is ignored for maintenance
                }
                log.info("Maintenance Thread ending");
            }).start();
        }
        while (!stopMain.compareAndSet(true,false)) { // now run the main loop
            try {
                Event event = events.take();  // blocking wait for an event from emit()
                if (event.eventType == null) continue; // eventType will be null if it was cancelled while in the queue
                if (event.eventType == EVENT_MAINTENANCE) {
                    // for maintenance we ignore the null listener in the event and distribute it to all maintenance listeners.
                    List<EventListener> listeners; // work on a copy because event handlers can add/remove from maintenanceListeners
                    synchronized (maintenanceListeners) { listeners = new ArrayList<>(maintenanceListeners); }
                    for (EventListener listener : listeners)
                        try { listener.handleEvent(event.source, event.eventType, event.args); }
                        catch (Throwable t) { log.error("Event Handler Badness: "+t); }
                }
                else if (event.listener != null) {  // don't crash if someone emits event to null listener
                    log.trace("DISPATCH:"+event);
                    try { event.listener.handleEvent(event.source, event.eventType, event.args); }
                    catch (Throwable t) { log.error("Event Handler Badness: "+t); }
                }
            } catch (InterruptedException e) { log.implementation("EventLoop interrupted?"); }
        }
        log.info("Event Loop ending");
    }

    static public void stop() {
        stopMain.compareAndSet(false,true);
        stopMaintenance.compareAndSet(false,true);
        events.clear();
        events.add(new Event(null, null, null)); // main thread is blocking on an event, so give it one
    }

}