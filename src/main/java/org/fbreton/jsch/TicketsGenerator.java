package org.fbreton.jsch;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class TicketsGenerator implements Supplier<Number> {
    protected final AtomicInteger generator = new AtomicInteger(0);
    private final int maxTickets;

    public TicketsGenerator(int maxTickets) {
        this.maxTickets = maxTickets;
    }

    @Override
    public Number get() {
        return Math.abs(this.generator.incrementAndGet() % maxTickets);
    }

    public static TicketsGenerator of(int maxTickets) {
        return new TicketsGenerator(maxTickets);
    }
}