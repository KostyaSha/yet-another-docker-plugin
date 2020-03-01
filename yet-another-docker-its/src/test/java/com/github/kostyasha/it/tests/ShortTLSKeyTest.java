package com.github.kostyasha.it.tests;

import com.github.kostyasha.it.rule.DockerRule;
import com.github.kostyasha.yad.client.ClientBuilderForConnector;
import com.github.kostyasha.yad.other.ConnectorType;
import com.github.kostyasha.yad.other.VariableSSLConfig;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.DockerCmdExecFactory;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.exception.NotFoundException;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.BuildResponseItem;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.ExposedPort;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.HostConfig;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.PortBinding;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.Version;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.VolumesFrom;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.DockerClientBuilder;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.DockerClientConfig;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.command.BuildImageResultCallback;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.command.PullImageResultCallback;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory;
import com.github.kostyasha.yad_docker_java.org.apache.commons.io.FileUtils;
import com.github.kostyasha.yad_docker_java.org.apache.commons.lang.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.Arrays;

import static com.github.kostyasha.it.rule.DockerRule.getDockerItDir;
import static com.github.kostyasha.it.utils.DockerHPIContainerUtil.getResource;
import static com.github.kostyasha.it.utils.DockerUtils.getExposedPort;
import static com.github.kostyasha.yad.other.ConnectorType.JERSEY;
import static com.github.kostyasha.yad.other.ConnectorType.NETTY;
import static com.github.kostyasha.yad.other.ConnectorType.OKHTTP;
import static com.jayway.awaitility.Awaitility.await;
import static java.util.Objects.nonNull;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Kanstantsin Shautsou
 */
@RunWith(Parameterized.class)
public class ShortTLSKeyTest {
    private static final Logger LOG = LoggerFactory.getLogger(ShortTLSKeyTest.class);
    private static final String DATA_IMAGE_TAG = ShortTLSKeyTest.class.getSimpleName().toLowerCase();
    private static final String DATA_CONTAINER_NAME = ShortTLSKeyTest.class.getName() + "_data";
    private static final String HOST_IMAGE_NAME = "kostyasha/dind:fedora23-1.11";
    private static final String HOST_CONTAINER_NAME = ShortTLSKeyTest.class.getName() + "_host";
    public static int CONTAINER_PORT = 44444;

    private String dataContainerId;
    private String hostContainerId;

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<ConnectorType> connectorTypes() {
        return Arrays.asList(
                NETTY,
                JERSEY,
                OKHTTP
        );
    }

    @Parameterized.Parameter
    public static ConnectorType connectorType;

    @ClassRule
    public static DockerRule d = new DockerRule(false);

    @ClassRule
    public static TemporaryFolder folder = new TemporaryFolder(new File(getDockerItDir()));

    @Before
    public void before() throws IOException, InterruptedException {
        after();

        final File buildDir = folder.newFolder(getClass().getName());

        File resources = new File("src/test/resources/" + getClass().getName().replace(".", "/") + "/data_container");
        FileUtils.copyDirectory(resources, buildDir);

        final String imageId = d.getDockerCli().buildImageCmd(buildDir)
                .withForcerm(true)
                .withTag(DATA_IMAGE_TAG)
                .exec(new BuildImageResultCallback() {
                    public void onNext(BuildResponseItem item) {
                        String text = item.getStream();
                        if (nonNull(text)) {
                            LOG.debug(StringUtils.removeEnd(text, DockerRule.NL));
                        }
                        super.onNext(item);
                    }
                })
                .awaitImageId();

        dataContainerId = d.getDockerCli().createContainerCmd(imageId)
                .withCmd("/bin/true")
                .withName(DATA_CONTAINER_NAME)
                .exec()
                .getId();

        d.getDockerCli().pullImageCmd(HOST_IMAGE_NAME).exec(new PullImageResultCallback()).awaitSuccess();

        hostContainerId = d.getDockerCli().createContainerCmd(HOST_IMAGE_NAME)
                .withName(HOST_CONTAINER_NAME)
                .withHostConfig(HostConfig.newHostConfig()
                        .withPrivileged(true)
                        .withPortBindings(PortBinding.parse("0.0.0.0:" + CONTAINER_PORT + ":" + CONTAINER_PORT))
                        .withVolumesFrom(new VolumesFrom(dataContainerId)))
                .withEnv("DOCKER_DAEMON_ARGS=" +
                                "--tlsverify " +
                                "--tlscacert=/var/keys/ca.pem " +
                                "--tlscert=/var/keys/server-cert.pem " +
                                "--tlskey=/var/keys/server-key.pem",
                        "PORT=" + CONTAINER_PORT)
                .withExposedPorts(new ExposedPort(CONTAINER_PORT))
                .withPortSpecs(String.format("%d/tcp", CONTAINER_PORT))
                .exec()
                .getId();

        d.getDockerCli().startContainerCmd(hostContainerId).exec();

        d.waitDindStarted(hostContainerId);
    }

