package org.apache.bookkeeper.bookie.storage.ldb;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.util.ArrayList;
import java.util.List;

public class MyReadCacheTestConfigurer {

    private static final int MAX_SEGMENT_SIZE = 256;
    private static final int SEGMENT_COUNT = 2;
    private static final int PARTIAL_SEGMENT_SIZE = MAX_SEGMENT_SIZE / 2;
    private static final int MAX_CACHE_SIZE = MAX_SEGMENT_SIZE * SEGMENT_COUNT;

    private static final int FIRST_SEGMENT_LEDGER = 1;
    private static final int SECOND_SEGMENT_LEDGER = 2;
    private static final int NON_PRESENT_LEDGER = 3;

    private int actualSegmentCapacity;

    private List<MyReadCacheTestEntry> addedEntries = new ArrayList<>();

    public void setup(MyReadCacheTest.TestState testState) {
        /* Configure the environment */
        this.configure(testState);

        /* Set up compositeId */
        long ledgerId, entryId;
        switch (testState.compositeIdState) {
            case NON_PRESENT:
                ledgerId = NON_PRESENT_LEDGER;
                entryId = 1;
                break;
            case PRESENT:
                if (addedEntries.get(0) == null) {
                    throw new IllegalStateException("Unexpected empty cache with PRESENT flag active.");
                }
                ledgerId = addedEntries.get(0).ledgerId;
                entryId = addedEntries.get(0).entryId;
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + testState.compositeIdState);
        }
        testState.ledgerId = ledgerId;
        testState.entryId = entryId;

        /* Set up entry */
        ByteBuf content;
        int size;
        switch (testState.entryState) {
            case LESS_EQUAL_THAN_ACTUAL_SEGMENT_CAPACITY_LOW:
                size = 1;
                content = getByteBuf(size, getByteFromId(ledgerId, entryId));
                break;
            case LESS_EQUAL_THAN_ACTUAL_SEGMENT_CAPACITY_HIGH:
                size = actualSegmentCapacity;
                content = getByteBuf(size, getByteFromId(ledgerId, entryId));
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + testState.entryState);
        }
        testState.entry = content;

        MyReadCacheTestEntry newEntry = new MyReadCacheTestEntry(ledgerId, entryId, content);

        /* Set up expectedEntries */
        List<MyReadCacheTestEntry> expectedEntries = testState.expectedEntries;
        if (testState.expectedState.contains(MyReadCacheTest.ExpectedFlag.NEW_ENTRY_ADDED)) {
            expectedEntries.add(newEntry);
        }
        if (testState.expectedState.contains(MyReadCacheTest.ExpectedFlag.NEW_ENTRY_UPDATED)) {
            MyReadCacheTestEntry found = null;
            for (MyReadCacheTestEntry entry : addedEntries) {
                if (entry.ledgerId == newEntry.ledgerId && entry.entryId == newEntry.entryId) {
                    found = entry;
                    break;
                }
            }
            // if found is null, then newEntry has not been found, and it's not expected
            if (found == null) throw new IllegalStateException("newEntry not found with NEW_ENTRY_UPDATED active.");

            // Update content of updated entry
            found.content = newEntry.content;
        }
    }

    private void configure(MyReadCacheTest.TestState testState) {
        /* Create the SUT */
        ReadCache sut;
        this.createSUT(testState);
        sut = testState.sut;

        /* Set up the state of the first segment */
        MyReadCacheTestEntry firstEntry;
        int firstSize;
        int ledgerId, entryId;
        switch (testState.configuration.segment1State) {
            case EMPTY:
                actualSegmentCapacity = MAX_SEGMENT_SIZE;
                return;
            case PARTIAL:
                firstSize = PARTIAL_SEGMENT_SIZE;
                actualSegmentCapacity = MAX_SEGMENT_SIZE - firstSize;
                // Put the entry in the sut
                ledgerId = FIRST_SEGMENT_LEDGER;
                entryId = 1;
                ByteBuf content = getByteBuf(firstSize, getByteFromId(ledgerId, entryId));
                sut.put(ledgerId, entryId, content);
                // Add the entry to the list of added entries
                firstEntry = new MyReadCacheTestEntry(ledgerId, entryId, content);
                addedEntries.add(firstEntry);
                return;
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

    private byte getByteFromId(long ledgerId, long entryId) {
        return (byte) (ledgerId * 10 + entryId);
    }
}
