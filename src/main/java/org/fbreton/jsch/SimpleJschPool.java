package org.fbreton.jsch;

import com.google.common.cache.*;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import lombok.Generated;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static java.text.MessageFormat.format;

@Slf4j
@Generated
public class SimpleJschPool implements RemovalListener<Integer, ChannelSftp>, AutoCloseable {
    protected transient final AtomicInteger ticketsGenerator = new AtomicInteger(0);
    protected transient final Set<Integer> inUseTickets = new CopyOnWriteArraySet<>();
    protected transient final AtomicReference<Session> sshSession = new AtomicReference<>();
    protected transient LoadingCache<Integer, ChannelSftp> cache;
    protected transient Semaphore semaphore;

    private transient final Supplier<Session> sessionProvider;
    private transient final int maxOpenedChannels;
    private transient final long waitingTimeInMs;

    public SimpleJschPool(final Supplier<Session> sessionProvider, final int maxOpenedChannels, final long waitingTimeInMs) {
        this.sessionProvider = sessionProvider;
        this.maxOpenedChannels = maxOpenedChannels;
        this.waitingTimeInMs = waitingTimeInMs;
        this.init();
    }

    public ChannelSftp get(final Integer key) {
        var channelSftp = this.cache.getUnchecked(key);
        if (isNotConnected(channelSftp)) {
            log.debug("Invalidate entry then try to load another connection");
            this.invalidate(key);
            channelSftp = this.cache.getUnchecked(key);
        }
        return channelSftp;

    }

    @SneakyThrows
    public int generateTicket() {
        final var maxOpenedChannels = this.maxOpenedChannels;
        final var enter = this.semaphore.tryAcquire(this.waitingTimeInMs, TimeUnit.MILLISECONDS);
        if (enter) {
            try {
                final var ticket = IntStream.of(0, maxOpenedChannels)
                        .mapToObj(attempt -> this.ticketsGenerator.incrementAndGet() % maxOpenedChannels)
                        .map(Math::abs)
                        .filter(Predicate.not(SimpleJschPool.this.inUseTickets::contains))
                        .findFirst().orElseThrow();
                this.inUseTickets.add(ticket);
                return ticket;
            } catch (RuntimeException exception) {
                log.debug("release as no ticket created");
                this.semaphore.release();
                throw exception;
            }
        } else {
            throw new IllegalStateException(
                    format("No ticket available. Increase pool channels max size. Context :[inUse={0} max={1}]",
                            this.inUseTickets.size(), maxOpenedChannels));
        }
    }


    public void invalidate(final Integer key) {
        try {
            this.inUseTickets.remove(key);
        } finally {
            this.cache.invalidate(key);
        }
    }


    public void releaseTicket(final Integer key) {
        try {
            log.debug("Release Ticket.");
            if (inUseTickets.contains(key)) {
                semaphore.release();
            }
        } finally {
            this.ticketsGenerator.decrementAndGet();
            this.inUseTickets.remove(key);
        }
    }

    public int availableTickets() {
        return semaphore.availablePermits();
    }

    @Override
    public void onRemoval(final RemovalNotification<Integer, ChannelSftp> notification) {
        try {
            log.debug("Remove a channel. Cache auto cleanup");
            final var channelSftp = notification.getValue();
            channelSftp.disconnect();
        } catch (Exception exception) {
            log.warn(exception.getMessage(), exception);
        }
    }


    @SneakyThrows
    public Session getSshSession() {
        final var session = sshSession.get();
        if (session == null || !session.isConnected()) {
            final var electedSession = sessionProvider.get();
            if (this.sshSession.compareAndSet(session, electedSession)) {
                log.debug("The lucky thread connect the elected mft session for anyone else.");
                electedSession.connect();
            }
        }
        return sshSession.get();
    }


    public void releaseAll() {
        this.semaphore.release(inUseTickets.size());
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
            this.ticketsGenerator.lazySet(0);
            this.releaseAll();
        } finally {
            this.inUseTickets.forEach(this::invalidate);
            final var session = this.sshSession.get();
            if (session != null) {
                session.disconnect();
            }
        }
    }

    @SneakyThrows
    ChannelSftp create() {
        ChannelSftp channelSftp;
        try {
            channelSftp = (ChannelSftp) getSshSession().openChannel("sftp");
            channelSftp.connect();
            log.debug("ChannelSftp created.");
        } catch (JSchException e) {
            channelSftp = (ChannelSftp) getSshSession().openChannel("sftp");
            channelSftp.connect();
        }
        return channelSftp;
    }

    private void init() {
        this.semaphore = new Semaphore(this.maxOpenedChannels, true);
        this.cache = CacheBuilder.newBuilder()
                .expireAfterAccess(1, TimeUnit.MINUTES)
                .removalListener(this)
                .build(CacheLoader.from(key -> create()));
    }
}