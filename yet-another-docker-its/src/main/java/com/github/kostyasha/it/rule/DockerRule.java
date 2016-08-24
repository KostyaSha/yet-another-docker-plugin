package com.github.kostyasha.it.rule;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.github.kostyasha.it.other.JenkinsDockerImage;
import com.github.kostyasha.it.other.WaitMessageResultCallback;
import com.github.kostyasha.it.utils.DockerHPIContainerUtil;
import com.github.kostyasha.yad.commons.DockerImagePullStrategy;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.DockerCmdExecFactory;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.InspectImageResponse;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.exception.NotFoundException;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.BuildResponseItem;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.Container;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.ExposedPort;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.Image;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.PortBinding;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.Ports;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.VolumesFrom;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.DockerClientBuilder;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.LocalDirectorySSLConfig;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.NameParser;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.command.BuildImageResultCallback;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.command.PullImageResultCallback;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory;
import com.github.kostyasha.yad_docker_java.com.google.common.collect.Iterables;
import com.github.kostyasha.yad_docker_java.org.apache.commons.codec.digest.DigestUtils;
import com.github.kostyasha.yad_docker_java.org.apache.commons.io.FileUtils;
import com.github.kostyasha.yad_docker_java.org.apache.commons.lang.StringUtils;
import hudson.cli.CLI;
import hudson.cli.CLIConnectionFactory;
import hudson.cli.DockerCLI;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.hamcrest.MatcherAssert;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.github.kostyasha.it.other.JenkinsDockerImage.JENKINS_DEFAULT;
import static com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.DefaultDockerClientConfig.createDefaultConfigBuilder;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.codehaus.plexus.util.FileUtils.copyFile;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

/**
 * Connects to remote Docker Host and provides client
 *
 * @author Kanstantsin Shautsou
 */
public class DockerRule extends ExternalResource {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(DockerRule.class);

    public static final String CONTAINER_JAVA_OPTS = "JAVA_OPTS="
            // hack crap https://issues.jenkins-ci.org/browse/JENKINS-23232
//            + "-Dhudson.diyChunking=false "
//            + "-agentlib:jdwp=transport=dt_socket,server=n,address=192.168.99.1:5005,suspend=y "
            + "-Dhudson.remoting.Launcher.pingIntervalSec=-1 "
            + "-Dhudson.model.UpdateCenter.never=true "
            + "-Dhudson.model.LoadStatistics.clock=1000 "
//            + "-verbose:class "
            ;
    public static final String YAD_PLUGIN_NAME = "yet-another-docker-plugin";
    public static final String NL = "\n";
    public static final String DATA_IMAGE = "kostyasha/jenkins-data:sometest";
    public static final String DATA_CONTAINER_NAME = "jenkins_data";
    public static final String SLAVE_IMAGE_JNLP = "kostyasha/jenkins-slave:jdk-wget";

    @CheckForNull
    private DockerClient dockerClient;

    boolean cleanup = true;
    private Set<String> provisioned = new HashSet<>();
    //cache
    public Description description;
    public DefaultDockerClientConfig clientConfig;
    private DockerCmdExecFactory dockerCmdExecFactory;

    public DockerRule() {
    }

    public DockerRule(boolean cleanup) {
        this.cleanup = cleanup;
    }

//    public String getDockerUri() {
//        return new StringBuilder()
//                .append("tcp://")
//                .append(host).append(":").append(dockerPort)
//                .toString();
//    }

    public String getHost() {
        return clientConfig.getDockerHost().getHost();
    }

    public DockerClient getDockerCli() {
        return dockerClient;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        this.description = description;
        return super.apply(base, description);
    }

    @Override
    protected void before() throws Throwable {
        prepareDockerCli();
        //check dockerClient
        getDockerCli().infoCmd().exec();
        // ensure we have right ssh creds
//        checkSsh();
    }

    /**
     * Pull docker image on this docker host.
     */
    public void pullImage(DockerImagePullStrategy pullStrategy, String imageName) throws InterruptedException {
        LOG.info("Pulling image {} with {} strategy...", imageName, pullStrategy);
        final List<Image> images = getDockerCli().listImagesCmd().withShowAll(true).exec();

        NameParser.ReposTag repostag = NameParser.parseRepositoryTag(imageName);
        // if image was specified without tag, then treat as latest
        final String fullImageName = repostag.repos + ":" + (repostag.tag.isEmpty() ? "latest" : repostag.tag);

        boolean hasImage = Iterables.any(images, image -> Arrays.asList(image.getRepoTags()).contains(fullImageName));

        boolean pull = hasImage ?
                pullStrategy.pullIfExists(imageName) :
                pullStrategy.pullIfNotExists(imageName);

        if (pull) {
            LOG.info("Pulling image '{}' {}. This may take awhile...", imageName,
                    hasImage ? "again" : "since one was not found");

            long startTime = System.currentTimeMillis();
            //Identifier amiId = Identifier.fromCompoundString(ami);
            getDockerCli().pullImageCmd(imageName).exec(new PullImageResultCallback()).awaitSuccess();
            long pullTime = System.currentTimeMillis() - startTime;
            LOG.info("Finished pulling image '{}', took {} ms", imageName, pullTime);
        }
    }

