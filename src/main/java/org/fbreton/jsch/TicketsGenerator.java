package org.fbreton.jsch;

import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Value(staticConstructor = "of")
class TicketsGenerator implements Supplier<Number> {
    protected transient final AtomicInteger generator = new AtomicInteger(0);
    private final int maxTickets;

    public Number get() {
        return Math.abs(this.generator.incrementAndGet() % maxTickets);
    }
}