    @After
    public void after() {
        // remove host container
        try {
//            d.getDockerCli().inspectContainerCmd(HOST_CONTAINER_NAME).exec();
            d.getDockerCli().removeContainerCmd(HOST_CONTAINER_NAME)
                    .withForce(true)
                    .withRemoveVolumes(true)
                    .exec();
            LOG.info("Removed container {}", HOST_CONTAINER_NAME);
        } catch (NotFoundException ignore) {
        }

        // remove data container
        try {
            d.getDockerCli().removeContainerCmd(DATA_CONTAINER_NAME)
                    .withForce(true)
                    .withRemoveVolumes(true)
                    .exec();
            LOG.info("Removed container {}", DATA_CONTAINER_NAME);
        } catch (NotFoundException ignore) {
        }

        // remove data image
        try {
            d.getDockerCli().removeImageCmd(DATA_IMAGE_TAG)
                    .withForce(true)
                    .exec();
            LOG.info("Removed image {}", DATA_IMAGE_TAG);
        } catch (NotFoundException ignore) {
        }
    }

    @Test
    public void testKey() throws IOException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException,
            KeyManagementException {
        final InspectContainerResponse inspect = d.getDockerCli().inspectContainerCmd(hostContainerId).exec();
        assertThat(inspect.getState().getRunning(), is(true));
        final int exposedPort = getExposedPort(inspect, CONTAINER_PORT);
        LOG.info("Exposed port {}", exposedPort);

        // create client
        final VariableSSLConfig sslConfig = new VariableSSLConfig(
                getResource(getClass(), "data_container/keys/key.pem"),
                getResource(getClass(), "data_container/keys/cert.pem"),
                getResource(getClass(), "data_container/keys/ca.pem")
        );

        DockerClientConfig clientConfig = new DefaultDockerClientConfig.Builder()
                .withDockerHost("tcp://" + d.getHost() + ":" + CONTAINER_PORT)
                .withDockerTlsVerify(true)
                .withCustomSslConfig(sslConfig)
                .build();

        DockerClient dockerClient = ClientBuilderForConnector.newClientBuilderForConnector()
                .withDockerClientConfig(clientConfig)
                .withConnectorType(connectorType)
                .build();

//        DockerCmdExecFactory dockerCmdExecFactory = new NettyDockerCmdExecFactory();
//        DockerCmdExecFactory dockerCmdExecFactory = new JerseyDockerCmdExecFactory();
//        DockerClient dockerClient = DockerClientBuilder.getInstance(clientConfig)
//                .withDockerCmdExecFactory(dockerCmdExecFactory)
//                .build();

        await().timeout(10, SECONDS).until(() -> {
            final Version version = dockerClient.versionCmd().exec();
            LOG.info("Daemon version {}", version);
            assertThat(version.getVersion(), notNullValue());
        });
    }
}
