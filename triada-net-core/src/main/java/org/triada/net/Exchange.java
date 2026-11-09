package org.triada.net;

/**
 * The telephone exchange of a TRIADA-NET server (M17): it decides what
 * happens when a caller dials a number.
 */
public interface Exchange {

    Answer dial(String number, Caller caller);
}
