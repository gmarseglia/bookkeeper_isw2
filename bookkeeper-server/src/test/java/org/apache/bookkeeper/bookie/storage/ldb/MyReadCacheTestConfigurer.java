package org.apache.bookkeeper.bookie.storage.ldb;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.util.ArrayList;
import java.util.List;

public class MyReadCacheTestConfigurer {

    private static final int MAX_SEGMENT_SIZE = 256;
    private static final int SEGMENT_COUNT = 2;
    private static final int MAX_CACHE_SIZE = MAX_SEGMENT_SIZE * SEGMENT_COUNT;

    private static final int NON_PRESENT_LEDGER = 1;

    private List<MyReadCacheTestEntry> addedEntries = new ArrayList<>();

    public void setup(MyReadCacheTest.TestState testState) {
        /* Configure the environment */
        this.configure(testState);

        /* Set up compositeId */
        int ledgerId, entryId;
        switch (testState.compositeIdState) {
            case NON_PRESENT:
                ledgerId = NON_PRESENT_LEDGER;
                entryId = 1;
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + testState.compositeIdState);
        }
        testState.ledgerId = ledgerId;
        testState.entryId = entryId;

        /* Set up entry */
        ByteBuf content;
        byte fill = (byte) (testState.ledgerId * 10 + testState.entryId);
        switch (testState.entryState) {
            case LESS_EQUAL_THAN_ACTUAL_SEGMENT_CAPACITY_LOW:
                content = getByteBuf(1, fill);
                testState.entry = content;
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + testState.entryState);
        }

        MyReadCacheTestEntry newEntry = new MyReadCacheTestEntry(ledgerId, entryId, content);

        /* Set up expectedEntries */
        List<MyReadCacheTestEntry> expectedEntries = testState.expectedEntries;
        if (testState.expectedState.contains(MyReadCacheTest.ExpectedFlag.NEW_ENTRY_ADDED)) {
            expectedEntries.add(newEntry);
        }
    }

    private void configure(MyReadCacheTest.TestState testState) {
        /* Create the SUT */
        ReadCache sut;
        this.createSUT(testState);
        sut = testState.sut;

        switch (testState.configuration.segment1State) {
            case EMPTY:
                return;
            case PARTIAL:
            case FULL:
                throw new IllegalStateException("#TODO: " + testState.configuration.segment1State);
            default:
                throw new IllegalStateException("Unexpected value: " + testState.configuration.segment1State);
        }
    }

    private void createSUT(MyReadCacheTest.TestState testState) {
        testState.sut = new ReadCache(ByteBufAllocator.DEFAULT, MAX_CACHE_SIZE, MAX_SEGMENT_SIZE);
    }

    private ByteBuf getByteBuf(int size, byte fill) {
        ByteBuf result = ByteBufAllocator.DEFAULT.buffer(size);
        for (int i = 0; i < size; i++) {
            result.writeByte(fill);
        }
        return result;
    }
}
