package org.apache.bookkeeper.bookie.storage.ldb;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;

class MyReadCacheTest {

    private static final Logger logger = LoggerFactory.getLogger(MyReadCacheTest.class);
    private static final String envFlag = System.getenv("flag");

    private static final Configuration FIRST_FULL = new Configuration(SegmentState.FULL, SegmentState.EMPTY);
    private static final Configuration ALL_EMPTY = new Configuration(SegmentState.EMPTY, SegmentState.EMPTY);
    private static final Configuration FIRST_PARTIAL = new Configuration(SegmentState.PARTIAL, SegmentState.EMPTY);
    private static final Configuration SECOND_PARTIAL = new Configuration(SegmentState.FULL, SegmentState.PARTIAL);
    private static final Configuration ALL_FULL = new Configuration(SegmentState.FULL, SegmentState.FULL);

    private static Stream<Arguments> putTestArguments() {
        logger.info(String.format("env: %s", envFlag));
        List<TestState> availableTestState = new ArrayList<>();
        List<Arguments> activeArguments = new ArrayList<>();

        availableTestState.add(new TestState(
                "#01: put in empty cache",
                ALL_EMPTY,
                CompositeIdState.NON_PRESENT, EntryState.LESS_EQUAL_THAN_ACTUAL_SEGMENT_CAPACITY_LOW,
                EnumSet.of(
                        ExpectedFlag.NEW_ENTRY_ADDED),
                true
        ));

        availableTestState.add(new TestState(
                "#02: put in partially full segment",
                FIRST_PARTIAL,
                CompositeIdState.PRESENT, EntryState.LESS_EQUAL_THAN_ACTUAL_SEGMENT_CAPACITY_HIGH,
                EnumSet.of(
                        ExpectedFlag.NEW_ENTRY_UPDATED,
                        ExpectedFlag.PRIOR_ENTRIES_READABLE),
                true
        ));


        availableTestState.add(new TestState(
                "#03: put in second segment, so it's partially full",
                FIRST_PARTIAL,
                CompositeIdState.NON_PRESENT, EntryState.LESS_EQUAL_THAN_SEGMENT_SIZE_LOW,
                EnumSet.of(
                        ExpectedFlag.NEW_ENTRY_ADDED,
                        ExpectedFlag.PRIOR_ENTRIES_READABLE),
                true
        ));

        availableTestState.add(new TestState(
                "#04: put in second segment, so it's full",
                FIRST_FULL,
                CompositeIdState.PRESENT, EntryState.LESS_EQUAL_THAN_SEGMENT_SIZE_HIGH,
                EnumSet.of(
                        ExpectedFlag.NEW_ENTRY_UPDATED,
                        ExpectedFlag.PRIOR_ENTRIES_READABLE),
                true
        ));

        availableTestState.add(new TestState(
                "#05: put with overwrite",
                ALL_FULL,
                CompositeIdState.NON_PRESENT, EntryState.LESS_EQUAL_THAN_ACTUAL_SEGMENT_CAPACITY_HIGH,
                EnumSet.of(
                        ExpectedFlag.NEW_ENTRY_ADDED,
                        ExpectedFlag.ONLY_SECOND_SEGMENT_ENTRIES_READABLE),
                true
        ));

        availableTestState.add(new TestState(
                "#06: entry too big",
                FIRST_PARTIAL,
                CompositeIdState.NON_PRESENT, EntryState.GREATER_THAN_SEGMENT_SIZE,
                EnumSet.of(
                        ExpectedFlag.PRIOR_ENTRIES_READABLE),
                true
        ));

        availableTestState.add(new TestState(
                "#07: put of null",
                FIRST_FULL,
                CompositeIdState.NON_PRESENT, EntryState.NULL,
                EnumSet.of(
                        ExpectedFlag.NULL_POINTER_EXCEPTION,
                        ExpectedFlag.PRIOR_ENTRIES_READABLE),
                true
        ));

        availableTestState.add(new TestState(
                "#08: put of empty",
                FIRST_FULL,
                CompositeIdState.NON_PRESENT, EntryState.EMPTY,
                EnumSet.of(
                        ExpectedFlag.PRIOR_ENTRIES_READABLE,
                        ExpectedFlag.NEW_ENTRY_ADDED),
                true
        ));

        availableTestState.add(new TestState(
                "#10: put of exact size to avoid overwrite",
                SECOND_PARTIAL,
                CompositeIdState.NON_PRESENT, EntryState.LESS_EQUAL_THAN_ACTUAL_SEGMENT_CAPACITY_HIGH,
                EnumSet.of(
                        ExpectedFlag.PRIOR_ENTRIES_READABLE,
                        ExpectedFlag.NEW_ENTRY_ADDED),
                true
        ));


        for (TestState state : availableTestState) {
            if (!state.successful)
                if (("pitest".equals(envFlag) || "onlySuccess".equals(envFlag)))
                    continue;
            activeArguments.add(Arguments.of(state));
        }

        return activeArguments.stream();
    }

