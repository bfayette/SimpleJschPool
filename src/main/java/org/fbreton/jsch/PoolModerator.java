package org.fbreton.jsch;

import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Moderator pattern for ticket-based access control.
 * <p>
 * The moderator explicitly validates and oversees ticket granting,
 * making the access control flow obvious and centralized rather than
 * scattered across semaphore + in-use set checks.
 * <p>
 * Hard truth: without an explicit moderator, access control becomes
 * implicit and error-prone (semaphore + set checks split across methods).
 * This pattern centralizes the moderation logic.
 */
public interface PoolModerator {

    /**
     * Attempt to grant a ticket. The moderator decides if access is allowed.
     * Returns the ticket if granted, or null if denied/timeout.
     *
     * @param ticketsProvider supplier of ticket numbers
     * @param inUseTickets    current set of in-use tickets
     * @param semaphore       concurrency limiter
     * @param maxOpenedChannels maximum concurrent channels
     * @param waitingTimeInMs max wait time for a permit
     * @return granted ticket or null if access denied
     */
    Number tryGrantTicket(Supplier<Number> ticketsProvider,
                          Set<Number> inUseTickets,
                          Semaphore semaphore,
                          int maxOpenedChannels,
                          long waitingTimeInMs);

    /**
     * Release a ticket back to the pool.
     * The moderator handles semaphore release and ticket removal.
     *
     * @param key         ticket to release
     * @param inUseTickets current set of in-use tickets
     * @param semaphore   concurrency limiter
     */
    void releaseTicket(Number key, Set<Number> inUseTickets, Semaphore semaphore);

    /**
     * Check if a ticket is currently in use.
     *
     * @param key ticket to check
     * @param inUseTickets current set of in-use tickets
     * @return true if the ticket is in use
     */
    boolean isTicketInUse(Number key, Set<Number> inUseTickets);

    /**
     * Get the number of available tickets/permits.
     *
     * @param semaphore concurrency limiter
     * @return available permits
     */
    int availableTickets(Semaphore semaphore);
}