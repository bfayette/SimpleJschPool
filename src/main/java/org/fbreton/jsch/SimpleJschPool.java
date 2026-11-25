package org.fbreton.jsch;

import com.google.common.cache.*;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static java.text.MessageFormat.format;

public class SimpleJschPool implements RemovalListener<Number, ChannelSftp>, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SimpleJschPool.class);

    protected transient final Set<Number> inUseTickets = new CopyOnWriteArraySet<>();
    protected transient final AtomicReference<Session> sshSession = new AtomicReference<>();
    protected transient LoadingCache<Number, ChannelSftp> cache;
    protected transient Semaphore semaphore;

    protected final PoolConfig poolConfig;
    protected final Supplier<Number> ticketsProvider;
    protected final PoolModerator moderator;
    private final Supplier<Session> sessionProvider;

    public SimpleJschPool(final Supplier<Number> ticketsProvider, final Supplier<Session> sessionProvider,
                          final PoolConfig poolConfig) {
        this(ticketsProvider, sessionProvider, poolConfig, new ModeratorImpl());
    }

    public SimpleJschPool(final Supplier<Number> ticketsProvider, final Supplier<Session> sessionProvider,
                          final PoolConfig poolConfig, final PoolModerator moderator) {
        this.ticketsProvider = ticketsProvider;
        this.sessionProvider = sessionProvider;
        this.poolConfig = poolConfig;
        this.moderator = moderator;
        this.init();
    }

    public ChannelSftp get(final Number key) {
        var channelSftp = this.cache.getUnchecked(key);
        if (isNotConnected(channelSftp)) {
            log.debug("Invalidate entry then try to load with another connection");
            this.invalidate(key);
            channelSftp = this.cache.getUnchecked(key);
        }
        return channelSftp;

    }

    public Number generateTicket() {
        var maxOpenedChannels = this.poolConfig.getMaxOpenedChannels();
        var waitingTimeInMs = this.poolConfig.getWaitingTimeInMs();

        var ticket = this.moderator.tryGrantTicket(
                this.ticketsProvider,
                this.inUseTickets,
                this.semaphore,
                maxOpenedChannels,
                waitingTimeInMs
        );

        if (ticket != null) {
            this.inUseTickets.add(ticket);
            return ticket;
        } else {
            throw new IllegalStateException(
                    format("No ticket available. Increase pool channels max size. Context :[inUse={0} max={1}]",
                            this.inUseTickets.size(), maxOpenedChannels));
        }
    }

    public void invalidate(final Number key) {
        try {
            this.moderator.releaseTicket(key, this.inUseTickets, this.semaphore);
            this.cache.invalidate(key);
        } catch (Exception e) {
            log.warn("Failed to invalidate ticket {}", key, e);
        }
    }


    public void releaseTicket(final Number key) {
        this.moderator.releaseTicket(key, this.inUseTickets, this.semaphore);
    }

    public int availableTickets() {
        return this.moderator.availableTickets(this.semaphore);
    }


    @Override
    public void onRemoval(final RemovalNotification<Number, ChannelSftp> notification) {
        try {
            log.debug("Remove a channel. Cache auto cleanup");
            final var channelSftp = notification.getValue();
            channelSftp.disconnect();
        } catch (Exception exception) {
            log.warn(exception.getMessage(), exception);
        }
    }


    public Session getSshSession() {
        final var session = sshSession.get();
        if (session == null || !session.isConnected()) {
            final var electedSession = sessionProvider.get();
            if (this.sshSession.compareAndSet(session, electedSession)) {
                log.debug("The lucky thread connects the elected mft session for anyone else.");
                try {
                    electedSession.connect();
                } catch (JSchException e) {
                    log.warn("Failed to connect elected session", e);
                }
            }
        }
        return sshSession.get();
    }


    public void releaseAll() {
        this.moderator.releaseTicket(null, this.inUseTickets, this.semaphore);
        this.inUseTickets.clear();
    }

    public boolean isNotConnected(final ChannelSftp channelSftp) {
        try {
            channelSftp.pwd();
            return !channelSftp.isConnected() || channelSftp.isClosed();
        } catch (SftpException exception) {
            log.warn(exception.getMessage(), exception);
            return false;
        }
    }

    @Override
    public void close() {
        log.debug("Cache cleanup general.");
        try {
            this.releaseAll();
        } finally {
            this.inUseTickets.forEach(this::invalidate);
            final var session = this.sshSession.get();
            if (session != null) {
                session.disconnect();
            }
        }
    }

    ChannelSftp create() {
        ChannelSftp channelSftp;
        try {
            var session = getSshSession();
            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect();
            log.debug("ChannelSftp created.");
        } catch (JSchException e) {
            try {
                var session = getSshSession();
                channelSftp = (ChannelSftp) session.openChannel("sftp");
                channelSftp.connect();
            } catch (JSchException ex) {
                log.warn("Failed to create SFTP channel after retry", ex);
                throw new RuntimeException("Failed to create SFTP channel", ex);
            }
        }
        return channelSftp;
    }

    private void init() {
        final var maxOpenedChannels = this.poolConfig.getMaxOpenedChannels();
        this.semaphore = new Semaphore(maxOpenedChannels, true);
        this.cache = CacheBuilder.newBuilder()
                .expireAfterAccess(1, TimeUnit.MINUTES)
                .removalListener(this)
                .build(CacheLoader.from(key -> create()));
    }

    public static class PoolConfig {
        private final int maxOpenedChannels;
        private final long waitingTimeInMs;

        public PoolConfig(int maxOpenedChannels, long waitingTimeInMs) {
            this.maxOpenedChannels = maxOpenedChannels;
            this.waitingTimeInMs = waitingTimeInMs;
        }

        public int getMaxOpenedChannels() {
            return maxOpenedChannels;
        }

        public long getWaitingTimeInMs() {
            return waitingTimeInMs;
        }
    }
}