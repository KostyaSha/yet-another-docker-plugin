package com.github.kostyasha.it.rule;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.github.kostyasha.it.other.JenkinsDockerImage;
import com.github.kostyasha.it.utils.DockerHPIContainerUtil;
import com.github.kostyasha.yad.commons.DockerImagePullStrategy;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.DockerException;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.NotFoundException;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.model.BuildResponseItem;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.model.Container;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.model.ExposedPort;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.model.Image;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.model.PortBinding;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.model.Ports;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.model.VolumesFrom;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.DockerClientBuilder;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.DockerClientConfig;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.LocalDirectorySSLConfig;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.SSLConfig;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.command.BuildImageResultCallback;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.command.PullImageResultCallback;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.jaxrs.DockerCmdExecFactoryImpl;
import com.github.kostyasha.yad.docker_java.org.apache.commons.codec.digest.DigestUtils;
import com.github.kostyasha.yad.docker_java.org.apache.commons.io.FileUtils;
import com.github.kostyasha.yad.docker_java.org.apache.commons.io.IOUtils;
import com.github.kostyasha.yad.docker_java.org.apache.commons.lang.StringUtils;
import hudson.cli.CLI;
import hudson.cli.CLIConnectionFactory;
import hudson.cli.DockerCLI;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials;
import org.jenkinsci.test.acceptance.Ssh;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.qatools.clay.aether.AetherException;

import javax.annotation.CheckForNull;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static java.util.Objects.isNull;
import static org.codehaus.plexus.util.FileUtils.copyFile;

/**
 * Connects to remote Docker Host and provides client
 *
 * @author Kanstantsin Shautsou
 */
public class DockerRule extends ExternalResource implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(DockerRule.class);

    public static final String CONTAINER_JAVA_OPTS = "JAVA_OPTS="
            // hack crap https://issues.jenkins-ci.org/browse/JENKINS-23232
//            + "-Dhudson.diyChunking=false "
            + "-agentlib:jdwp=transport=dt_socket,server=n,address=192.168.99.1:5005,suspend=y "
            + "-Dhudson.remoting.Launcher.pingIntervalSec=-1 "
            + "-Dhudson.model.UpdateCenter.never=true "
            + "-Dhudson.model.LoadStatistics.clock=1000 "
//            + "-verbose:class "
            ;
    public static final String YAD_PLUGIN_NAME = "yet-another-docker-plugin";
    public static final String NL = "\n";
    public static final String THIS_PLUGIN = "com.github.kostyasha.yet-another-docker:yet-another-docker-plugin:hpi:0.1.0-SNAPSHOT";
    public static final String DATA_IMAGE = "kostyasha/jenkins-data:sometest";
    public static final String DATA_CONTAINER_NAME = "jenkins_data";


    @CheckForNull
    public DockerRule outerRule;

    @CheckForNull
    public DockerClient cli;

    private String sshUser = "docker";
    private String sshPass = "tcuser";
    private String host = "192.168.99.100";
    private int dockerPort = 2376;
    private boolean useTls = true;

    boolean cleanup = true;
    private Set<String> provisioned = new HashSet<>();
    //cache
    public transient Description description;
    public DockerClientConfig clientConfig;

    public DockerRule(boolean cleanup) {
        this.cleanup = cleanup;
    }

    public DockerRule(String host) {
        this.host = host;
    }

    public String getDockerUri() {
        return new StringBuilder()
                .append(useTls ? "https" : "http").append("://")
                .append(host).append(":").append(dockerPort)
                .toString();
    }

    public String getHost() {
        return host;
    }

    public String getSshUser() {
        return sshUser;
    }

    public String getSshPass() {
        return sshPass;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        this.description = description;
        return super.apply(base, description);
    }

    @Override
    protected void before() throws Throwable {
        prepareDockerCli();
        //check cli
        cli.infoCmd().exec();
        // ensure we have right ssh creds
        checkSsh();
    }

    /**
     * Pull docker image on this docker host.
     */
    public void pullImage(DockerImagePullStrategy pullStrategy, String image) throws InterruptedException {
        LOG.info("Pulling image {} with {}...", image, pullStrategy);
        final List<Image> images = cli.listImagesCmd().withShowAll(true).exec();
        boolean imageExists = false;

        for (Image candidateImage : images) {
            if (candidateImage.getId().equals(image)) {
                imageExists = true;
                break;
            }
        }

        // TODO implement strategies!

        cli.pullImageCmd(image)
                .exec(new PullImageResultCallback())
                .awaitSuccess();
    }

    /**
     * Prepare cached DockerClient `cli`
     */
    private void prepareDockerCli() {
        clientConfig = DockerClientConfig.createDefaultConfigBuilder()
                .withUri(getDockerUri())
                .build();

        DockerCmdExecFactoryImpl dockerCmdExecFactory = new DockerCmdExecFactoryImpl()
                .withReadTimeout(0)
                .withConnectTimeout(10000);

        cli = DockerClientBuilder.getInstance(clientConfig)
                .withDockerCmdExecFactory(dockerCmdExecFactory)
                .build();
    }

    private void checkSsh() {
        try (Ssh ssh = getSsh()) {
            ssh.executeRemoteCommand("env");
        }
    }

    /**
     * @return ssh connection to docker host
     */
    public Ssh getSsh() {
        try {
            Ssh ssh = new Ssh(getHost());
            ssh.getConnection().authenticateWithPassword(getSshUser(), getSshPass());
            return ssh;
        } catch (IOException e) {
            throw new AssertionError("Failed to create ssh connection", e);
        }
    }

