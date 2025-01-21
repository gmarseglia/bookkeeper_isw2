package org.apache.bookkeeper.bookie.storage.ldb;

import io.netty.buffer.ByteBuf;

public class MyReadCacheTestEntry {
    long ledgerId, entryId;
    ByteBuf content;
    int segment = -1;

    public MyReadCacheTestEntry(long ledgerId, long entryId, ByteBuf content) {
        this.ledgerId = ledgerId;
        this.entryId = entryId;
        this.content = content;
    }
}
