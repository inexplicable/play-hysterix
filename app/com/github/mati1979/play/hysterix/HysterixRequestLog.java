package com.github.mati1979.play.hysterix;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.libs.F;
import scala.concurrent.Future;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HysterixRequestLog {

    static final int MAX_STORAGE = 1000;

    private static final Logger logger = LoggerFactory.getLogger(HysterixRequestLog.class);

    private LinkedBlockingQueue<HysterixCommand<?>> executedCommands = new LinkedBlockingQueue<>(MAX_STORAGE);

    private LinkedBlockingQueue<scala.concurrent.Promise<Collection<HysterixCommand<?>>>> promises = new LinkedBlockingQueue<>();

    private final HysterixContext hysterixContext;

    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    public HysterixRequestLog(final HysterixContext hysterixContext) {
        this.hysterixContext = hysterixContext;
        if (hysterixContext.getHysterixSettings().isLogRequestStatistics()) {
            scheduleTimerTask();
        }
    }

    private void scheduleTimerTask() {
        final long timeoutInMs = hysterixContext.getHysterixSettings().getLogRequestStatisticsTimeoutMs();
        scheduledExecutorService.schedule(() -> notifyPromises(), timeoutInMs, TimeUnit.MILLISECONDS);
    }

    public void addExecutedCommand(final HysterixCommand<?> command) {
        if (!executedCommands.offer(command)) {
            logger.debug("commands.size:" + executedCommands.size());
            logger.warn("RequestLog ignoring command after reaching limit of " + MAX_STORAGE);
        }
    }

    private void notifyPromises() {
        logger.debug("Notifying interested parties, partiesCount:" + promises.size());
        promises.stream().forEach(p -> p.success(getExecutedCommands()));
    }

    public void markWebRequestEnd() {
        logger.debug("WebRequest ends.");
        notifyPromises();
    }

    public Collection<HysterixCommand<?>> getExecutedCommands() {
        return Collections.unmodifiableCollection(executedCommands);
    }

    public F.Promise<Collection<HysterixCommand<?>>> executedCommands() {
        if (!hysterixContext.getHysterixSettings().isLogRequestStatistics()) {
            throw new HysterixException("Cannot inspect log, you have to enable request log inspect via hysterix settings");
        }
        scala.concurrent.Promise<Collection<HysterixCommand<?>>> promise =
                scala.concurrent.Promise$.MODULE$.<Collection<HysterixCommand<?>>>apply();

        promises.add(promise);

        final Future<Collection<HysterixCommand<?>>> future = promise.future();

        return F.Promise.wrap(future);
    }

}