//    public Map<String, String> getDockerVars() {
//        final DockerClientConfig config = DockerClientConfig.createDefaultConfigBuilder().build();
//        config.
//    }

    /**
     * Docker data container with unique name based on docker data-image (also unique).
     * If we are refreshing container, then it makes sense rebuild data-image.
     */
    public String getDataContainerId(boolean refresh) throws AetherException, SettingsBuildingException,
            InterruptedException, IOException {
        boolean dataContainerExist = false;
        String dataContainerId = null;
        LOG.debug("Checking whether data-container exist...");
        final List<Container> containers = cli.listContainersCmd().withShowAll(true).exec();
        for (Container container : containers) {
            final String[] names = container.getNames();
            for (String name : names) {
                // docker adds "/" before container name
                if (name.equals("/" + DATA_CONTAINER_NAME)) {
                    dataContainerExist = true;
                    dataContainerId = container.getId();
                }
            }
        }

        if (dataContainerExist && refresh) {
            LOG.info("Removing data-container for refresh.");
            try {
                cli.removeContainerCmd(dataContainerId)
                        .withForce()
                        .withRemoveVolumes(true)
                        .exec();
            } catch (NotFoundException ignored) {
            }
            dataContainerExist = false;
        }

        ensureDataImageExist(refresh);

        if (!dataContainerExist) {
            LOG.debug("Data container doesn't exist, creating...");
            final CreateContainerResponse containerResponse = cli.createContainerCmd(DATA_IMAGE)
                    .withName(DATA_CONTAINER_NAME)
                    .withCmd("/bin/true")
                    .exec();
            LOG.warn("Create warnings {}", containerResponse.getWarnings());
            dataContainerId = containerResponse.getId();
        }

        if (isNull(dataContainerId)) {
            throw new IllegalStateException("Container id can't be null.");
        }

        cli.inspectContainerCmd(dataContainerId).exec().getId();

        LOG.debug("Data containerId: '{}'", dataContainerId);
        return dataContainerId;
    }

    /**
     * Ensures that data-image exist on docker.
     * TODO optimise and exclude rebuild if hashes from label are the same.
     *
     * @param refresh rebuild data image
     */
    public void ensureDataImageExist(boolean refresh)
            throws IOException, AetherException, SettingsBuildingException, InterruptedException {
        // prepare data image
        boolean dataImageExist = false;
        final List<Image> images = cli.listImagesCmd()
                .withShowAll(true)
                .exec();
        for (Image image : images) {
            final String[] repoTags = image.getRepoTags();
            for (String repoTag : repoTags) {
                if (repoTag.equals(DATA_IMAGE)) {
                    dataImageExist = true;
                }
            }
        }

        if (dataImageExist && refresh) {
            LOG.info("Removing data-image.");
            // https://github.com/docker-java/docker-java/issues/398
            cli.removeImageCmd(DATA_IMAGE).withForce().exec();
            dataImageExist = false;
        }

        if (!dataImageExist) {
            LOG.debug("Preparing plugin files for");
            final Map<String, File> pluginFiles = new HashMap<>();

            final File pluginsDir = new File(targetDir().getAbsolutePath() + "/docker-it/plugins");
            for (File plugin : pluginsDir.listFiles()) {
                pluginFiles.put(plugin.getName().replace(".hpi", ".jpi"), plugin);
            }

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

            buildImageFor(pluginFiles);
        }
    }


    /**
     * return {@link CLI} for specified jenkins container ID
     */
    public DockerCLI createCliForContainer(String containerId) throws IOException, InterruptedException {
        final InspectContainerResponse inspect = cli.inspectContainerCmd(containerId).exec();
        return createCliForInspect(inspect);
    }


    /**
     * Run, record and remove after test container with jenkins.
     */
    public String runFreshJenkinsContainer(CreateContainerCmd cmd,
                                           DockerImagePullStrategy pullStrategy,
                                           boolean refreshDataContainer)
            throws IOException, AetherException, SettingsBuildingException, InterruptedException {
        pullImage(pullStrategy, cmd.getImage());

        //remove existed before
        try {
            final List<Container> containers = cli.listContainersCmd().withShowAll(true).exec();
            for (Container c : containers) {
                if (c.getLabels().equals(cmd.getLabels())) {
                    cli.removeContainerCmd(c.getId())
                            .withForce()
                            .exec();
                    break;
                }
            }
        } catch (NotFoundException ex) {
            // that's ok;
        }

        // recreating data container without data-image doesn't make sense, so reuse boolean
        String dataContainerId = getDataContainerId(refreshDataContainer);
        final String id = cmd
                .withEnv(CONTAINER_JAVA_OPTS)
                .withExposedPorts(new ExposedPort(48000))
                .withPortSpecs("48000/tcp")
                .withPortBindings(PortBinding.parse("0.0.0.0:48000:48000"))
//                .withPortBindings(PortBinding.parse("0.0.0.0:48000:48000"), PortBinding.parse("0.0.0.0:50000:50000"))
                .withVolumesFrom(new VolumesFrom(dataContainerId))
                .exec()
                .getId();
        provisioned.add(id);

        cli.startContainerCmd(id).exec();
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

        for (Map.Entry<String, File> entry : plugins.entrySet()) {
            final String s = DigestUtils.sha256Hex(new FileInputStream(entry.getValue()));
            builder.append("LABEL ").append(entry.getKey()).append("=").append(s).append(NL);
        }

        builder.append("LABEL GENERATION_UUID=").append(UUID.randomUUID()).append(NL);

        return builder.toString();
    }

    /**
     * Build docker image containing specified plugins.
     */
    public void buildImageFor(Map<String, File> plugins) throws IOException, InterruptedException {
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


        final File buildHomePath = new File(buildDir, JenkinsDockerImage.JENKINS_1_609_3.homePath);
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
            cli.buildImageCmd(buildDir)
                    .withTag(DATA_IMAGE)
                    .withForcerm()
                    .exec(new BuildImageResultCallback() {
                        public void onNext(BuildResponseItem item) {
                            String text = item.getStream();
                            if (text != null) {
                                LOG.debug(StringUtils.removeEnd(text, NL));
                            }
                            super.onNext(item);
                        }
                    })
                    .awaitImageId();
        } catch (DockerException ex) {
            buildDir.delete();
            throw ex;
        }

        buildDir.delete();
    }

    /**
     * Return {@link CLI} for jenkins docker container.
     * // TODO no way specify CliPort (hacky resolution {@link DockerCLI})
     */
    public DockerCLI createCliForInspect(InspectContainerResponse inspect)
            throws IOException, InterruptedException {
        Integer httpPort = null;
        // CLI mess around ports
        Integer cliPort = null; // should be this
        Integer jnlpAgentPort = null; // but in reality used this

        final Map<ExposedPort, Ports.Binding[]> bindings = inspect.getNetworkSettings().getPorts().getBindings();
        for (Map.Entry<ExposedPort, Ports.Binding[]> entry : bindings.entrySet()) {
            if (entry.getKey().getPort() == 8080) {
                httpPort = entry.getValue()[0].getHostPort();
            }
            if (entry.getKey().getPort() == 50000) {
                cliPort = entry.getValue()[0].getHostPort();
            }
            if (entry.getKey().getPort() == 48000) {
                final Ports.Binding binding = entry.getValue()[0];
                jnlpAgentPort = binding.getHostPort();
            }
        }

        final URL url = new URL("http://" + getHost() + ":" + httpPort.toString());

        if (jnlpAgentPort == null) {
            throw new IOException("Can't get jnlpPort." +  bindings.toString());
        }
        return createCliWithWait(url, jnlpAgentPort);
    }

    /**
     * Create DockerCLI connection against specified jnlpSlaveAgent port
     */
    private DockerCLI createCliWithWait(URL url, int port) throws InterruptedException, IOException {
        DockerCLI cli = null;
        boolean connected = false;
        int i = 0;
        while (i <= 10 && !connected) {
            i++;
            try {
                final CLIConnectionFactory factory = new CLIConnectionFactory().url(url);
                cli = new DockerCLI(factory, port);
                final String channelName = cli.getChannel().getName();
                // https://github.com/jenkinsci/jenkins/commit/653fbdb65024b1b528e21f682172885f7111bba9
                if (channelName.contains("CLI connection to")) {
                    cli.upgrade();
                    connected = true;
                    LOG.debug(channelName);
                } else {
                    LOG.debug("Cli connection is not via CliPort '{}'. Sleeping for 5s...", channelName);
                    cli.close();
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
        return cli;
    }

    @Override
    protected void after() {
        if (cleanup) {
            provisioned.stream().forEach(id ->
                    cli.removeContainerCmd(id)
                            .withForce()
                            .withRemoveVolumes(true)
                            .exec()
            );
        }
    }

    public DockerServerCredentials getDockerServerCredentials() throws IOException {
        String certPath = null;
        final SSLConfig sslConfig = clientConfig.getSslConfig();
        if (sslConfig instanceof LocalDirectorySSLConfig) {
            final LocalDirectorySSLConfig directorySSLConfig = (LocalDirectorySSLConfig) sslConfig;
            certPath = directorySSLConfig.getDockerCertPath();
        }

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
}
