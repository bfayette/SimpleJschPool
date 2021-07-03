package org.fbreton.jsch;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * Consider this as a snipet for creating a Jsch session object with asymmetric encryption.
 * No test provided as we may need a mock sftp server. So try it before.
 * @Author : bfayette
 *
 */
@RequiredArgsConstructor
@Slf4j
public class JschSessionProvider implements Supplier<Session> {

    protected SftpPropertiesConfig config;

    @Override
    public Session get() {
        return jschSession(config);
    }

    @SneakyThrows
    private Session jschSession(SftpPropertiesConfig config) {
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