package com.github.kostyasha.it.tests;

import com.github.kostyasha.it.rule.DockerResource;
import com.github.kostyasha.it.rule.DockerRule;
import com.github.kostyasha.it.utils.DockerUtils;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.DockerCmdExecFactory;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.exception.NotFoundException;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.AuthConfig;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.BuildResponseItem;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.ExposedPort;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.HostConfig;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.PortBinding;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.RestartPolicy;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.DockerClientBuilder;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.DockerClientConfig;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.command.BuildImageResultCallback;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.command.PullImageResultCallback;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.command.PushImageResultCallback;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory;
import com.github.kostyasha.yad_docker_java.org.apache.commons.io.FileUtils;
import com.github.kostyasha.yad_docker_java.org.apache.commons.lang.StringUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static com.github.kostyasha.it.rule.DockerRule.getDockerItDir;
import static com.github.kostyasha.it.utils.DockerUtils.ensureContainerRemoved;
import static com.github.kostyasha.it.utils.TempFileHelper.checkPathIT;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.io.FileUtils.writeStringToFile;

/**
 * client -> (DinD:44447) -> (nginx-proxy) -> (docker-registry:44446)
 * () is container.
 *
 * @author Kanstantsin Shautsou
 */
public class NginxRegistryTest {
    private static final Logger LOG = LoggerFactory.getLogger(NginxRegistryTest.class);

    @ClassRule
    public static DockerRule d = new DockerRule(false);

    @ClassRule
    transient public static TemporaryFolder folder = new TemporaryFolder(new File(getDockerItDir()));

    public DindResource dindResource = new DindResource();

    /**
     * Depends on nginx because sets it as insecure.
     */
    public class DindResource extends DockerResource {
        public final String IMAGE_NAME = "kostyasha/dind:fedora23-1.11";
        public final int CONTAINER_PORT = 4243;
        public final String HOST_CONTAINER_NAME = getClass().getCanonicalName() + "_host";

        private String hostContainerId;

        @NonNull
        public String getHostContainerId() {
            requireNonNull(hostContainerId);
            return hostContainerId;
        }

        public int getExposedPort() {
            final InspectContainerResponse inspect = d.getDockerCli().inspectContainerCmd(getHostContainerId()).exec();
            return DockerUtils.getExposedPort(inspect, CONTAINER_PORT);
        }

        @Override
        protected void before() throws Throwable {
            after();

            d.getDockerCli().pullImageCmd(IMAGE_NAME)
                    .exec(new PullImageResultCallback())
                    .awaitSuccess();

            hostContainerId = d.getDockerCli().createContainerCmd(IMAGE_NAME)
                    .withName(HOST_CONTAINER_NAME)
                    .withHostConfig(HostConfig.newHostConfig()
                            .withPrivileged(true)
                            .withRestartPolicy(RestartPolicy.alwaysRestart())
                            .withPortBindings(PortBinding.parse(":44447:" + CONTAINER_PORT))
                    )
                    .withEnv("PORT=4243",
                            String.format("DOCKER_DAEMON_ARGS=--insecure-registry %s:%s",
                                    d.getHost(), nginxContainer.getExposedPort()
                            ))
                    .withExposedPorts(new ExposedPort(CONTAINER_PORT))
                    .exec()
                    .getId();

            d.getDockerCli().startContainerCmd(hostContainerId).exec();

            d.waitDindStarted(hostContainerId);
        }

        @Override
        protected void after() {
            ensureContainerRemoved(d.getDockerCli(), HOST_CONTAINER_NAME);
        }
    }

    private NginxRegistryResource nginxContainer = new NginxRegistryResource();

    private class NginxRegistryResource extends DockerResource {
        private final String DATA_IMAGE_TAG = getClass().getSimpleName().toLowerCase();
        private final String HOST_CONTAINER_NAME = getClass().getCanonicalName() + "_host";
        public static final int CONTAINER_PORT = 80;

        private String hostContainerId;

        @NonNull
        public String getHostContainerId() {
            requireNonNull(hostContainerId);
            return hostContainerId;
        }

        public int getExposedPort() {
            final InspectContainerResponse inspect = d.getDockerCli().inspectContainerCmd(getHostContainerId()).exec();
            return DockerUtils.getExposedPort(inspect, CONTAINER_PORT);
        }

