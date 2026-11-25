package org.fbreton.jsch;

import com.google.common.collect.Range;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Minimal unit tests for the moderator pattern.
 * Tests ModeratorImpl directly with plain Java objects.
 * JDK 25 compatible - no Mockito, no JSch, no ChannelSftp required.
 */
class SimpleJschPoolTest {

    private ModeratorImpl moderator;

    @BeforeEach
    void setUp() {
        moderator = new ModeratorImpl();
    }

    /**
     * Test ModeratorImpl.tryGrantTicket() grants ticket when permits available.
     * Note: The moderator validates unused tickets via the provided set,
     * but the caller (SimpleJschPool) is responsible for adding the ticket
     * to its inUseTickets set after generateTicket().
     */
    @Test
    void test_moderator_grants_ticket_when_permits_available() {
        // Given: moderator with 3 permits
        var ticketsProvider = (Supplier<Number>) () -> 1;
        var inUseTickets = new HashSet<Number>();
        var semaphore = new java.util.concurrent.Semaphore(3, true);

        // When: tryGrantTicket called
        var ticket = moderator.tryGrantTicket(
                ticketsProvider,
                inUseTickets,
                semaphore,
                3, // maxOpenedChannels
                100L // waitingTimeInMs
        );

        // Then: ticket should be granted (moderator validates unused ticket)
        Assertions.assertThat(ticket).isEqualTo(1);
        // And: semaphore should have 2 permits remaining (moderator acquired 1)
        Assertions.assertThat(semaphore.availablePermits()).isEqualTo(2);
        // And: the pool would add ticket to its inUseTickets after generateTicket()
        // (this is demonstrated in test_simple_jsch_pool_with_moderator)
    }

    /**
     * Test ModeratorImpl.tryGrantTicket() returns null when no permits available.
     */
    @Test
    void test_moderator_returns_null_when_no_permits() {
        // Given: moderator with 0 permits
        var ticketsProvider = (Supplier<Number>) () -> 1;
        var inUseTickets = new HashSet<Number>();
        var semaphore = new java.util.concurrent.Semaphore(0, true);

        // When: tryGrantTicket called
        var ticket = moderator.tryGrantTicket(
                ticketsProvider,
                inUseTickets,
                semaphore,
                3,
                100L
        );

        // Then: ticket should be null (no permits available)
        Assertions.assertThat(ticket).isNull();
        // And: semaphore should still have 0 permits
        Assertions.assertThat(semaphore.availablePermits()).isEqualTo(0);
    }

    /**
     * Test ModeratorImpl.releaseTicket() releases permit and removes from in-use set.
     * Note: The moderator releases the semaphore permit if the ticket was in use,
     * but the caller manages the inUseTickets set.
     */
    @Test
    void test_moderator_releases_ticket() {
        // Given: moderator with 1 ticket in use, semaphore with 1 permit
        var inUseTickets = new HashSet<Number>();
        inUseTickets.add(1);
        var semaphore = new java.util.concurrent.Semaphore(1, true);

        // When: releaseTicket called
        moderator.releaseTicket(1, inUseTickets, semaphore);

        // Then: ticket should be removed from in-use set
        Assertions.assertThat(inUseTickets).isEmpty();
        // And: semaphore should have 2 permits available (1 original + 1 released)
        Assertions.assertThat(semaphore.availablePermits()).isEqualTo(2);
    }

    /**
     * Test ModeratorImpl.isTicketInUse() checks if ticket is held.
     */
    @Test
    void test_moderator_is_ticket_in_use() {
        // Given: moderator with ticket 1 in use
        var inUseTickets = new HashSet<Number>();
        inUseTickets.add(1);

        // When: isTicketInUse called with ticket 1
        var result = moderator.isTicketInUse(1, inUseTickets);

        // Then: should return true
        Assertions.assertThat(result).isTrue();

        // And: isTicketInUse called with ticket 2 should return false
        var result2 = moderator.isTicketInUse(2, inUseTickets);
        Assertions.assertThat(result2).isFalse();
    }

    /**
     * Test ModeratorImpl.availableTickets() returns available permits.
     */
    @Test
    void test_moderator_available_tickets() {
        // Given: moderator with semaphore having 4 permits
        var semaphore = new java.util.concurrent.Semaphore(4, true);

        // When: availableTickets called
        var available = moderator.availableTickets(semaphore);

        // Then: should return 4
        Assertions.assertThat(available).isEqualTo(4);
    }

    /**
     * Test SimpleJschPool integration with moderator pattern.
     * Uses minimal test infrastructure - just verifies the pool
     * works with the moderator without needing JSch mocks.
     */
    @Test
    void test_simple_jsch_pool_with_moderator() {
        // Given: pool configured for 2 channels
        var config = new SimpleJschPool.PoolConfig(2, 100);
        var tickets = TicketsGenerator.of(10);

        // Create pool with moderator - the moderator pattern is now
        // explicit and centralized via PoolModerator interface
        jschPool = new SimpleJschPool(tickets::get,
                () -> null, config, moderator);

        // When: generate 2 tickets
        var t1 = jschPool.generateTicket();
        var t2 = jschPool.generateTicket();

        // Then: both tickets should be generated (not null)
        Assertions.assertThat(t1).isNotNull();
        Assertions.assertThat(t2).isNotNull();

        // And: no tickets should be available (both in use)
        Assertions.assertThat(jschPool.availableTickets()).isZero();

        // And: release one ticket
        jschPool.releaseTicket(t1);

        // And: one ticket should be available again
        Assertions.assertThat(jschPool.availableTickets()).isOne();
    }

    // Test field - declared at class level for use by multiple tests
    private SimpleJschPool jschPool;
}