    /**
     * Prepare cached DockerClient `dockerClient`.
     * Pick system/file connection settings.
     */
    private void prepareDockerCli() {
        clientConfig = createDefaultConfigBuilder()
                .build();

        dockerCmdExecFactory = new JerseyDockerCmdExecFactory();

        dockerClient = DockerClientBuilder.getInstance(clientConfig)
                .withDockerCmdExecFactory(dockerCmdExecFactory)
                .build();
    }

//    private void checkSsh() {
//        try (Ssh ssh = getSsh()) {
//            ssh.executeRemoteCommand("env");
//        }
//    }
//
//    /**
//     * @return ssh connection to docker host
//     */
//    public Ssh getSsh() {
//        try {
//            Ssh ssh = new Ssh(getHost());
//            ssh.getConnection().authenticateWithPassword(getSshUser(), getSshPass());
//            return ssh;
//        } catch (IOException e) {
//            throw new AssertionError("Failed to create ssh connection", e);
//        }
//    }

    /**
     * Docker data container with unique name based on docker data-image (also unique).
     * If we are refreshing container, then it makes sense rebuild data-image.
     */
    public String getDataContainerId(boolean forceRefresh) throws SettingsBuildingException,
            InterruptedException, IOException {
        final Map<String, File> pluginFiles = getPluginFiles();
        String dataContainerId = null;
        String dataContainerImage = null;

        LOG.debug("Checking whether data-container exist...");
        final List<Container> containers = getDockerCli().listContainersCmd().withShowAll(true).exec();
        OUTER:
        for (Container container : containers) {
            final String[] names = container.getNames();
            for (String name : names) {
                // docker adds "/" before container name
                if (name.equals("/" + DATA_CONTAINER_NAME)) {
                    dataContainerId = container.getId();
                    dataContainerImage = container.getImage();
                    LOG.debug("Data container exists {}, based on image {}", dataContainerId, dataContainerImage);
                    break OUTER;
                }
            }
        }

        final String dataImage = getDataImage(forceRefresh, pluginFiles, DATA_IMAGE);
        final boolean dataImagesEquals = dataImage.equals(dataContainerImage);
        LOG.debug("Data image is the same: {}", dataImagesEquals);

        if (nonNull(dataContainerId) && (forceRefresh || !dataImagesEquals)) {
            LOG.info("Removing data-container. ForceRefresh: {}", forceRefresh);
            try {
                getDockerCli().removeContainerCmd(dataContainerId)
                        .withForce(true) // force is not good
                        .withRemoveVolumes(true)
                        .exec();
            } catch (NotFoundException ignored) {
            }
            dataContainerId = null;
        }

        if (isNull(dataContainerId)) {
            LOG.debug("Data container doesn't exist, creating...");
            final CreateContainerResponse containerResponse = getDockerCli().createContainerCmd(dataImage)
                    .withName(DATA_CONTAINER_NAME)
                    .withCmd("/bin/true")
                    .exec();
            dataContainerId = containerResponse.getId();
        }

        if (isNull(dataContainerId)) {
            throw new IllegalStateException("Container id can't be null.");
        }

        getDockerCli().inspectContainerCmd(dataContainerId).exec().getId();

        LOG.debug("Data containerId: '{}'", dataContainerId);
        return dataContainerId;
    }

