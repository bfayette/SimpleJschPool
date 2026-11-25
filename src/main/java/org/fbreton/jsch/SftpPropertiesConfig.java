package org.fbreton.jsch;

public class SftpPropertiesConfig {
    private String privateKey;
    private String username;
    private String host;
    private int port;
    private int serverAliveInterval;

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getServerAliveInterval() {
        return serverAliveInterval;
    }

    public void setServerAliveInterval(int serverAliveInterval) {
        this.serverAliveInterval = serverAliveInterval;
    }
}