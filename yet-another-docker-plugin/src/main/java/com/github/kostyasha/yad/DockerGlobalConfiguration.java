package com.github.kostyasha.yad;

import com.github.kostyasha.yad.other.cloudorder.DockerCloudOrder;
import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;

@Extension
@Symbol("dockerSettings")
public class DockerGlobalConfiguration extends GlobalConfiguration {
    DockerCloudOrder cloudOrder;

    public DockerGlobalConfiguration() {
        load();
    }

    public DockerCloudOrder getCloudOrder() {
        return cloudOrder;
    }

    public void setCloudOrder(DockerCloudOrder cloudOrder) {
        this.cloudOrder = cloudOrder;
        save();
    }

    public static DockerGlobalConfiguration dockerGlobalConfig() {
        return Jenkins.getInstance().getInjector().getInstance(DockerGlobalConfiguration.class);
    }

}
