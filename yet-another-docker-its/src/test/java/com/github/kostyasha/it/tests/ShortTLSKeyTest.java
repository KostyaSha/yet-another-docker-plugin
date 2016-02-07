package com.github.kostyasha.it.tests;

import com.github.kostyasha.it.rule.DockerRule;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.NotFoundException;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.model.BuildResponseItem;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.model.ExposedPort;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.model.PortBinding;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.model.Ports;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.model.Version;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.model.VolumesFrom;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.DockerClientBuilder;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.DockerClientConfig;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.command.BuildImageResultCallback;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.jaxrs.DockerCmdExecFactoryImpl;
import com.github.kostyasha.yad.docker_java.org.apache.commons.io.FileUtils;
import com.github.kostyasha.yad.docker_java.org.apache.commons.lang.StringUtils;
import com.github.kostyasha.yad.other.VariableSSLConfig;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static com.github.kostyasha.it.rule.DockerRule.getDockerItDir;
import static com.github.kostyasha.it.utils.DockerHPIContainerUtil.getResource;
import static java.util.Objects.nonNull;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Kanstantsin Shautsou
 */
public class ShortTLSKeyTest {
    private static final Logger LOG = LoggerFactory.getLogger(ShortTLSKeyTest.class);
    private static final String DATA_IMAGE_TAG = ShortTLSKeyTest.class.getSimpleName().toLowerCase();
    private static final String DATA_CONTAINER_NAME = ShortTLSKeyTest.class.getName() + "_data";
    private static final String HOST_IMAGE_NAME = "dind_fedora";
    private static final String HOST_CONTAINER_NAME = ShortTLSKeyTest.class.getName() + "_host";
    public static int CONTAINER_PORT = 44444;

    private String dataContainerId;
    private String hostContainerId;

    @ClassRule
    public static DockerRule d = new DockerRule(false);

    @ClassRule
    public static TemporaryFolder folder = new TemporaryFolder(new File(getDockerItDir()));

    @Before
    public void before() throws IOException {
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


        final File buildDir = folder.newFolder(getClass().getName());

        File resources = new File("src/test/resources/" + getClass().getName().replace(".", "/") + "/data_container");
        FileUtils.copyDirectory(resources, buildDir);

        final String imageId = d.getDockerCli().buildImageCmd(buildDir)
                .withForcerm()
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

        hostContainerId = d.getDockerCli().createContainerCmd(HOST_IMAGE_NAME)
                .withName(HOST_CONTAINER_NAME)
                .withPrivileged(true)
                .withEnv("DOCKER_DAEMON_ARGS=" +
                                "--tlsverify " +
                                "--tlscacert=/var/keys/ca.pem " +
                                "--tlscert=/var/keys/server-cert.pem " +
                                "--tlskey=/var/keys/server-key.pem",
                        "PORT=" + CONTAINER_PORT)
                .withExposedPorts(new ExposedPort(CONTAINER_PORT))
                .withPortBindings(PortBinding.parse("0.0.0.0:" + CONTAINER_PORT + ":" + CONTAINER_PORT))
                .withPortSpecs(String.format("%d/tcp", CONTAINER_PORT))
                .withVolumesFrom(new VolumesFrom(dataContainerId))
                .exec()
                .getId();

        d.getDockerCli().startContainerCmd(hostContainerId).exec();
    }

    @Test
    public void testKey() throws IOException {
        final InspectContainerResponse inspect = d.getDockerCli().inspectContainerCmd(hostContainerId).exec();
        assertThat(inspect.getState().isRunning(), is(true));
        final int exposedPort = getExposedPort(inspect, CONTAINER_PORT);
        LOG.info("Exposed port {}", exposedPort);

        // create client
        final VariableSSLConfig sslConfig = new VariableSSLConfig(
                getResource(getClass(), "data_container/keys/key.pem"),
                getResource(getClass(), "data_container/keys/cert.pem"),
                getResource(getClass(), "data_container/keys/ca.pem")
        );

        DockerClientConfig clientConfig = new DockerClientConfig.DockerClientConfigBuilder()
                .withUri("https://" + d.getHost() + ":" + CONTAINER_PORT)
                .withSSLConfig(sslConfig)
                .build();

        DockerCmdExecFactoryImpl dockerCmdExecFactory = new DockerCmdExecFactoryImpl()
                .withReadTimeout(0)
                .withConnectTimeout(10000);

        DockerClient dockerClient = DockerClientBuilder.getInstance(clientConfig)
                .withDockerCmdExecFactory(dockerCmdExecFactory)
                .build();

        final Version version = dockerClient.versionCmd().exec();
        LOG.info("Daemon version {}", version);
        assertThat(version.getVersion(), notNullValue());
    }

    private int getExposedPort(InspectContainerResponse inspect, int targetPort) {
        final Map<ExposedPort, Ports.Binding[]> bindings = inspect.getNetworkSettings().getPorts().getBindings();
        for (Map.Entry<ExposedPort, Ports.Binding[]> entry : bindings.entrySet()) {
            if (entry.getKey().getPort() == targetPort) {
                return entry.getValue()[0].getHostPort();
            }
        }

        throw new IllegalArgumentException("Can't find exposed port for port: " + targetPort);
    }
}