    /**
     * Ensures that data-image exist on docker.
     * Rebuilds data image if content (plugin hashes) doesn't match.
     *
     * @param forceUpdate rebuild data image.
     */
    public String getDataImage(boolean forceUpdate, final Map<String, File> pluginFiles, String imageName)
            throws IOException, SettingsBuildingException, InterruptedException {
        String existedDataImage = null;

        final List<Image> images = getDockerCli().listImagesCmd()
                .withShowAll(true)
                .exec();
        OUTER:
        for (Image image : images) {
            final String[] repoTags = image.getRepoTags();
            for (String repoTag : repoTags) {
                if (repoTag.equals(imageName)) {
                    existedDataImage = image.getId();
                    break OUTER;
                }
            }
        }

        if (nonNull(existedDataImage) && (forceUpdate || !isActualDataImage(pluginFiles, existedDataImage))) {
            LOG.info("Removing data-image.");
            //TODO https://github.com/docker-java/docker-java/issues/398
            getDockerCli().removeImageCmd(existedDataImage).withForce(true).exec();
            existedDataImage = null;
        }

        if (isNull(existedDataImage)) {
            LOG.debug("Preparing plugin files for");

//            final Set<Artifact> artifactResults = resolvePluginsFor(THIS_PLUGIN);
//            LOG.debug("Resolved plugins: {}", artifactResults);
////            System.out.println(artifactResults);
//            artifactResults.stream().forEach(artifact ->
//                            pluginFiles.put(
//                                    // {@link hudson.PluginManager.copyBundledPlugin()}
////                            artifact.getArtifactId() + "." + artifact.getExtension(),
//                                    artifact.getArtifactId() + ".jpi",
//                                    artifact.getFile()
//                            )
//            );
            existedDataImage = buildImage(pluginFiles);
        }

        return existedDataImage;
    }

    /**
     * Directory for placing temp files for building images.
     * Every class should create some subdirectory for their needs.
     * Path ends with '/';
     */
    public static String getDockerItDir() {
        return targetDir().getAbsolutePath() + "/docker-it/";
    }

    @Nonnull
    public static Map<String, File> getPluginFiles() {
        final Map<String, File> pluginFiles = new HashMap<>();
        final File pluginsDir = new File(getDockerItDir() + "/plugins");
        final File[] files = pluginsDir.listFiles();
        requireNonNull(files, "Files must exist in plugin dir");
        for (File plugin : files) {
            pluginFiles.put(plugin.getName().replace(".hpi", ".jpi"), plugin);
        }

        return pluginFiles;
    }

    public boolean isActualDataImage(Map<String, File> plugins, String existedDataImage) throws IOException {
        //TODO verify that there is at least one .jpi/hpi in data image, either it was build without
        //executed maven step that puts hpi's into directory for building image
        if (isNull(existedDataImage)) {
            return false;
        }

        final InspectImageResponse imageResponse = getDockerCli().inspectImageCmd(existedDataImage).exec();
        final Map<String, String> imageLabels = imageResponse.getContainerConfig().getLabels();
        final Map<String, String> generateLabels = generateLabels(plugins);

        //filter non plugin labels
        for (Map.Entry<String, String> entry : imageLabels.entrySet()) {
            if (!entry.getKey().endsWith(".jpi")) {
                imageLabels.remove(entry.getKey());
            }
        }

        boolean equals = imageLabels.entrySet().equals(generateLabels.entrySet());

        LOG.debug("Existed data image {} is actual: {}", existedDataImage, equals);
        return equals;
    }

    /**
     * Build docker image containing specified plugins.
     */
    public String buildImage(Map<String, File> plugins) throws IOException, InterruptedException {
        LOG.debug("Building image for {}", plugins);
//        final File tempDirectory = TempFileHelper.createTempDirectory("build-image", targetDir().toPath());
        final File buildDir = new File(targetDir().getAbsolutePath() + "/docker-it/build-image");
        if (buildDir.exists()) {
            FileUtils.deleteDirectory(buildDir);
        }
        if (!buildDir.mkdirs()) {
            throw new IllegalStateException("Can't create temp directory " + buildDir.getAbsolutePath());
        }

        final String dockerfile = generateDockerfileFor(plugins);
        final File dockerfileFile = new File(buildDir, "Dockerfile");
        FileUtils.writeStringToFile(dockerfileFile, dockerfile);

        final File buildHomePath = new File(buildDir, JenkinsDockerImage.JENKINS_DEFAULT.homePath);
        final File jenkinsConfig = new File(buildHomePath, "config.xml");
        DockerHPIContainerUtil.copyResourceFromClass(DockerHPIContainerUtil.class, "config.xml", jenkinsConfig);

        final File pluginDir = new File(buildHomePath, "/plugins/");
        if (!pluginDir.mkdirs()) {
            throw new IllegalStateException("Can't create dirs " + pluginDir.getAbsolutePath());
        }

        for (Map.Entry<String, File> entry : plugins.entrySet()) {
            final File dst = new File(pluginDir + "/" + entry.getKey());
            copyFile(entry.getValue(), dst);
        }

        try {
            LOG.info("Building data-image...");
            return getDockerCli().buildImageCmd(buildDir)
                    .withTag(DATA_IMAGE)
                    .withForcerm(true)
                    .exec(new BuildImageResultCallback() {
                        public void onNext(BuildResponseItem item) {
                            String text = item.getStream();
                            if (nonNull(text)) {
                                LOG.debug(StringUtils.removeEnd(text, NL));
                            }
                            super.onNext(item);
                        }
                    })
                    .awaitImageId();
        } finally {
            buildDir.delete();
        }

    }

