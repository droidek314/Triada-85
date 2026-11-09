package org.triada.net;

/** The in-memory store keeps the store contract (M18). */
class MemoryNetStoreTest extends NetStoreContract {

    @Override
    protected NetStore store() {
        return new MemoryNetStore();
    }
}
