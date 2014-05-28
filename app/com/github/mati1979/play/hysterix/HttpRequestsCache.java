package com.github.mati1979.play.hysterix;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import play.libs.F;

import javax.annotation.concurrent.NotThreadSafe;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Created by mati on 24/05/2014.
 */
@NotThreadSafe
public class HttpRequestsCache<T> {

    private Map<String, ClientsGroup> cache = Maps.newHashMap();

    private Map<String, String> requestIdsToGroupIds = Maps.newHashMap();

    public HttpRequestsCache() {
    }

    public HttpRequestsCache addRequest(final String requestId, final HysterixCommand<T> command) {
        if (shouldNotCache(command)) {
            return this;
        }

        final String clientsGroupId = command.getCacheKey().get();
        requestIdsToGroupIds.put(requestId, clientsGroupId);

        final ClientsGroup clientsGroup = getOrCreateClientsGroup(clientsGroupId);

        clientsGroup.lazyProxyPromises.put(requestId, createLazyPromise());
        clientsGroup.hystrixCommands.add(command);

        cache.put(clientsGroupId, clientsGroup);

        return this;
    }

    private boolean isRequestCachingEnabled(final HysterixCommand<T> command) {
        final HysterixSettings hysterixSettings = command.getHysterixSettings();

        return hysterixSettings.isRequestCacheEnabled() && command.getCacheKey().isPresent();
    }

    private boolean shouldNotCache(final HysterixCommand<T> command) {
        return !command.getCacheKey().isPresent() && !command.getHysterixSettings().isRequestCacheEnabled();
    }

    public HttpRequest createRequest(final HysterixCommand command) {
        return new HttpRequest(this, command);
    }

    public F.Promise<T> execute(final String requestId) {
        final String clientsGroupId = requestIdsToGroupIds.get(requestId);

        final ClientsGroup clientsGroup = getOrCreateClientsGroup(clientsGroupId);

        if (clientsGroup.realPromise.isPresent()) {
            return clientsGroup.getLazyPromise(requestId);
        }

        return realGet(requestId, clientsGroup);
    }

    private ClientsGroup getOrCreateClientsGroup(final String clientGroupId) {
        return cache.getOrDefault(clientGroupId, new ClientsGroup(clientGroupId));
    }

    private F.Promise<T> realGet(final String requestId, final ClientsGroup clientsGroup) {
        if (clientsGroup.hystrixCommands.isEmpty()) {
            return F.Promise.throwing(new RuntimeException("You must first enqueue a holder via addRequest method!"));
        }

        final HysterixCommand<T> next = clientsGroup.hystrixCommands.iterator().next();
        clientsGroup.stopwatch.start();
        final F.Promise<T> realResponseP = next.run();
        clientsGroup.realPromise = Optional.of(realResponseP);

        realResponseP.onRedeem(response -> clientsGroup.redeemSuccess(response));
        realResponseP.onFailure(t -> clientsGroup.redeemFailure(t));

        cache.put(clientsGroup.groupId, clientsGroup);

        return clientsGroup.getLazyPromise(requestId);
    }

    private scala.concurrent.Promise createLazyPromise() {
        return scala.concurrent.Promise$.MODULE$.<T>apply();
    }

    private class ClientsGroup {

        private final String groupId;
        private Optional<F.Promise<T>> realPromise = Optional.empty();
        private Collection<HysterixCommand<T>> hystrixCommands = Lists.newArrayList();
        private Map<String, scala.concurrent.Promise<T>> lazyProxyPromises = Maps.newHashMap();
        private boolean isCompleted = false;

        private Stopwatch stopwatch = Stopwatch.createUnstarted();

        private ClientsGroup(final String groupId) {
            this.groupId = groupId;
        }

        public F.Promise<T> getLazyPromise(final String requestId) {
            return F.Promise.<T>wrap(asScalaPromise(requestId).future());
        }

        public boolean isCompleted() {
            return isCompleted;
        }

        private void redeemSuccess(final T data) {
            isCompleted = true;
            stopwatch.stop();
            lazyProxyPromises.values().stream().forEach(p -> p.success(data));
        }

        private void redeemFailure(final Throwable t) {
            isCompleted = true;
            stopwatch.stop();
            lazyProxyPromises.values().stream().forEach(p -> p.failure(t));
        }

        private scala.concurrent.Promise<T> asScalaPromise(final String requestId) {
            return lazyProxyPromises.get(requestId);
        }

    }

}
