package org.apache.bookkeeper.bookie.storage.ldb;

import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

class MyReadCacheTest {

    private static final Logger logger = LoggerFactory.getLogger(MyReadCacheTest.class);
    private static final String envFlag = System.getenv("flag");

    private static Stream<Arguments> putTestArguments() {
        logger.info(String.format("env: %s", envFlag));
        List<TestState> availableTestState = new ArrayList<>();
        List<Arguments> activeArguments = new ArrayList<>();

        Configuration firstFull = new Configuration(SegmentState.FULL, SegmentState.EMPTY);
        Configuration allEmpty = new Configuration(SegmentState.EMPTY, SegmentState.EMPTY);
        Configuration firstPartial = new Configuration(SegmentState.PARTIAL, SegmentState.EMPTY);
        Configuration allFull = new Configuration(SegmentState.FULL, SegmentState.FULL);

        availableTestState.add(new TestState(
                "#01: put in empty cache",
                allEmpty,
                CompositeIdState.NON_PRESENT, EntryState.LESS_EQUAL_THAN_ACTUAL_SEGMENT_CAPACITY_LOW,
                EnumSet.of(
                        ExpectedFlag.NEW_ENTRY_ADDED),
                false
        ));

        // availableTestState.add(new TestState(
        //         "#02: put on partially full segment",
        //         firstPartial,
        //         CompositeIdState.PRESENT, EntryState.LESS_EQUAL_THAN_ACTUAL_SEGMENT_CAPACITY_HIGH,
        //         EnumSet.of(
        //                 ExpectedFlag.NEW_ENTRY_UPDATED,
        //                 ExpectedFlag.PRIOR_ENTRIES_READABLE),
        //         false
        // ));
        //
        // availableTestState.add(new TestState(
        //         "#03: put of second segment, so it's partially full",
        //         firstPartial,
        //         CompositeIdState.NON_PRESENT, EntryState.LESS_EQUAL_THAN_ACTUAL_SEGMENT_CAPACITY_LOW,
        //         EnumSet.of(
        //                 ExpectedFlag.NEW_ENTRY_ADDED,
        //                 ExpectedFlag.PRIOR_ENTRIES_READABLE),
        //         false
        // ));
        //
        // availableTestState.add(new TestState(
        //         "#04: put of second segment, so it's full",
        //         firstFull,
        //         CompositeIdState.PRESENT, EntryState.LESS_EQUAL_THAN_ACTUAL_SEGMENT_CAPACITY_HIGH,
        //         EnumSet.of(
        //                 ExpectedFlag.NEW_ENTRY_UPDATED,
        //                 ExpectedFlag.PRIOR_ENTRIES_READABLE),
        //         false
        // ));
        //
        // availableTestState.add(new TestState(
        //         "#05: put with overwrite",
        //         allFull,
        //         CompositeIdState.NON_PRESENT, EntryState.LESS_EQUAL_THAN_ACTUAL_SEGMENT_CAPACITY_HIGH,
        //         EnumSet.of(
        //                 ExpectedFlag.NEW_ENTRY_ADDED,
        //                 ExpectedFlag.ONLY_SECOND_SEGMENT_ENTRIES_READABLE),
        //         false
        // ));
        //
        // availableTestState.add(new TestState(
        //         "#06: entry too big",
        //         firstPartial,
        //         CompositeIdState.NON_PRESENT, EntryState.GREATER_THAN_SEGMENT_SIZE,
        //         EnumSet.of(
        //                 ExpectedFlag.PRIOR_ENTRIES_READABLE),
        //         false
        // ));
        //
        // availableTestState.add(new TestState(
        //         "#07: put of null",
        //         firstFull,
        //         CompositeIdState.NON_PRESENT, EntryState.NULL,
        //         EnumSet.of(
        //                 ExpectedFlag.NULL_POINTER_EXCEPTION,
        //                 ExpectedFlag.PRIOR_ENTRIES_READABLE),
        //         false
        // ));


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

        testState.sut.put(testState.ledgerId, testState.entryId, testState.entry);

        for (MyReadCacheTestEntry expectedEntry : testState.expectedEntries) {
            int size = expectedEntry.content.writerIndex();
            ByteBuf actual = testState.sut.get(expectedEntry.ledgerId, expectedEntry.entryId);
            logger.info("actual:" + actual.toString());
            Assertions.assertEquals(
                    expectedEntry.content.internalNioBuffer(0, size),
                    actual.internalNioBuffer(0, size));
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
        GREATER_THAN_SEGMENT_SIZE
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
        int ledgerId, entryId;
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
