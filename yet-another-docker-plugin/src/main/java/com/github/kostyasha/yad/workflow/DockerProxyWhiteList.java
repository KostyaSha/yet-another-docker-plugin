package com.github.kostyasha.yad.workflow;

import hudson.Extension;
import org.jenkinsci.plugins.scriptsecurity.sandbox.Whitelist;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.ProxyWhitelist;

import java.util.Collection;

/**
 * @author Kanstantsin Shautsou
 */
@Extension
public class DockerProxyWhiteList extends ProxyWhitelist {
    public DockerProxyWhiteList(Collection<? extends Whitelist> delegates) {
        super(delegates);
    }
}
