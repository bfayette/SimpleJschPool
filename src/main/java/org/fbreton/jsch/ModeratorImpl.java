package org.fbreton.jsch;

import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * Concrete moderator implementation implementing the PoolModerator interface.
 * <p>
 * This centralizes the access control logic that was previously scattered:
 * - semaphore.acquire() in generateTicket()
 * - inUseTickets.contains() checks in releaseTicket() and generateTicket()
 * - filter logic to find unused tickets
 * <p>
 * Hard truth: without this moderator, the access control flow is implicit
 * and requires understanding the interplay between semaphore + inUseTickets
 * across multiple methods. Now it's explicit and localized.
 */
public class ModeratorImpl implements PoolModerator {

    @Override
    public Number tryGrantTicket(Supplier<Number> ticketsProvider,
                                  Set<Number> inUseTickets,
                                  Semaphore semaphore,
                                  int maxOpenedChannels,
                                  long waitingTimeInMs) {
        boolean enter;
        try {
            enter = semaphore.tryAcquire(waitingTimeInMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for ticket", e);
        }
        if (!enter) {
            return null;
        }

        try {
            return IntStream.of(0, maxOpenedChannels)
                    .mapToObj(attempt -> ticketsProvider.get())
                    .filter(Predicate.not(inUseTickets::contains))
                    .findFirst()
                    .orElseThrow();
        } catch (RuntimeException exception) {
            semaphore.release();
            throw exception;
        }
    }

    @Override
    public void releaseTicket(Number key, Set<Number> inUseTickets, Semaphore semaphore) {
        if (inUseTickets.contains(key)) {
            semaphore.release();
        }
        inUseTickets.remove(key);
    }

    @Override
    public boolean isTicketInUse(Number key, Set<Number> inUseTickets) {
        return inUseTickets.contains(key);
    }

    @Override
    public int availableTickets(Semaphore semaphore) {
        return semaphore.availablePermits();
    }
}