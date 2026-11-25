package com.tracci.module.trip.support;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AfterCommitTest {

    @Test
    void runsImmediatelyWhenNoTransactionIsActive() {
        AtomicInteger calls = new AtomicInteger();

        AfterCommit.run(calls::incrementAndGet);

        assertEquals(1, calls.get());
    }
}
