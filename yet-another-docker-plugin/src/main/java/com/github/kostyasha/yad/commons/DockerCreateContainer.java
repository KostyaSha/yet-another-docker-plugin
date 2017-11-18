package com.github.kostyasha.yad.commons;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserListBoxModel;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.Bind;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.Device;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.Link;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.PortBinding;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.Volume;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.VolumesFrom;
import com.github.kostyasha.yad_docker_java.com.google.common.base.Function;
import com.github.kostyasha.yad_docker_java.com.google.common.base.Splitter;
import com.github.kostyasha.yad_docker_java.com.google.common.base.Strings;
import com.github.kostyasha.yad_docker_java.com.google.common.collect.Iterables;
import com.trilead.ssh2.Connection;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.github.kostyasha.yad.commons.DockerContainerRestartPolicyName.NO;
import static com.github.kostyasha.yad.utils.BindUtils.joinToStr;
import static com.github.kostyasha.yad.utils.BindUtils.splitAndFilterEmpty;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang.StringUtils.trimToNull;
import static org.apache.commons.lang.builder.ToStringStyle.SHORT_PREFIX_STYLE;

/**
 * Contains docker container create related settings.
 *
 * @see com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.command.CreateContainerCmdImpl
 */
public class DockerCreateContainer extends AbstractDescribableImpl<DockerCreateContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(DockerCreateContainer.class);

    /**
     * Field command
     */
    @CheckForNull
    private String command;

    @CheckForNull
    private String entrypoint;

    @CheckForNull
    private String hostname;

    @CheckForNull
    private List<String> dnsHosts;

    /**
     * Every String is volume specification
     */
    @CheckForNull
    private List<String> volumes;

    /**
     * Every String is volumeFrom specification
     */
    @CheckForNull
    private List<String> volumesFrom;

    @CheckForNull
    private List<String> environment;

    @CheckForNull
    private String bindPorts;

    @CheckForNull
    private Boolean bindAllPorts;

    @CheckForNull
    private Long memoryLimit;

    @CheckForNull
    private Integer cpuShares;

    @CheckForNull
    private Boolean privileged;

    @CheckForNull
    private Boolean tty;

    @CheckForNull
    private String macAddress;

    @CheckForNull
    private List<String> extraHosts;

    @CheckForNull
    private String networkMode;

    @CheckForNull
    private List<String> devices;

    @CheckForNull
    private String cpusetCpus;

    @CheckForNull
    private String cpusetMems;

    @CheckForNull
    private List<String> links;

    @CheckForNull
    private Long shmSize;

    @CheckForNull
    private DockerContainerRestartPolicy restartPolicy = new DockerContainerRestartPolicy(NO, 0);

    @CheckForNull
    private String workdir;

    @CheckForNull
    private String user;

    @DataBoundConstructor
    public DockerCreateContainer() {
    }

    @CheckForNull
    public Boolean getBindAllPorts() {
        return bindAllPorts;
    }

    @DataBoundSetter
    public void setBindAllPorts(Boolean bindAllPorts) {
        this.bindAllPorts = bindAllPorts;
    }

    @CheckForNull
    public String getBindPorts() {
        return bindPorts;
    }

    @Nonnull
    public Iterable<PortBinding> getPortMappings() {
        if (Strings.isNullOrEmpty(bindPorts)) {
            return Collections.emptyList();
        }

        return Iterables.transform(
                Splitter.on(' ')
                        .trimResults()
                        .omitEmptyStrings()
                        .split(bindPorts),
                new PortBindingFunction()
        );
    }

    private static class PortBindingFunction implements Function<String, PortBinding> {
        @Nullable
        public PortBinding apply(String s) {
            return PortBinding.parse(s);
        }
    }

    @DataBoundSetter
    public void setBindPorts(String bindPorts) {
        this.bindPorts = bindPorts;
    }

    // dns hosts
    @CheckForNull
    public List<String> getDnsHosts() {
        return dnsHosts;
    }

    public void setDnsHosts(List<String> dnsHosts) {
        this.dnsHosts = dnsHosts;
    }

    @Nonnull
    public String getDnsString() {
        return joinToStr(dnsHosts);
    }

    @DataBoundSetter
    public void setDnsString(String dnsString) {
        setDnsHosts(splitAndFilterEmpty(dnsString));
    }

    // hostname
    @CheckForNull
    public String getHostname() {
        return hostname;
    }

    @DataBoundSetter
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    // memory limit
    @CheckForNull
    public Long getMemoryLimit() {
        return memoryLimit;
    }

    @DataBoundSetter
    public void setMemoryLimit(Long memoryLimit) {
        this.memoryLimit = memoryLimit;
    }

    // privileged
    @CheckForNull
    public Boolean getPrivileged() {
        return privileged;
    }

    @DataBoundSetter
    public void setPrivileged(Boolean privileged) {
        this.privileged = privileged;
    }

    @CheckForNull
    public Boolean getTty() {
        return tty;
    }

    @DataBoundSetter
    public void setTty(Boolean tty) {
        this.tty = tty;
    }

    @CheckForNull
    public List<String> getVolumes() {
        return volumes;
    }

    public void setVolumes(List<String> volumes) {
        this.volumes = volumes;
    }

    @Nonnull
    public String getVolumesString() {
        return joinToStr(volumes);
    }

    @DataBoundSetter
    public void setVolumesString(String volumesString) {
        setVolumes(splitAndFilterEmpty(volumesString));
    }

    @CheckForNull
    public List<String> getVolumesFrom() {
        return volumesFrom;
    }

    public void setVolumesFrom(List<String> folumesFrom) {
        this.volumesFrom = folumesFrom;
    }

    @DataBoundSetter
    public void setVolumesFromString(String volumesFromString) {
        setVolumesFrom(splitAndFilterEmpty(volumesFromString));
    }

    @Nonnull
    public String getVolumesFromString() {
        return joinToStr(getVolumesFrom());
    }

    // mac address
    @CheckForNull
    public String getMacAddress() {
        return trimToNull(macAddress);
    }

    @DataBoundSetter
    public void setMacAddress(String macAddress) {
        this.macAddress = trimToNull(macAddress);
    }

    //cpuShares
    @CheckForNull
    public Integer getCpuShares() {
        return cpuShares;
    }

    @DataBoundSetter
    public void setCpuShares(Integer cpuShares) {
        this.cpuShares = cpuShares;
    }

    // command
    @CheckForNull
    public String getCommand() {
        return command;
    }

    @Nonnull
    public String[] getDockerCommandArray() {
        return getCommandArray(command);
    }

    @DataBoundSetter
    public void setCommand(String command) {
        this.command = command;
    }

    public String getEntrypoint() {
        return entrypoint;
    }

    public String[] getDockerEntrypointArray() {
        return getCommandArray(entrypoint);
    }

    @DataBoundSetter
    public void setEntrypoint(String entrypoint) {
        this.entrypoint = entrypoint;
    }

    // environment
    @CheckForNull
    public List<String> getEnvironment() {
        return environment;
    }

    public void setEnvironment(List<String> environment) {
        this.environment = environment;
    }

    @Nonnull
    public String getEnvironmentString() {
        return joinToStr(environment);
    }

    @DataBoundSetter
    public void setEnvironmentString(String environmentString) {
        setEnvironment(splitAndFilterEmpty(environmentString));
    }

    // extrahosts
    @CheckForNull
    public List<String> getExtraHosts() {
        return extraHosts;
    }

    public void setExtraHosts(List<String> extraHosts) {
        this.extraHosts = extraHosts;
    }

    @DataBoundSetter
    public void setExtraHostsString(String extraHostsString) {
        setExtraHosts(splitAndFilterEmpty(extraHostsString));
    }

    /**
     * String for expandableTextBox Jelly form. New-line separated.
     */
    @Nonnull
    public String getExtraHostsString() {
        return joinToStr(getExtraHosts());
    }

    // network mode
    @CheckForNull
    public String getNetworkMode() {
        return StringUtils.trimToNull(networkMode);
    }

    @DataBoundSetter
    public void setNetworkMode(String networkMode) {
        this.networkMode = trimToNull(networkMode);
    }

    // devices
    @Nonnull
    public List<String> getDevices() {
        return isNull(devices) ? Collections.EMPTY_LIST : devices;
    }

    public void setDevices(List<String> devices) {
        this.devices = devices;
    }

    public String getDevicesString() {
        return joinToStr(getDevices());
    }

    @DataBoundSetter
    public void setDevicesString(String devicesString) {
        setDevices(splitAndFilterEmpty(devicesString));
    }

    //
    public String getCpusetCpus() {
        return cpusetCpus;
    }

    @DataBoundSetter
    public void setCpusetCpus(String cpusetCpus) {
        this.cpusetCpus = cpusetCpus;
    }

    //
    public String getCpusetMems() {
        return cpusetMems;
    }

    @DataBoundSetter
    public void setCpusetMems(String cpusetMems) {
        this.cpusetMems = cpusetMems;
    }

    // links
    @Nonnull
    public List<String> getLinks() {
        return isNull(links) ? Collections.EMPTY_LIST : links;
    }

    public void setLinks(List<String> links) {
        this.links = links;
    }

    public String getLinksString() {
        return joinToStr(getLinks());
    }

    @DataBoundSetter
    public void setLinksString(String devicesString) {
        setLinks(splitAndFilterEmpty(devicesString));
    }

    @CheckForNull
    public Long getShmSize() {
        return shmSize;
    }

    @DataBoundSetter
    public void setShmSize(Long shmSize) {
        this.shmSize = shmSize;
    }

    @CheckForNull
    public DockerContainerRestartPolicy getRestartPolicy() {
        return restartPolicy;
    }

    @DataBoundSetter
    public void setRestartPolicy(DockerContainerRestartPolicy restartPolicy) {
        this.restartPolicy = restartPolicy;
    }

    @CheckForNull
    public String getWorkdir() {
        return workdir;
    }

    @DataBoundSetter
    public void setWorkdir(String workdir) {
        this.workdir = workdir;
    }

    @CheckForNull
    public String getUser() {
        return user;
    }

    @DataBoundSetter
    public void setUser(String user) {
        this.user = user;
    }

    /**
     * Fills user specified values
     *
     * @param containerConfig config for filling values
     * @return filled config
     */
    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "no npe in getters")
    public CreateContainerCmd fillContainerConfig(CreateContainerCmd containerConfig,
                                                  @CheckForNull java.util.function.Function<String, String> resolveVar) {
        if (StringUtils.isNotBlank(hostname)) {
            containerConfig.withHostName(hostname);
        }

        String[] cmd = getDockerCommandArray();
        if (cmd.length > 0) {
            containerConfig.withCmd(cmd);
        }

        String[] entry = getDockerEntrypointArray();
        if (entry.length > 0) {
            containerConfig.withEntrypoint(entry);
        }

        containerConfig.withPortBindings(Iterables.toArray(getPortMappings(), PortBinding.class));

        if (BooleanUtils.isTrue(getBindAllPorts())) {
            containerConfig.withPublishAllPorts(getBindAllPorts());
        }

        if (BooleanUtils.isTrue(getPrivileged())) {
            containerConfig.withPrivileged(getPrivileged());
        }

        if (getCpuShares() != null && getCpuShares() > 0) {
            containerConfig.withCpuShares(getCpuShares());
        }

        if (getMemoryLimit() != null && getMemoryLimit() > 0) {
            Long memoryInByte = getMemoryLimit() * 1024 * 1024;
            containerConfig.withMemory(memoryInByte);
        }

        if (CollectionUtils.isNotEmpty(getDnsHosts())) {
            containerConfig.withDns(getDnsHosts().toArray(new String[getDnsHosts().size()]));
        }

        // https://github.com/docker/docker/blob/ed257420025772acc38c51b0f018de3ee5564d0f/runconfig/parse.go#L182-L196
        if (CollectionUtils.isNotEmpty(getVolumes())) {
            ArrayList<Volume> vols = new ArrayList<>();
            ArrayList<Bind> binds = new ArrayList<>();

            for (String vol : getVolumes()) {
                if (nonNull(resolveVar)) vol = resolveVar.apply(vol);

                final String[] group = vol.split(":");
                if (group.length > 1) {
                    if (group[1].equals("/")) {
                        throw new IllegalArgumentException("Invalid bind mount: destination can't be '/'");
                    }

                    binds.add(Bind.parse(vol));
                } else if (vol.equals("/")) {
                    throw new IllegalArgumentException("Invalid volume: path can't be '/'");
                } else {
                    vols.add(new Volume(vol));
                }
            }

            containerConfig.withVolumes(vols.toArray(new Volume[vols.size()]));
            containerConfig.withBinds(binds.toArray(new Bind[binds.size()]));
        }

        if (CollectionUtils.isNotEmpty(getVolumesFrom())) {
            ArrayList<VolumesFrom> volFrom = new ArrayList<>();
            for (String volFromStr : getVolumesFrom()) {
                volFrom.add(new VolumesFrom(volFromStr));
            }

            containerConfig.withVolumesFrom(volFrom.toArray(new VolumesFrom[volFrom.size()]));
        }

        if (BooleanUtils.isTrue(getTty())) {
            containerConfig.withTty(getTty());
        }

        if (CollectionUtils.isNotEmpty(getEnvironment())) {
            containerConfig.withEnv(getEnvironment().toArray(new String[getEnvironment().size()]));
        }

        if (StringUtils.isNotBlank(getMacAddress())) {
            containerConfig.withMacAddress(getMacAddress());
        }

        if (CollectionUtils.isNotEmpty(getExtraHosts())) {
            containerConfig.withExtraHosts(getExtraHosts().toArray(new String[getExtraHosts().size()]));
        }

        if (StringUtils.isNotBlank(getNetworkMode())) {
            containerConfig.withNetworkMode(getNetworkMode());
        }

        if (!getDevices().isEmpty()) {
            containerConfig.withDevices(
                    getDevices().stream().map(Device::parse).collect(Collectors.toList())
            );
        }

        if (StringUtils.isNotBlank(getCpusetCpus())) {
            containerConfig.withCpusetCpus(getCpusetCpus());
        }

        if (StringUtils.isNotBlank(getCpusetMems())) {
            containerConfig.withCpusetMems(getCpusetMems());
        }

        if (!getLinks().isEmpty()) {
            containerConfig.withLinks(
                    getLinks().stream().map(Link::parse).collect(Collectors.toList())
            );
        }

        if (nonNull(shmSize)) {
            containerConfig.getHostConfig().withShmSize(shmSize);
        }

        if (nonNull(restartPolicy)) {
            containerConfig.withRestartPolicy(restartPolicy.getRestartPolicy());
        }

        if (StringUtils.isNotBlank(getWorkdir())) {
            containerConfig.withWorkingDir(nonNull(resolveVar) ? resolveVar.apply(workdir) : workdir);
        }

        if (StringUtils.isNotBlank(getUser())) {
            containerConfig.withUser(getUser());
        }

        return containerConfig;
    }


    public Object readResolve() {
        return this;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, SHORT_PREFIX_STYLE);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    private static String[] getCommandArray(String command) {
        String[] dockerCommandArray = new String[0];
        final ArrayList<String> commands = new ArrayList<>();
        if (StringUtils.isNotEmpty(command)) {

            // https://stackoverflow.com/questions/3366281/tokenizing-a-string-but-ignoring-delimiters-within-quotes
            String regex = "[\"\']([^\"]*)[\"\']|(\\S+)";

            Matcher m = Pattern.compile(regex).matcher(command);
            while (m.find()) {
                if (nonNull(m.group(1))) {
                    commands.add(m.group(1));
                } else {
                    commands.add(m.group(2));
                }
            }

            dockerCommandArray = commands.toArray(new String[commands.size()]);
        }

        return dockerCommandArray;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DockerCreateContainer> {

        @CheckReturnValue
        public FormValidation doCheckVolumesString(@QueryParameter String volumesString) {
            try {
                final List<String> strings = splitAndFilterEmpty(volumesString);
                for (String s : strings) {
                    if (s.equals("/")) {
                        return FormValidation.error("Invalid volume: path can't be '/'");
                    }

                    final String[] group = s.split(":");
                    if (group.length > 3) {
                        return FormValidation.error("Wrong syntax: " + s);
                    } else if (group.length == 2 || group.length == 3) {
                        if (group[1].equals("/")) {
                            return FormValidation.error("Invalid bind mount: destination can't be '/'");
                        }
                        Bind.parse(s);
                    } else if (group.length == 1) {
                        final Volume ignore = new Volume(s);
                    } else {
                        return FormValidation.error("Wrong line: " + s);
                    }
                }
            } catch (Throwable t) {
                return FormValidation.error(t.getMessage());
            }

            return FormValidation.ok();

        }

        public FormValidation doCheckVolumesFromString(@QueryParameter String volumesFromString) {
            try {
                final List<String> strings = splitAndFilterEmpty(volumesFromString);
                for (String volFrom : strings) {
                    VolumesFrom.parse(volFrom);
                }
            } catch (Throwable t) {
                return FormValidation.error(t, t.getMessage());
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckExtraHostsString(@QueryParameter String extraHostsString) {
            final List<String> extraHosts = splitAndFilterEmpty(extraHostsString);
            for (String extraHost : extraHosts) {
                if (extraHost.trim().split(":").length < 2) {
                    return FormValidation.error("Wrong extraHost: " + extraHost);
                }
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckDevicesString(@QueryParameter String devicesString) {
            final List<String> devicesStrings = splitAndFilterEmpty(devicesString);
            for (String deviceString : devicesStrings) {
                try {
                    Device.parse(deviceString);
                } catch (Exception ex) {
                    return FormValidation.error("Bad device configuration: " + deviceString, ex);
                }
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckLinksString(@QueryParameter String linksString) {
            final List<String> links = splitAndFilterEmpty(linksString);
            for (String linkString : links) {
                try {
                    Link.parse(linkString);
                } catch (Exception ex) {
                    return FormValidation.error("Bad link configuration: " + linkString, ex);
                }
            }

            return FormValidation.ok();
        }

        public static ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context) {
            return new SSHUserListBoxModel().withMatching(
                    SSHAuthenticator.matcher(Connection.class),
                    CredentialsProvider.lookupCredentials(
                            StandardUsernameCredentials.class,
                            context,
                            ACL.SYSTEM,
                            SSHLauncher.SSH_SCHEME)
            );
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Docker template base";
        }

    }
}
