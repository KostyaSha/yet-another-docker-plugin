package com.github.kostyasha.yad.credentials;

/**
 * @author Kanstantsin Shautsou
 */
public interface DockerDaemonCerts {
    String getClientKey();

    String getClientCertificate();

    String getServerCaCertificate();
}
