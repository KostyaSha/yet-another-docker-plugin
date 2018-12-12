package com.github.kostyasha.yad;

import com.github.kostyasha.yad.other.cloudorder.DockerCloudOrder;
import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;

import javax.annotation.CheckForNull;

@Extension
@Symbol("dockerSettings")
public class DockerGlobalConfiguration extends GlobalConfiguration {
    private DockerCloudOrder cloudOrder;

    public DockerGlobalConfiguration() {
        load();
    }

    @CheckForNull
    public DockerCloudOrder getCloudOrder() {
        return cloudOrder;
    }

    public void setCloudOrder(DockerCloudOrder cloudOrder) {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);

        this.cloudOrder = cloudOrder;
        save();
    }

    public static DockerGlobalConfiguration dockerGlobalConfig() {
        return Jenkins.getInstance().getInjector().getInstance(DockerGlobalConfiguration.class);
    }

}
