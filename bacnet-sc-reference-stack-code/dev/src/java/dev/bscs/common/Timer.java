// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.common;

/**
 * A general purpose timer that can be used for one-time or periodic timings.
 * All values are in milliseconds, but this uses System.nanoTime() internally to be immune to clock changes.
 * @author drobin
 */
public class Timer {

    private long expiration;
    private long timeout;

    public Timer() {  }

    public Timer(long startingTimeout) { start(startingTimeout); }


    public void    start(long timeout) {
        this.timeout = timeout;
        if (timeout == 0) expiration = 0;
        else expiration = System.nanoTime()/1000000 + timeout;
    }

    public void    restart() {
        expiration = System.nanoTime()/1000000 + timeout;
    }

    public void    clear() {
        timeout = 0;
        expiration = 0;
    }

    public boolean expired() {
        return expiration != 0 && System.nanoTime()/1000000 > expiration;
    }

    public long remaining() {
        long current = System.nanoTime()/1000000;
        return current > expiration? 0 : expiration - current;
    }

    public String toString() {
        return expiration==0? "cleared" : remaining()/1000+"/"+timeout/1000;
    }
}
