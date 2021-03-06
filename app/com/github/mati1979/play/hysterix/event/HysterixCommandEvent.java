package com.github.mati1979.play.hysterix.event;

import com.github.mati1979.play.hysterix.HysterixCommand;

/**
 * Created by mati on 06/06/2014.
 */
public class HysterixCommandEvent {

    private final HysterixCommand command;
    private final long currentTime = System.currentTimeMillis();

    public HysterixCommandEvent(final HysterixCommand command) {
        this.command = command;
    }

    public HysterixCommand getHysterixCommand() {
        return command;
    }

    public long getCurrentTime() {
        return currentTime;
    }

    @Override
    public String toString() {
        return "HysterixCommandEvent{" +
                "command=" + command +
                ", currentTime=" + currentTime +
                '}';
    }

}