    /**
     * return {@link CLI} for specified jenkins container ID
     */
    public DockerCLI createCliForContainer(String containerId) throws IOException, InterruptedException {
        LOG.trace("Creating cli for container {}.", containerId);
        final InspectContainerResponse inspect = getDockerCli().inspectContainerCmd(containerId).exec();
        return createCliForInspect(inspect);
    }


    /**
     * Run, record and remove after test container with jenkins.
     *
     * @param forceRefresh enforce data container and data image refresh
     */
    public String runFreshJenkinsContainer(DockerImagePullStrategy pullStrategy, boolean forceRefresh)
            throws IOException, SettingsBuildingException, InterruptedException {
        LOG.debug("Entering run fresh jenkins container.");
        pullImage(pullStrategy, JENKINS_DEFAULT.getDockerImageName());

        // labels attached to container allows cleanup container if it wasn't removed
        final Map<String, String> labels = new HashMap<>();
        labels.put("test.displayName", description.getDisplayName());

        LOG.debug("Removing existed container before");
        try {
            final List<Container> containers = getDockerCli().listContainersCmd().withShowAll(true).exec();
            for (Container c : containers) {
                if (c.getLabels().equals(labels)) { // equals? container labels vs image labels?
                    LOG.debug("Removing {}, for labels: '{}'", c, labels);
                    getDockerCli().removeContainerCmd(c.getId())
                            .withForce(true)
                            .exec();
                    break;
                }
            }
        } catch (NotFoundException ex) {
            LOG.debug("Container wasn't found, that's ok");
        }

        LOG.debug("Recreating data container without data-image doesn't make sense, so reuse boolean.");
        String dataContainerId = getDataContainerId(forceRefresh);
        final String id = getDockerCli().createContainerCmd(JENKINS_DEFAULT.getDockerImageName())
                .withEnv(CONTAINER_JAVA_OPTS)
                .withExposedPorts(new ExposedPort(JENKINS_DEFAULT.tcpPort))
                .withPortSpecs(String.format("%d/tcp", JENKINS_DEFAULT.tcpPort))
                .withPortBindings(PortBinding.parse("0.0.0.0:48000:48000"))
//                .withPortBindings(PortBinding.parse("0.0.0.0:48000:48000"), PortBinding.parse("0.0.0.0:50000:50000"))
                .withVolumesFrom(new VolumesFrom(dataContainerId))
                .withLabels(labels)
                .withPublishAllPorts(true)
                .exec()
                .getId();
        provisioned.add(id);

        LOG.debug("Starting container");
        getDockerCli().startContainerCmd(id).exec();
        return id;
    }

    /**
     * @return maven module 'target' dir for placing temp files/dirs.
     */
    public static File targetDir() {
        ///i.e. /Users/integer/src/github/yet-another-docker-plugin/yet-another-docker-its/target/classes/
        String relPath = DockerRule.class.getProtectionDomain().getCodeSource().getLocation().getFile();
        return new File(relPath).getParentFile();
    }

    /**
     * 'Dockerfile' as String based on sratch for placing plugins.
     */
    public String generateDockerfileFor(Map<String, File> plugins) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("FROM scratch").append(NL)
                .append("MAINTAINER Kanstantsin Shautsou <kanstantsin.sha@gmail.com>").append(NL)
                .append("COPY ./ /").append(NL)
                .append("VOLUME /usr/share/jenkins/ref").append(NL);

        for (Map.Entry<String, String> entry : generateLabels(plugins).entrySet()) {
            builder.append("LABEL ").append(entry.getKey()).append("=").append(entry.getValue()).append(NL);
        }

//        newClientBuilderForConnector.append("LABEL GENERATION_UUID=").append(UUID.randomUUID()).append(NL);

