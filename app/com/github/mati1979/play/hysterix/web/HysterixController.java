package com.github.mati1979.play.hysterix.web;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.mati1979.play.hysterix.HysterixContext;
import com.github.mati1979.play.hysterix.event.HysterixStatisticsEvent;
import com.google.common.collect.Lists;
import com.google.common.eventbus.Subscribe;
import play.libs.EventSource;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by mati on 06/06/2014.
 */
public class HysterixController extends Controller {

    private static final play.Logger.ALogger logger = play.Logger.of(HysterixController.class);

    private final HysterixContext hysterixContext;
    private List<EventSource> activeEventSources;

    public HysterixController(final HysterixContext hysterixContext) {
        this.hysterixContext = hysterixContext;
        activeEventSources = Collections.synchronizedList(Lists.newArrayList());
        hysterixContext.getEventBus().register(new Subscriber());
    }

    public Result index() {
        final EventSource eventSource = new EventSource() {
            @Override
            public void onConnected() {
                activeEventSources = activeEventSources.stream().filter(eventS -> eventS != null).collect(Collectors.toList());
                if (activeEventSources.size() > 1000) {
                    logger.warn("activeEventSources over 1000, possibly memory leak!");
                }
                activeEventSources.add(this);
                onDisconnected(() -> activeEventSources.remove(this));
            }
        };

        return ok(eventSource);
    }

    public Result clearActiveEventSources() {
        activeEventSources.clear();

        return ok(String.valueOf(activeEventSources.size() == 0));
    }

    private class Subscriber {

        @Subscribe
        public void onEvent(final HysterixStatisticsEvent event) {
            final ObjectNode data = Json.newObject();

            data.put("type", "HystrixCommand");
            data.put("name", event.getEvent().getHysterixCommand().getCommandKey());
            data.put("group", (String) event.getEvent().getHysterixCommand().getCommandGroupKey().orElse(""));
            data.put("currentTime", event.getEvent().getCurrentTime());
            data.put("errorPercentage", event.getStats().getErrorPercentage());
            data.put("isCircuitBreakerOpen", false);
            data.put("errorCount", event.getStats().getErrorCount());
            data.put("requestCount", event.getStats().getTotalCount());
            data.put("rollingCountCollapsedRequests", 0); //TODO in my case response from cache is a collapsed one?
            data.put("rollingCountExceptionsThrown", event.getStats().getRollingCountExceptionsThrown());
            data.put("rollingCountFailure", event.getStats().getRollingCountFailure());
            data.put("rollingCountFallbackFailure", event.getStats().getRollingCountFailure());
            data.put("rollingCountFallbackRejection", 0);
            data.put("rollingCountFallbackSuccess", event.getStats().getRollingCountFallbackSuccess());
            data.put("rollingCountResponsesFromCache", event.getStats().getRollingCountResponsesFromCache());
            data.put("rollingCountSemaphoreRejected", 0);
            data.put("rollingCountShortCircuited", 0);
            data.put("rollingCountSuccess", event.getStats().getRollingSuccessWithoutRequestCache());
            data.put("rollingCountThreadPoolRejected", 0);
            data.put("rollingCountTimeout", event.getStats().getRollingTimeoutCount());
            data.put("currentConcurrentExecutionCount", 0);
            data.put("latencyExecute_mean", event.getStats().getAverageExecutionTime());

            final ObjectNode percentiles = Json.newObject();
            percentiles.put("0", event.getStats().getAverageExecutionTimePercentile(0.0D));
            percentiles.put("25", event.getStats().getAverageExecutionTimePercentile(0.25D));
            percentiles.put("50", event.getStats().getAverageExecutionTimePercentile(0.50D));
            percentiles.put("75", event.getStats().getAverageExecutionTimePercentile(0.75D));
            percentiles.put("90", event.getStats().getAverageExecutionTimePercentile(0.90D));
            percentiles.put("95", event.getStats().getAverageExecutionTimePercentile(0.95D));
            percentiles.put("99", event.getStats().getAverageExecutionTimePercentile(0.99D));
            percentiles.put("99.5", event.getStats().getAverageExecutionTimePercentile(0.995D));
            percentiles.put("100", event.getStats().getAverageExecutionTimePercentile(1.0D));

            data.put("latencyExecute", percentiles);

            data.put("latencyTotal_mean", event.getStats().getAverageExecutionTime());
            data.put("latencyTotal", percentiles);

            data.put("propertyValue_circuitBreakerRequestVolumeThreshold", 0);
            data.put("propertyValue_circuitBreakerSleepWindowInMilliseconds", 0);
            data.put("propertyValue_circuitBreakerErrorThresholdPercentage", 0);
            data.put("propertyValue_circuitBreakerForceOpen", false);
            data.put("propertyValue_circuitBreakerForceClosed", false);
            data.put("propertyValue_circuitBreakerEnabled", false);
            data.put("propertyValue_executionIsolationStrategy", "THREAD");
            data.put("propertyValue_executionIsolationThreadTimeoutInMilliseconds", "2000");
            data.put("propertyValue_executionIsolationThreadInterruptOnTimeout", true);
            data.putNull("propertyValue_executionIsolationThreadPoolKeyOverride");
            data.put("propertyValue_executionIsolationSemaphoreMaxConcurrentRequests", 20);
            data.put("propertyValue_fallbackIsolationSemaphoreMaxConcurrentRequests", 20);
            data.put("propertyValue_metricsRollingStatisticalWindowInMilliseconds", hysterixContext.getHysterixSettings().getRollingTimeWindowIntervalInMs());
            data.put("propertyValue_requestCacheEnabled", hysterixContext.getHysterixSettings().isRequestCacheEnabled());
            data.put("propertyValue_requestLogEnabled", hysterixContext.getHysterixSettings().isLogRequestStatistics());
            data.put("reportingHosts", 1);
            activeEventSources.stream().filter(eventSource -> eventSource != null).forEach(eventSource -> eventSource.send(EventSource.Event.event(data)));
        }

    }

}
