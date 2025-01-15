package org.apache.bookkeeper.bookie.storage.ldb;

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
