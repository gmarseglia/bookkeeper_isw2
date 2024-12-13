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

import java.util.stream.Stream;

@RunWith(Parameterized.class)
class MyReadCacheTest {

    private static final Logger logger = LoggerFactory.getLogger(MyReadCacheTest.class);
    private static final int MAX_CACHE_SIZE = 10 * 1024;


    private static Stream<Arguments> putAndGetArguments() {
        return Stream.of(
                Arguments.of((Object) null, (Object) null),
                Arguments.of("", ""),
                Arguments.of("test", "test")
        );
    }

    @ParameterizedTest
    @MethodSource("putAndGetArguments")
    void putAndGet(String inputString, String expectedString) {

        try (ReadCache sut = new ReadCache(UnpooledByteBufAllocator.DEFAULT, MAX_CACHE_SIZE)) {

            ByteBuf inputBuf;
            if (inputString != null) {
                inputBuf = Unpooled.wrappedBuffer(inputString.getBytes());
                sut.put(1, 1, inputBuf);
            } else {
                inputBuf = null;
                Assertions.assertThrows(NullPointerException.class, () -> sut.put(1,1, inputBuf));
                return;
            }

            ByteBuf expectedBuf = null;
            if (expectedString != null) {
                expectedBuf = Unpooled.wrappedBuffer(expectedString.getBytes());
            }

            Assertions.assertEquals(expectedBuf, sut.get(1, 1));
        }
    }

}
