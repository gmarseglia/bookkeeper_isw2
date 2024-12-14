package org.apache.bookkeeper.bookie.storage.ldb;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@RunWith(Parameterized.class)
class MyReadCacheTest {

    private static final Logger logger = LoggerFactory.getLogger(MyReadCacheTest.class);
    private static final int MAX_CACHE_SIZE = 10 * 1024;
    private static final int MAX_SEGMENT_SIZE = 1024;
    private static final int SEGMENT_NUM = MAX_CACHE_SIZE / MAX_SEGMENT_SIZE;

    private static String buildStringOfLength(int n, char c) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < n; i++) {
            builder.append(c);
        }
        return builder.toString();
    }

    private static Stream<Arguments> putTestArguments() {
        Entry nullEntry = new Entry(1, 1, null);
        Entry emptyEntry = new Entry(1, 1, "");
        String leThanSS = buildStringOfLength(MAX_SEGMENT_SIZE, 'a');
        Entry leThanSSEntry = new Entry(1, 1, leThanSS);

        String gtThanSS = buildStringOfLength(MAX_SEGMENT_SIZE + 1, 'a');
        Entry gtThanSSEntry = new Entry(1, 1, gtThanSS, null);

        Entry fillerEntry = new Entry(2, 1, buildStringOfLength(MAX_SEGMENT_SIZE, 'a'));

        State writeCurrentState = new State(StateType.WRITE_CURRENT);
        State writeNextState = new State(StateType.WRITE_NEXT);
        writeNextState.addEntry(fillerEntry);

        State overwriteDenseState = new State(StateType.OVERWRITE_DENSE);

        int density = 2;
        for (int entryId = 1; entryId <= SEGMENT_NUM * density; entryId++) {
            fillerEntry = new Entry(2, entryId, buildStringOfLength(MAX_SEGMENT_SIZE / density, 'a'));
            if (entryId <= density)
                fillerEntry.expected = null;
            overwriteDenseState.addEntry(fillerEntry);
        }

        return Stream.of(
                Arguments.of("1", writeCurrentState, nullEntry),
                Arguments.of("2", writeCurrentState, emptyEntry),
                Arguments.of("3.1", writeCurrentState, leThanSSEntry),
                Arguments.of("3.2", writeNextState, leThanSSEntry),
                Arguments.of("3.3", overwriteDenseState, leThanSSEntry),
                Arguments.of("4", writeCurrentState, gtThanSSEntry)
        );
    }

    private ByteBuf byteBufFromEntryContent(Entry entry) {
        return Unpooled.wrappedBuffer(entry.content.getBytes());
    }

    private ByteBuf byteBufFromEntryExpected(Entry entry) {
        if (entry.expected == null)
            return null;
        return Unpooled.wrappedBuffer(entry.expected.getBytes());
    }


    private void sutPutEntry(ReadCache sut, Entry entry) {
        sut.put(entry.ledgerId, entry.entryId, byteBufFromEntryContent(entry));
    }

    private ByteBuf sutGetEntry(ReadCache sut, Entry entry) {
        return sut.get(entry.ledgerId, entry.entryId);
    }

    @ParameterizedTest
    @MethodSource("putTestArguments")
    void putTest(String testID, State state, Entry testEntry) {
        logger.info(String.format("Test: #%s", testID));

        // Create the ReadCache object
        try (ReadCache sut = new ReadCache(UnpooledByteBufAllocator.DEFAULT, MAX_CACHE_SIZE, MAX_SEGMENT_SIZE)) {
            String inputString = testEntry.content;
            ByteBuf inputBuf;

            // Check the "entry == null" case
            if (inputString == null) {
                inputBuf = null;
                Assertions.assertThrows(NullPointerException.class, () -> sut.put(1, 1, inputBuf));
                return;
            }

            // Put all the entries needed to reach the state configuration
            if (state.type != StateType.WRITE_CURRENT) {
                for (Entry entry : state.entryList) {
                    sutPutEntry(sut, entry);
                }
            }

            // Put the test entry
            sutPutEntry(sut, testEntry);

            // Check if the test entry has been put correctly
            ByteBuf expectedBuf = byteBufFromEntryExpected(testEntry);
            ByteBuf actualBuf = sutGetEntry(sut, testEntry);
            Assertions.assertEquals(expectedBuf, actualBuf);

            // Check if the entries needed to reach the state configuration have been put correctly
            ByteBuf prevExpectedBuf, prevActualBuf;
            if (state.type != StateType.WRITE_CURRENT) {
                for (Entry entry : state.entryList) {
                    prevExpectedBuf = byteBufFromEntryExpected(entry);
                    prevActualBuf = sutGetEntry(sut, entry);
                    Assertions.assertEquals(prevExpectedBuf, prevActualBuf);
                }
            }

        }
    }

    protected enum StateType {
        WRITE_CURRENT, WRITE_NEXT, OVERWRITE_DENSE,
    }

    protected static class Entry {
        protected final int ledgerId;
        protected final int entryId;
        protected final String content;
        protected String expected;

        public Entry(int ledgerId, int entryId, String content) {
            this.ledgerId = ledgerId;
            this.entryId = entryId;
            this.content = content;
            this.expected = content;
        }

        public Entry(int ledgerId, int entryId, String content, String expected) {
            this.ledgerId = ledgerId;
            this.entryId = entryId;
            this.content = content;
            this.expected = expected;
        }
    }

    protected static class State {
        protected final StateType type;
        protected List<Entry> entryList = null;

        public State(StateType type) {
            this.type = type;
        }

        public void addEntry(Entry entry) {
            if (this.entryList == null) {
                this.entryList = new ArrayList<>();
            }
            this.entryList.add(entry);
        }
    }

}