    private static Stream<Arguments> putConcurrentTestArguments() {
        logger.info(String.format("env: %s", envFlag));
        List<TestState> availableTestState = new ArrayList<>();
        List<Arguments> activeArguments = new ArrayList<>();

        availableTestState.add(new TestState(
                "#09: concurrent test",
                ALL_FULL,
                CompositeIdState.NON_PRESENT, EntryState.LESS_EQUAL_THAN_ACTUAL_SEGMENT_CAPACITY_LOW,
                EnumSet.of(
                        ExpectedFlag.PRIOR_ENTRIES_READABLE),
                true
        ));

        availableTestState.add(new TestState(
                "#11: concurrent test with exact size to avoid overwrite",
                ALL_FULL,
                CompositeIdState.NON_PRESENT, EntryState.HALF_OF_SEGMENT_SIZE,
                EnumSet.of(
                        ExpectedFlag.ONLY_SECOND_SEGMENT_ENTRIES_READABLE),
                true
        ));


        for (TestState state : availableTestState) {
            if (!state.successful)
                if (("pitest".equals(envFlag) || "onlySuccess".equals(envFlag)))
                    continue;
            activeArguments.add(Arguments.of(state));
        }

        return activeArguments.stream();
    }

    @ParameterizedTest
    @MethodSource("putTestArguments")
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void putTest(TestState testState) {
        logger.info(testState.description);

        MyReadCacheTestConfigurer configurer = new MyReadCacheTestConfigurer();
        configurer.setup(testState);

        String debugMsg = String.format(
                "ledgerId: %d, entryId: %d, entry.writerIndex(): %s",
                testState.ledgerId,
                testState.entryId,
                testState.entry == null ? "null" : testState.entry.writerIndex());
        logger.info(debugMsg);

        if (testState.expectedState.contains(ExpectedFlag.NULL_POINTER_EXCEPTION)) {
            Assertions.assertThrows(
                    NullPointerException.class,
                    () -> testState.sut.put(testState.ledgerId, testState.entryId, testState.entry));
        } else {
            testState.sut.put(testState.ledgerId, testState.entryId, testState.entry);
        }

        logger.info(String.format("# of assertEquals expected: %d", testState.expectedEntries.size()));
        for (MyReadCacheTestEntry expectedEntry : testState.expectedEntries) {
            int size = expectedEntry.content.writerIndex();
            ByteBuf actual = testState.sut.get(expectedEntry.ledgerId, expectedEntry.entryId);
            logger.info(String.format("actual: %s", actual.toString()));

            Assertions.assertEquals(
                    expectedEntry.content.internalNioBuffer(0, size),
                    actual.internalNioBuffer(0, size));

            actual.release();
        }
    }

