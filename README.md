# SimpleJschPool

A Java library for managing SSH/SFTP connection pools using the JSch library. Provides a reusable, thread-safe pool of SFTP channels with ticket-based access, automatic cleanup, and connection lifecycle management.

## Features

- **Connection pooling**: Manage a pool of SFTP channels with configurable size
- **Ticket-based access**: Control concurrent access using tickets/semaphores
- **Moderator pattern**: Access control is explicit and centralized via `PoolModerator` interface
- **Automatic cleanup**: Channels are automatically disconnected when removed from cache
- **Session sharing**: Share a single SSH session across multiple connections
- **Expiration**: Cache entries expire after access timeout
- **Resource safety**: Proper disconnection on `close()` and error handling

## Installation

This project uses Maven. Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>org.fbreton.jsch</groupId>
    <artifactId>ssh-cache</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

Or install locally:

```bash
mvn install
```

## Quick Start

```java
import org.fbreton.jsch.SimpleJschPool;
import org.fbreton.jsch.JschSessionProvider;
import org.fbreton.jsch.SftpPropertiesConfig;

// Configure SFTP properties
var config = SftpPropertiesConfig.builder()
    .username("user")
    .host("example.com")
    .port(22)
    .privateKey(Files.readString(Paths.get("path/to/key")))
    .serverAliveInterval(30000)
    .build();

// Create a session provider
var sessionProvider = new JschSessionProvider(config);

// Create ticket generator
var ticketsProvider = TicketsGenerator.of(10);

// Create the pool
try (var pool = new SimpleJschPool(ticketsProvider, sessionProvider, config.PoolConfig.of(10, 1))) {
    // Get an SFTP channel using a ticket
    var ticket = pool.generateTicket();
    var sftpChannel = pool.get(ticket);

    // Use the SFTP channel...
    // sftpChannel.put(...);
    // sftpChannel.get(...);

    // Release the ticket when done
    pool.releaseTicket(ticket);
}
```

## Moderator Pattern

The library now implements the **moderator pattern** for access control. This is a hard truth about the original design: without an explicit moderator, access control was implicit — scattered across `semaphore.tryAcquire()`, `inUseTickets.contains()` checks, and ticket filtering logic across multiple methods.

The `PoolModerator` interface centralizes this logic, making the access control flow obvious and localizable:

- `tryGrantTicket()` — attempts to grant a ticket, combining semaphore acquisition + unused-ticket validation
- `releaseTicket()` — releases the semaphore permit and removes the ticket from the in-use set
- `isTicketInUse()` — checks if a ticket is currently held
- `availableTickets()` — returns available permits from the semaphore

You can provide your own moderator implementation by implementing `PoolModerator` and passing it to `SimpleJschPool` constructor:

```java
// Default moderator (behavior unchanged)
var pool = new SimpleJschPool(ticketsProvider, sessionProvider, poolConfig);

// Custom moderator with additional validation
var customPool = new SimpleJschPool(ticketsProvider, sessionProvider, poolConfig, new CustomModerator());
```

### `SimpleJschPool`

- `generateTicket()` — generates a ticket through the moderator (semaphore + unused validation)
- `get(Number key)` — Get an SFTP channel by ticket
- `releaseTicket(Number key)` — Release a ticket via the moderator
- `availableTickets()` — Get number of available tickets (from moderator)
- `releaseAll()` — Release all tickets
- `close()` — Close the pool and release all resources

### `PoolModerator`

Interface for custom access control logic. See `org.fbreton.jsch.PoolModerator` for the full API.

## API

### `SimpleJschPool`

- `generateTicket()` - Generate a ticket for channel access
- `get(Number key)` - Get an SFTP channel by ticket
- `releaseTicket(Number key)` - Release a ticket
- `availableTickets()` - Get number of available tickets
- `releaseAll()` - Release all tickets
- `close()` - Close the pool and release all resources

### `SftpPropertiesConfig`

- `username` - SSH username
- `host` - SSH host
- `port` - SSH port (default: 22)
- `privateKey` - Private key for authentication
- `serverAliveInterval` - Server alive interval in milliseconds

### `PoolConfig`

- `maxOpenedChannels` - Maximum number of concurrent channels
- `waitingTimeInMs` - Waiting time in milliseconds for ticket acquisition

## Development

### Building

```bash
mvn compile       # Compile the project
mvn test          # Run tests
mvn package       # Create JAR
```

### Project Structure

- `SimpleJschPool.java` - Main pool implementation with cache, semaphore, and removal listener
- `JschSessionProvider.java` - Creates JSch sessions with asymmetric encryption
- `SftpPropertiesConfig.java` - Configuration class for SFTP properties
- `TicketsGenerator.java` - Generates integer tickets for channel access
- Test files in `src/test/java/`

## License

This project is open source. See the project repository for license details.