        return builder.toString();
    }

    public Map<String, String> generateLabels(Map<String, File> plugins) throws IOException {
        Map<String, String> labels = new HashMap<>();

        for (Map.Entry<String, File> entry : plugins.entrySet()) {
            final String sha256Hex = DigestUtils.sha256Hex(new FileInputStream(entry.getValue()));
            labels.put(entry.getKey(), sha256Hex);
        }

        return labels;
    }

    /**
     * Return {@link CLI} for jenkins docker container.
     * // TODO no way specify CliPort (hacky resolution {@link DockerCLI})
     */
    public DockerCLI createCliForInspect(InspectContainerResponse inspect)
            throws IOException, InterruptedException {
        LOG.debug("Creating CLI for {}", inspect);
        Integer httpPort = null;
        // CLI mess around ports
        Integer cliPort = null; // should be this
        Integer jnlpAgentPort = null; // but in reality used this

        final Map<ExposedPort, Ports.Binding[]> bindings = inspect.getNetworkSettings().getPorts().getBindings();
        LOG.trace("Bindings: {}", bindings);
        for (Map.Entry<ExposedPort, Ports.Binding[]> entry : bindings.entrySet()) {
            if (entry.getKey().getPort() == JENKINS_DEFAULT.httpPort) {
                httpPort = Integer.valueOf(entry.getValue()[0].getHostPortSpec());
            }
            if (entry.getKey().getPort() == JENKINS_DEFAULT.tcpPort) {
                final Ports.Binding binding = entry.getValue()[0];
                jnlpAgentPort = Integer.valueOf(binding.getHostPortSpec());
            }
            if (entry.getKey().getPort() == JENKINS_DEFAULT.jnlpPort) {
                cliPort = Integer.valueOf(entry.getValue()[0].getHostPortSpec());
            }
        }
        LOG.trace("Creating URL {}", bindings);
        final URL url = new URL("http://" + getHost() + ":" + httpPort.toString());
        LOG.trace("Created URL {}", url.toExternalForm());

        if (isNull(jnlpAgentPort)) {
            throw new IOException("Can't get jnlpPort." + bindings.toString());
        }
        return createCliWithWait(url, jnlpAgentPort);
    }

    /**
     * Create DockerCLI connection against specified jnlpSlaveAgent port
     */
    private DockerCLI createCliWithWait(URL url, int port) throws InterruptedException, IOException {
        DockerCLI tempCli = null;
        boolean connected = false;
        int i = 0;
        while (i <= 10 && !connected) {
            i++;
            try {
                final CLIConnectionFactory factory = new CLIConnectionFactory().url(url);
                tempCli = new DockerCLI(factory, port);
                final String channelName = tempCli.getChannel().getName();
                if (channelName.contains("CLI connection to")) {
                    tempCli.upgrade();
                    connected = true;
                    LOG.debug(channelName);
                } else {
                    LOG.debug("Cli connection is not via CliPort '{}'. Sleeping for 5s...", channelName);
                    tempCli.close();
                    Thread.sleep(5 * 1000);
                }
            } catch (IOException e) {
                LOG.debug("Jenkins is not available. Sleeping for 5s...", e.getMessage());
                Thread.sleep(5 * 1000);
            }
        }

        if (!connected) {
            throw new IOException("Can't connect to {}" + url.toString());
        }

        LOG.info("Jenkins future {}", url);
        LOG.info("Jenkins future {}/configure", url);
        LOG.info("Jenkins future {}/log/all", url);
        return tempCli;
    }

    @Override
    protected void after() {
        if (cleanup) {
            provisioned.forEach(id ->
                    getDockerCli().removeContainerCmd(id)
                            .withForce(true)
                            .withRemoveVolumes(true)
                            .exec()
            );
        }
    }

    public DockerServerCredentials getDockerServerCredentials() throws IOException {
        final LocalDirectorySSLConfig sslContext = (LocalDirectorySSLConfig) clientConfig.getSSLConfig();

        assertThat("DockerCli must be connected via SSL", sslContext, notNullValue());

        String certPath = sslContext.getDockerCertPath();

        final String keypem = FileUtils.readFileToString(new File(certPath + "/" + "key.pem"));
        final String certpem = FileUtils.readFileToString(new File(certPath + "/" + "cert.pem"));
        final String capem = FileUtils.readFileToString(new File(certPath + "/" + "ca.pem"));

        return new DockerServerCredentials(
                CredentialsScope.GLOBAL, // scope
                null, // name
                null, //desc
                keypem,
                certpem,
                capem
        );
    }

    public void waitDindStarted(String hostContainerId) throws InterruptedException, IOException {
        waitLogMsg(hostContainerId, "API listen on");
    }

    public void waitLogMsg(String containerId, String msg) throws InterruptedException, IOException {
        final WaitMessageResultCallback callback = new WaitMessageResultCallback(msg);

        getDockerCli().logContainerCmd(containerId)
                .withTailAll()
                .withStdOut(true)
                .withStdErr(true)
                .withFollowStream(true)
                .exec(callback)
                .awaitCompletion(60, SECONDS);

        assertThat("Didn't found msg '" + msg + "' in log.", callback.getFound(), is(true));
        callback.close();
    }
}
