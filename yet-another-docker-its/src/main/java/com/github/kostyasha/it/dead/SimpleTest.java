package com.github.kostyasha.it.dead;

import com.github.kostyasha.it.rule.DockerRule;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import hudson.cli.DockerCLI;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import jenkins.model.Jenkins;
import org.jenkinsci.remoting.RoleChecker;
import org.junit.ClassRule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

import static com.github.kostyasha.it.utils.DockerHPIContainerUtil.getResource;
import static com.github.kostyasha.yad.commons.DockerImagePullStrategy.PULL_ALWAYS;
import static org.junit.Assert.assertTrue;

/**
 * @author Kanstantsin Shautsou
 */
public class SimpleTest implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(SimpleTest.class);

    @ClassRule
    public static DockerRule d = new DockerRule(false);


    @Test
    public void configureWithGroovyHack() throws Exception {
        String jenkinsId = d.runFreshJenkinsContainer(PULL_ALWAYS, true);
        final DockerCLI cli = d.createCliForContainer(jenkinsId);

        try (Channel channel = cli.getChannel()) {
            final String resource = getResource(getClass(), "AddDocker.groovy");

            assertTrue(channel.call(
                    new Callable<Boolean, Exception>() {
                        @Override
                        public void checkRoles(RoleChecker checker) throws SecurityException {
                        }

                        @Override
                        public Boolean call() throws Exception {
                            final Binding binding = new Binding();
                            binding.setVariable("testVar", "testValue");

                            final ClassLoader cl = Jenkins.getInstance().pluginManager.uberClassLoader;
                            GroovyShell shell = new GroovyShell(cl, binding);
                            return (Boolean) shell.evaluate(resource);
                        }

                        private static final long serialVersionUID = 1L;
                    })
            );

        } finally {
            cli.close();
        }
    }
}
