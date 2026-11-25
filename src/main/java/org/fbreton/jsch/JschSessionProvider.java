package org.fbreton.jsch;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.function.Supplier;

public class JschSessionProvider implements Supplier<Session> {
    private static final Logger log = LoggerFactory.getLogger(JschSessionProvider.class);

    protected SftpPropertiesConfig config;

    public JschSessionProvider(SftpPropertiesConfig config) {
        this.config = config;
    }

    @Override
    public Session get() {
        try {
            return jschSession(config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create JSch session", e);
        }
    }

    @SuppressWarnings("sneakythrows")
    private Session jschSession(SftpPropertiesConfig config) throws Exception {
        log.debug("jschSession MFT @ creer. Pas encore connecte.");
        var jsch = new JSch();
        jsch.addIdentity("mft_key", config.getPrivateKey().getBytes(), null, null);
        var session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
        var jschConfiguration = jschConfiguration();
        session.setConfig(jschConfiguration);
        //JSCH session connection issue
        // https://stackoverflow.com/questions/45088540/jsch-session-connection-issue
        session.setServerAliveCountMax(2);
        session.setServerAliveInterval(config.getServerAliveInterval());
        return session;
    }

    private static Properties jschConfiguration() throws IOException {
        var configProperties = new Properties();
        try (InputStream in = JschSessionProvider.class.getResourceAsStream("/configJsch.properties")) {
            configProperties.load(in);
        }
        return configProperties;
    }
}