    @ParameterizedTest
    @MethodSource("putConcurrentTestArguments")
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void putConcurrentTest(TestState testState) throws NoSuchFieldException, IllegalAccessException, InterruptedException {
        logger.info(testState.description);

        MyReadCacheTestConfigurer configurer = new MyReadCacheTestConfigurer();
        configurer.setup(testState);

        String debugMsg = String.format(
                "ledgerId: %d, entryId: %d, entry.writerIndex(): %s",
                testState.ledgerId,
                testState.entryId,
                testState.entry == null ? "null" : testState.entry.writerIndex());
        logger.info(debugMsg);

        /* Set up the mocked lock */
        ReentrantReadWriteLock spyReentrantReadWriteLock = spy(ReentrantReadWriteLock.class);
        ReentrantReadWriteLock.WriteLock mockWriteLock = mock(ReentrantReadWriteLock.WriteLock.class);

        // semaphore really implements the synchronization between the threads
        Semaphore semaphore = new Semaphore(1);
        semaphore.drainPermits();

        // The first thread to ask for the lock, will be blocked until the second has released it
        doAnswer(invocationOnMock -> {
            logger.info("1st wants to lock");
            semaphore.acquire();
            logger.info("1st has received");
            return null;
        }).doAnswer(invocationOnMock -> {
            logger.info("2nd wants to lock");
            logger.info("2nd has received");
            return null;
        }).when(mockWriteLock).lock();

        // first thread to release, will release it
        doAnswer(invocationOnMock -> {
            logger.info("2nd wants to unlock");
            semaphore.release();
            logger.info("2nd has released");
            return null;
        }).doAnswer(invocationOnMock -> {
            logger.info("1st wants to unlock");
            logger.info("1st has released");
            return null;
        }).when(mockWriteLock).unlock();

        // Mock the writeLock() method to return the mocker writeLock
        doReturn(mockWriteLock).when(spyReentrantReadWriteLock).writeLock();

        /* Mock the internal lock via reflection */
        ReadCache sut = testState.sut;
        Field lockField = ReadCache.class.getDeclaredField("lock");
        lockField.setAccessible(true);
        lockField.set(sut, spyReentrantReadWriteLock);

        /* Create the entries */
        long aEntryId = testState.entryId;
        long bEntryId = aEntryId + 1;
        int expectedSize = testState.entry.writerIndex();
        ByteBuf aEntry = Unpooled.buffer(expectedSize);
        ByteBuf bEntry = Unpooled.buffer(expectedSize);
        for (int i = 0; i < expectedSize; i++) {
            aEntry.writeByte((byte) (testState.ledgerId * 10 + aEntryId));
            bEntry.writeByte((byte) (testState.ledgerId * 10 + bEntryId));
        }
        testState.entry.release();

        /* Create and launch the threads */
        Thread threadA = new Thread(() -> sut.put(testState.ledgerId, aEntryId, aEntry));
        Thread threadB = new Thread(() -> sut.put(testState.ledgerId, bEntryId, bEntry));
        threadA.start();
        threadB.start();

        /* Wait for the thread to complete */
        threadB.join();
        threadA.join();

        /* Assert first */
        ByteBuf aEntryActual = sut.get(testState.ledgerId, aEntryId);
        Assertions.assertEquals(
                aEntry.internalNioBuffer(0, expectedSize),
                aEntryActual.internalNioBuffer(0, expectedSize)
        );
        aEntryActual.release();
        aEntry.release();

        /* Assert second */
        ByteBuf bEntryActual = sut.get(testState.ledgerId, bEntryId);
        Assertions.assertEquals(
                bEntry.internalNioBuffer(0, expectedSize),
                bEntryActual.internalNioBuffer(0, expectedSize)
        );
        bEntryActual.release();
        bEntry.release();

        /* Assert on the expectedEntries */
        logger.info(String.format("# of assertEquals expected: %d", testState.expectedEntries.size()));
        for (MyReadCacheTestEntry expectedEntry : testState.expectedEntries) {
            int size = expectedEntry.content.writerIndex();
            ByteBuf actual = testState.sut.get(expectedEntry.ledgerId, expectedEntry.entryId);
            logger.info(String.format("actual: %s", actual == null ? "null" : actual.toString()));

            if (expectedEntry.segment == 2) {
                Assertions.assertEquals(
                        expectedEntry.content.internalNioBuffer(0, size),
                        actual.internalNioBuffer(0, size));
                actual.release();
            } else {
                Assertions.assertNull(actual);
            }

            expectedEntry.content.release();
        }
    }

    public enum SegmentState {
        EMPTY, PARTIAL, FULL
    }

    public enum CompositeIdState {
        NON_PRESENT, PRESENT
    }

    public enum EntryState {
        NULL, EMPTY,
        LESS_EQUAL_THAN_ACTUAL_SEGMENT_CAPACITY_LOW, LESS_EQUAL_THAN_ACTUAL_SEGMENT_CAPACITY_HIGH,
        LESS_EQUAL_THAN_SEGMENT_SIZE_LOW, LESS_EQUAL_THAN_SEGMENT_SIZE_HIGH,
        GREATER_THAN_SEGMENT_SIZE,
        HALF_OF_SEGMENT_SIZE
    }

    public enum ExpectedFlag {
        NULL_POINTER_EXCEPTION,
        NEW_ENTRY_ADDED, NEW_ENTRY_UPDATED,
        PRIOR_ENTRIES_READABLE, ONLY_SECOND_SEGMENT_ENTRIES_READABLE
    }

    public static class Configuration {
        public SegmentState segment1State, segment2State;

        public Configuration(SegmentState segment1State, SegmentState segment2State) {
            this.segment1State = segment1State;
            this.segment2State = segment2State;
        }
    }

    public static class TestState {
        String description;
        Configuration configuration;
        CompositeIdState compositeIdState;
        EntryState entryState;
        EnumSet<ExpectedFlag> expectedState;
        boolean successful;

        ReadCache sut;
        long ledgerId, entryId;
        ByteBuf entry;
        List<MyReadCacheTestEntry> expectedEntries = new ArrayList<>();

        public TestState(String description, Configuration configuration, CompositeIdState compositeIdState, EntryState entryState, EnumSet<ExpectedFlag> expectedState, boolean successful) {
            this.description = description;
            this.configuration = configuration;
            this.compositeIdState = compositeIdState;
            this.entryState = entryState;
            this.expectedState = expectedState;
            this.successful = successful;
        }
    }

}
