package com.github.kostyasha.yad.client;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.github.kostyasha.yad.DockerCloud;
import com.github.kostyasha.yad.DockerConnector;
import com.github.kostyasha.yad.DockerSlaveTemplate;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.DockerClientConfig;
import hudson.model.ItemGroup;
import org.acegisecurity.Authentication;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.WithoutJenkins;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

import static com.cloudbees.plugins.credentials.CredentialsScope.GLOBAL;
import static com.github.kostyasha.yad.client.ClientConfigBuilderForPlugin.dockerClientConfigBuilder;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * @author lanwen (Merkushev Kirill)
 */
public class ClientBuilderForConnectorTest {

    public static final String HTTP_SERVER_URL = "http://server.url/";
    public static final String DOCKER_API_VER = "test-ver12";
    public static final String CLOUD_NAME = "cloud-name";
    public static final int READ_TIMEOUT = 10;
    public static final String EMPTY_CREDS = "";
    public static final int CONNECT_TIMEOUT = 0;
    public static final String EMPTY_CONTAINER_CAP = "";

    public static final String ID_OF_CREDS = "idcreds";
    public static final String USERNAME = "usrname";
    public static final String PASSWORD = "pwd021";

    public static final DockerConnector DOCKER_CONNECTOR = new DockerConnector(HTTP_SERVER_URL);

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    @WithoutJenkins
    public void shouldGetUriVersionReadTimeoutSettingsFromCloud() throws Exception {
        DockerCloud cloud = new DockerCloud(
                CLOUD_NAME,
                Collections.<DockerSlaveTemplate>emptyList(),
                0, DOCKER_CONNECTOR
        );
//        HTTP_SERVER_URL, CONNECT_TIMEOUT, READ_TIMEOUT, EMPTY_CREDS, DOCKER_API_VER);
        ClientConfigBuilderForPlugin builder = dockerClientConfigBuilder();
        builder.forConnector(cloud.getConnector());

        DockerClientConfig config = builder.config().build();
        assertThat("server", config.getDockerHost().toString(), equalTo(HTTP_SERVER_URL));
        // no idea what version test was about
//        assertThat("version", config.getVersion(), equalTo(DOCKER_API_VER));
//        assertThat("read TO", config.getReadTimeout(), equalTo((int) SECONDS.toMillis(READ_TIMEOUT)));
    }

    @Test
    @Ignore(value = "refactored connector for netty")
    public void shouldFindPasswordCredsFromJenkins() throws Exception {
        ClientConfigBuilderForPlugin builder = dockerClientConfigBuilder();
        builder.forServer(HTTP_SERVER_URL, DOCKER_API_VER);

        DockerClientConfig config = builder.config().build();
        assertThat("login", config.getRegistryUsername(), equalTo(USERNAME));
        assertThat("pwd", config.getRegistryPassword(), equalTo(PASSWORD));
    }


    @TestExtension
    public static class TestCreds extends CredentialsProvider {
        @Nonnull
        @Override
        public <C extends Credentials> List<C> getCredentials(Class<C> type,
                                                              ItemGroup itemGroup,
                                                              Authentication authentication) {
            return asList((C) new UsernamePasswordCredentialsImpl(GLOBAL, ID_OF_CREDS, null, USERNAME, PASSWORD));
        }
    }
}
