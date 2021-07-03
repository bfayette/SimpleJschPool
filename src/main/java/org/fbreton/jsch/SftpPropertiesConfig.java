package org.fbreton.jsch;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class SftpPropertiesConfig {
    private String privateKey;
    private String username;
    private String host;
    private int port;
    private int serverAliveInterval;
}
