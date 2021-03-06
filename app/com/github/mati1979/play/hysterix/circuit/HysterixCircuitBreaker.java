package com.github.mati1979.play.hysterix.circuit;

/**
 * Created by mati on 17/06/2014.
 */
public interface HysterixCircuitBreaker {

    public static final Stub NULL = new Stub();

    boolean allowRequest();

    boolean isOpen();

    boolean allowSingleTest();

    void markSuccess();

    class Stub implements HysterixCircuitBreaker {

        @Override
        public boolean allowRequest() {
            return true;
        }

        @Override
        public boolean isOpen() {
            return false;
        }

        @Override
        public boolean allowSingleTest() {
            return true;
        }

        @Override
        public void markSuccess() {
        }

    }

}
