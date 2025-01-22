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

    public final List<MyReadCacheTestEntry> addedEntries = new ArrayList<>();
    private int actualSegmentCapacity;

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
                break;
            case LESS_EQUAL_THAN_ACTUAL_SEGMENT_CAPACITY_HIGH:
                size = actualSegmentCapacity;
                break;
            case LESS_EQUAL_THAN_SEGMENT_SIZE_LOW:
                size = actualSegmentCapacity + 1;
                break;
            case LESS_EQUAL_THAN_SEGMENT_SIZE_HIGH:
                size = MAX_SEGMENT_SIZE;
                break;
            case GREATER_THAN_SEGMENT_SIZE:
                size = MAX_SEGMENT_SIZE + 1;
                break;
            case NULL:
                size = -1;
                break;
            case EMPTY:
                size = 0;
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + testState.entryState);
        }
        content = getByteBuf(size, getByteFromId(ledgerId, entryId));
        testState.entry = content;

        MyReadCacheTestEntry newEntry = new MyReadCacheTestEntry(ledgerId, entryId, content);

        /* Set up expectedEntries */
        List<MyReadCacheTestEntry> expectedEntries = testState.expectedEntries;
        // Add newEntry to expectedEntries
        if (testState.expectedState.contains(MyReadCacheTest.ExpectedFlag.NEW_ENTRY_ADDED)) {
            expectedEntries.add(newEntry);
        }
        // Update newEntry content and add it to expectedEntries
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

            // Add newEntry to the list of expected entries
            expectedEntries.add(found);
        }
        // Add all entries in addedEntries different from newEntry to expectedEntries
        if (testState.expectedState.contains(MyReadCacheTest.ExpectedFlag.PRIOR_ENTRIES_READABLE)) {
            for (MyReadCacheTestEntry entry : addedEntries) {
                // Only added entries different from newEntry
                if (entry.ledgerId != newEntry.ledgerId || entry.entryId != newEntry.entryId) {
                    expectedEntries.add(entry);
                }
            }
        }
        if (testState.expectedState.contains(MyReadCacheTest.ExpectedFlag.ONLY_SECOND_SEGMENT_ENTRIES_READABLE)) {
            for (MyReadCacheTestEntry entry : addedEntries) {
                // Only added entries different from newEntry
                if (entry.ledgerId != newEntry.ledgerId || entry.entryId != newEntry.entryId) {
                    // Only entries from second segment
                    if (entry.segment == 2)
                        expectedEntries.add(entry);
                }
            }
        }

    }

    private void configure(MyReadCacheTest.TestState testState) {
        /* Create the SUT */
        ReadCache sut;
        this.createSUT(testState);
        sut = testState.sut;

        /* Set up the state of the first segment */
        Integer firstSize = null;
        boolean concludeConfiguration;
        switch (testState.configuration.segment1State) {
            case EMPTY:
                actualSegmentCapacity = MAX_SEGMENT_SIZE;
                concludeConfiguration = true;
                break;
            case PARTIAL:
                firstSize = PARTIAL_SEGMENT_SIZE;
                concludeConfiguration = true;
                break;
            case FULL:
                firstSize = MAX_SEGMENT_SIZE;
                concludeConfiguration = false;
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + testState.configuration.segment1State);
        }

        if (firstSize != null) {
            MyReadCacheTestEntry firstEntry;
            long ledgerId, entryId;
            actualSegmentCapacity = MAX_SEGMENT_SIZE - firstSize;

            // Put the entry in the sut
            ledgerId = FIRST_SEGMENT_LEDGER;
            entryId = 1;
            ByteBuf content = getByteBuf(firstSize, getByteFromId(ledgerId, entryId));
            sut.put(ledgerId, entryId, content);

            // Add the entry to the list of added entries
            firstEntry = new MyReadCacheTestEntry(ledgerId, entryId, content);
            addedEntries.add(firstEntry);
        }

        if (concludeConfiguration) return;

        /* Set up the state of the second segment */
        Integer secondSize = null;
        switch (testState.configuration.segment2State) {
            case EMPTY:
                actualSegmentCapacity = MAX_SEGMENT_SIZE;
                break;
            case FULL:
                secondSize = MAX_SEGMENT_SIZE;
                actualSegmentCapacity = MAX_SEGMENT_SIZE;
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + testState.configuration.segment2State);
        }

        if (secondSize != null) {
            MyReadCacheTestEntry secondEntry;
            long ledgerId, entryId;
            actualSegmentCapacity = MAX_SEGMENT_SIZE - secondSize;
            // This is for when second segment it's full so next segment is used
            if (actualSegmentCapacity == 0) actualSegmentCapacity = MAX_SEGMENT_SIZE;

            // Put the entry in the sut
            ledgerId = SECOND_SEGMENT_LEDGER;
            entryId = 1;
            ByteBuf content = getByteBuf(secondSize, getByteFromId(ledgerId, entryId));
            sut.put(ledgerId, entryId, content);

            // Add the entry to the list of added entries
            secondEntry = new MyReadCacheTestEntry(ledgerId, entryId, content);
            secondEntry.segment = 2;
            addedEntries.add(secondEntry);
        }
    }

    private void createSUT(MyReadCacheTest.TestState testState) {
        testState.sut = new ReadCache(ByteBufAllocator.DEFAULT, MAX_CACHE_SIZE, MAX_SEGMENT_SIZE);
    }

    private ByteBuf getByteBuf(int size, byte fill) {
        if (size < 0) return null;

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
