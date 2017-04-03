package com.github.kostyasha.yad.other;

import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.exception.DockerClientException;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.SSLConfig;
import com.github.kostyasha.yad_docker_java.org.bouncycastle.jce.provider.BouncyCastleProvider;
import com.github.kostyasha.yad_docker_java.org.glassfish.jersey.SslConfigurator;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import javax.net.ssl.SSLContext;
import java.io.Serializable;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.UnrecoverableKeyException;

import static com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.util.CertificateUtils.createKeyStore;
import static com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.util.CertificateUtils.createTrustStore;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang.StringUtils.isNotBlank;

/**
 * @author Kanstantsin Shautsou
 */
public class VariableSSLConfig implements SSLConfig, Serializable {
    private static final long serialVersionUID = 1L;

    private String keypem;
    private String certpem;
    private String capem;

    public VariableSSLConfig(String keypem, String certpem, String capem) {
        this.capem = capem;
        this.certpem = certpem;
        this.keypem = keypem;
    }

    @SuppressFBWarnings(value = "REC_CATCH_EXCEPTION", justification = "docker-java uses runtime exceptions")
    @Override
    public SSLContext getSSLContext() throws KeyManagementException, UnrecoverableKeyException,
            NoSuchAlgorithmException, KeyStoreException {
        try {
            Security.addProvider(new BouncyCastleProvider());

            SslConfigurator sslConfig = SslConfigurator.newInstance(true);
            sslConfig.securityProtocol("TLSv1.2");

            // add keystore
            sslConfig.keyStore(createKeyStore(keypem, certpem));
            sslConfig.keyStorePassword("docker"); // ??

            if (isNotBlank(capem)) {
                sslConfig.trustStore(createTrustStore(capem));
            }

            return sslConfig.createSSLContext();
        } catch (Exception e) {
            throw new DockerClientException(e.getMessage(), e);
        }
    }
}
