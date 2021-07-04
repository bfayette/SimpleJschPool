
package org.fbreton.jsch;

import com.google.common.collect.Range;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.SneakyThrows;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class})
class SimpleJschPoolTest extends Assertions {

    private SimpleJschPool jschPool;
    private Set<ChannelSftp> inUseChannels = new CopyOnWriteArraySet<>();
    private AtomicInteger counter = new AtomicInteger(0);
    private Supplier<Session> sessionProvider;

    @BeforeEach
    void setup() {
        sessionProvider = () -> {
            var session = Mockito.mock(Session.class);
            whenSessionConnect(session);
            whenSessionDisconnect(session);
            return session;
        };

        final  var generator = TicketsGenerator.of(10);
        final var config = SimpleJschPool.PoolConfig.of(10, 1);
        jschPool = new SimpleJschPool( generator, sessionProvider, config) {
            @Override
            @SneakyThrows
            ChannelSftp create() {
                final var session = jschPool.getSshSession();
                final var channel = mock(ChannelSftp.class);
                //channel linked to a session
                lenient().when(channel.getSession()).thenReturn(session);
                lenient().when(channel.isConnected()).then(arg -> session.isConnected());
                return channel;
            }
        };
    }

    @Test
    @Execution(ExecutionMode.CONCURRENT)
    @RepeatedTest(value = 10)
    void unchannel_by_ticket_via_only_one_session() throws JSchException {
        try {
            this.inUseChannels.add(jschPool.get(jschPool.generateTicket()));
            assertThat(inUseChannels).hasSize(counter.incrementAndGet());
            assertThat(inUseChannels.stream().map(this::session).collect(Collectors.toSet())).containsExactly(jschPool.getSshSession());
        } finally {
            jschPool.close();
        }
    }

    @Test
    void unchannel_unique_by_ticket() throws JSchException {
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
            var session = jschPool.getSshSession();
            session.disconnect();
            var ticket = jschPool.generateTicket();
            var channel = jschPool.get(ticket);
            assertThat(jschPool.isNotConnected(channel)).isFalse();
            assertThat(channel.getSession()).isNotSameAs(session);
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    private void whenSessionConnect(Session session) {
        try {
            lenient().doAnswer((arg) -> {
                lenient().when(session.isConnected()).thenReturn(true);
                return null;
            }).when(session).connect();
        } catch (JSchException e) {
            fail("should not get here");
        }
    }

    private void whenSessionDisconnect(Session session) {
        try {
            lenient().doAnswer((arg) -> {
                lenient().when(session.isConnected()).thenReturn(false);
                return null;
            }).when(session).disconnect();
        } finally {
        }
    }

}
