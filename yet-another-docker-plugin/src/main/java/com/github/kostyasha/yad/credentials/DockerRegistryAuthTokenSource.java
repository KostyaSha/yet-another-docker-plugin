package com.github.kostyasha.yad.credentials;

import jenkins.authentication.tokens.api.AuthenticationTokenException;
import jenkins.authentication.tokens.api.AuthenticationTokenSource;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.Charsets;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryToken;

import javax.annotation.Nonnull;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerRegistryAuthTokenSource
        extends AuthenticationTokenSource<DockerRegistryToken, DockerRegistryAuthCredentials> {
    public DockerRegistryAuthTokenSource() {
        super(DockerRegistryToken.class, DockerRegistryAuthCredentials.class);
    }

    @Nonnull
    @Override
    public DockerRegistryToken convert(@Nonnull DockerRegistryAuthCredentials c) throws AuthenticationTokenException {
        return new DockerRegistryToken(c.getEmail(),
                Base64.encodeBase64String((c.getUsername() + ":" + c.getPassword().getPlainText())
                        .getBytes(Charsets.UTF_8)));
    }
}
