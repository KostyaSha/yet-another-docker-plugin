package com.github.kostyasha.yad.utils;

import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.AuthConfig;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.AuthConfigurations;
import org.junit.Test;

import static com.github.kostyasha.yad.utils.DockerJavaUtils.getAuthConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerJavaUtilsTest {

    @Test
    public void testName() {
        final String s = "some.host.com:3233/namespace/image:tag";
        final AuthConfigurations authConfigurations = new AuthConfigurations();
        authConfigurations.addConfig(
                new AuthConfig()
                        .withRegistryAddress("some.host.com:3233")
                        .withUsername("user")
                        .withPassword("pass")
        );
        final AuthConfig authConfig = getAuthConfig(s, authConfigurations);
        assertThat(authConfig, notNullValue());
    }
}
