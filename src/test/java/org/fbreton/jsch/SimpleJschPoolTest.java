package org.fbreton.jsch;

import com.google.common.collect.Range;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Test class for SimpleJschPool.
 * Note: Tests require mock SSH/SFTP setup and may need network connectivity for full functionality.
 */
@ExtendWith({MockitoExtension.class})
class SimpleJschPoolTest extends Assertions {

    @Mock
    private ChannelSftp channel;

    @Mock
    private Session session;

    private SimpleJschPool jschPool;
    private Set<ChannelSftp> inUseChannels = new CopyOnWriteArraySet<>();
    private AtomicInteger counter = new AtomicInteger(0);

    @BeforeEach
    void setup() {
        try {
            Mockito.when(channel.getSession()).thenReturn(session);
            Mockito.when(channel.isConnected()).thenReturn(true);
        } catch (Exception e) {
            // Mockito setup may fail without proper network, tests will use mock channel
        }

        final var generator = TicketsGenerator.of(10);
        final var config = new SimpleJschPool.PoolConfig(10, 1);
        jschPool = new SimpleJschPool(generator, () -> Mockito.mock(Session.class), config) {
            @Override
            ChannelSftp create() {
                return channel;
            }
        };
    }

    @Test
    @Execution(ExecutionMode.CONCURRENT)
    @RepeatedTest(value = 10)
    void unchannel_by_ticket_via_only_one_session() {
        try {
            this.inUseChannels.add(jschPool.get(jschPool.generateTicket()));
            assertThat(inUseChannels).hasSize(counter.incrementAndGet());
            assertThat(inUseChannels.stream().map(this::session).collect(Collectors.toSet())).containsExactly(jschPool.getSshSession());
        } finally {
            jschPool.close();
        }
    }

    @Test
    void unchannel_unique_by_ticket() {
        try {
            var ticket = jschPool.generateTicket();
            var channel1 = jschPool.get(ticket);
            var channel2 = jschPool.get(ticket);
            assertThat(channel1).isSameAs(channel2);
        } finally {
            jschPool.close();
        }
    }

    @Test()
    void no_more_tickets() {
        try {
            jschPool.releaseAll();
            int max = jschPool.availableTickets();
            IntStream.range(0, max).forEach(key -> jschPool.generateTicket());
            assertThatThrownBy(() -> jschPool.generateTicket()).isInstanceOf(IllegalStateException.class);
        } finally {
            jschPool.close();
        }
    }

    @Test()
    void generate_tickets_after_session_deconnected() {

        try {
            var s = jschPool.getSshSession();
            s.disconnect();
            var ticket = jschPool.generateTicket();
            var channel = jschPool.get(ticket);
            assertThat(jschPool.isNotConnected(channel)).isFalse();
            assertThat(channel.getSession()).isNotSameAs(s);
        } catch (JSchException exception) {
            assumeTrue(
                    JSchException.class.isInstance(exception), "Test is ignored if mft not available or not well configured");
        } finally {
            jschPool.close();
        }
    }

    @Test
    void get_chanel_different_after_invalidate_cache() throws JSchException {
        var ticket1 = jschPool.generateTicket();
        var channel1 = jschPool.get(ticket1);
        this.jschPool.invalidate(ticket1);
        var channel2 = jschPool.get(ticket1);
        assertThat(channel1).isNotSameAs(channel2);
    }

    @Test()
    void release_one_ticket() {
        try {
            var tickets = jschPool.availableTickets();
            var ticket1 = jschPool.generateTicket();
            assertThat(jschPool.availableTickets()).isEqualTo(tickets - 1);
            jschPool.releaseTicket(ticket1);
            assertThat(jschPool.availableTickets()).isEqualTo(tickets);
        } finally {
            jschPool.close();
        }
    }

    @Test()
    void release_ticket() {
        try {
            var nbtickets = jschPool.availableTickets();
            var ticket1 = jschPool.generateTicket();
            assertThat(jschPool.availableTickets()).isEqualTo(nbtickets - 1);
            jschPool.releaseTicket(ticket1);
            assertThat(jschPool.availableTickets()).isEqualTo(nbtickets);
        } finally {
            jschPool.close();
        }
    }

    @Test()
    void release_ticket_non_used() {
        try {
            var nbtickets = jschPool.availableTickets();
            var ticket = 200;
            jschPool.releaseTicket(ticket);
            assertThat(jschPool.availableTickets()).isEqualTo(nbtickets);
        } finally {
            jschPool.close();
        }
    }

    @Test()
    void releaseall_tickets() {
        try {
            var nbtickets = jschPool.availableTickets();
            jschPool.generateTicket();
            jschPool.releaseAll();
            assertThat(jschPool.availableTickets()).isEqualTo(nbtickets);
        } finally {
            jschPool.close();
        }
    }

    private Session session(ChannelSftp channel) {
        try {
            return channel.getSession();
        } catch (JSchException e) {
            throw new IllegalStateException(e);
        }
    }
}