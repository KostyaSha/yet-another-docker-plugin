package com.github.kostyasha.yad.other;

import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.DockerClientException;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.SSLConfig;
import com.github.kostyasha.yad.docker_java.org.bouncycastle.jce.provider.BouncyCastleProvider;
import com.github.kostyasha.yad.docker_java.org.glassfish.jersey.SslConfigurator;
import com.github.kostyasha.yad.utils.CertUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import javax.net.ssl.SSLContext;
import java.io.Serializable;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.UnrecoverableKeyException;

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

            // properties acrobatics not needed for java > 1.6
            String httpProtocols = System.getProperty("https.protocols");
            System.setProperty("https.protocols", "TLSv1");
            SslConfigurator sslConfig = SslConfigurator.newInstance(true);
            if (nonNull(httpProtocols)) {
                System.setProperty("https.protocols", httpProtocols);
            }

            // add keystore
            sslConfig.keyStore(CertUtils.createKeyStore(keypem, certpem));
            sslConfig.keyStorePassword("docker"); // ??

            if (isNotBlank(capem)) {
                sslConfig.trustStore(CertUtils.createTrustStore(capem));
            }

            return sslConfig.createSSLContext();
        } catch (Exception e) {
            throw new DockerClientException(e.getMessage(), e);
        }
    }
}