        @Override
        public void before() throws IOException {
            after();
            checkPathIT(new File(""));

            final File buildDir = folder.newFolder(getClass().getName());

            File resources = new File(String.format("src/test/resources/%s/docker",
                    NginxRegistryTest.class.getName().replace(".", "/"))
            );
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

            hostContainerId = d.getDockerCli().createContainerCmd(imageId)
                    .withName(HOST_CONTAINER_NAME)
                    .withHostConfig(HostConfig.newHostConfig()
                            .withRestartPolicy(RestartPolicy.alwaysRestart())
                            .withExtraHosts("registry:" + d.getHost())
                            .withPortBindings(PortBinding.parse(Integer.toString(CONTAINER_PORT))))
                    .exec()
                    .getId();

            d.getDockerCli().startContainerCmd(hostContainerId).exec();
        }

        @Override
        protected void after() {
            // remove host container
            ensureContainerRemoved(d.getDockerCli(), HOST_CONTAINER_NAME);

            // remove data image
            try {
                d.getDockerCli().removeImageCmd(DATA_IMAGE_TAG)
                        .withForce(true)
                        .exec();
                LOG.info("Removed image {}", DATA_IMAGE_TAG);
            } catch (NotFoundException ignore) {
            }
        }
    }

    private RegistryResource registryResource = new RegistryResource();

    private class RegistryResource extends DockerResource {
        public final String REGISTRY_IMAGE_NAME = "registry:2.3.0";
        public final String HOST_CONTAINER_NAME = getClass().getCanonicalName() + "_host";
        public static final int CONTAINER_PORT = 5000;

        private String hostContainerId;

        @NonNull
        public String getHostContainerId() {
            requireNonNull(hostContainerId);
            return hostContainerId;
        }

        @Override
        protected void before() throws Throwable {
            after();

            d.getDockerCli().pullImageCmd(REGISTRY_IMAGE_NAME).exec(new PullImageResultCallback()).awaitSuccess();

            hostContainerId = d.getDockerCli().createContainerCmd(REGISTRY_IMAGE_NAME)
                    .withHostConfig(HostConfig.newHostConfig()
                            .withRestartPolicy(RestartPolicy.alwaysRestart())
                            .withExtraHosts("registry:" + d.getHost())
                            .withPortBindings(PortBinding.parse(String.format(":%d:%s",
                                    44446, Integer.toString(CONTAINER_PORT)))))
                    .withName(HOST_CONTAINER_NAME)
                    .exec()
                    .getId();

            d.getDockerCli().startContainerCmd(hostContainerId).exec();
        }

        @Override
        protected void after() {
            ensureContainerRemoved(d.getDockerCli(), HOST_CONTAINER_NAME);

        }
    }

    @Rule
    public RuleChain chain = RuleChain.outerRule(registryResource)
            .around(nginxContainer)
            .around(dindResource);

    @Test
    public void testCliAuth() throws InterruptedException, IOException {
        checkPathIT(new File(""));

        DockerClientConfig clientConfig = new DefaultDockerClientConfig.Builder()
                .withDockerTlsVerify(false)
                .withDockerHost(String.format("tcp://%s:%d", d.getHost(), dindResource.getExposedPort()))
                .build();

        DockerCmdExecFactory dockerCmdExecFactory = new JerseyDockerCmdExecFactory();

        DockerClient dockerClient = DockerClientBuilder.getInstance(clientConfig)
                .withDockerCmdExecFactory(dockerCmdExecFactory)
                .build();

        final String imageName = String.format("%s:%d/image", d.getHost(), nginxContainer.getExposedPort());

        final File buildDir = folder.newFolder();
        writeStringToFile(new File(buildDir, "Dockerfile"),
                "FROM scratch\n" +
                        "MAINTAINER Kanstantsin Shautsou <kanstantsin.sha@gmail.com>");
        final String imageId = dockerClient.buildImageCmd(buildDir)
                .exec(new BuildImageResultCallback())
                .awaitImageId();

        dockerClient.tagImageCmd(imageId, imageName, "tag").exec();

        final AuthConfig authConfig = new AuthConfig();
        authConfig.withPassword("docker-registry-password");
        authConfig.withUsername("docker-registry-login");
        authConfig.withEmail("sdf@sdf.com");
        authConfig.withRegistryAddress(String.format("%s:%d", d.getHost(), nginxContainer.getExposedPort()));

        LOG.trace("Authconfig for push {}", authConfig);

        dockerClient.pushImageCmd(imageName)
                .withTag("tag")
                .withAuthConfig(authConfig)
                .exec(new PushImageResultCallback())
                .awaitSuccess();
    }

    //    @Test
    public void jenkinsPullWithAuth() {

    }
}
