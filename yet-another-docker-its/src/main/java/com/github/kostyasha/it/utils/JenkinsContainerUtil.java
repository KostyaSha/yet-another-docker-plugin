package com.github.kostyasha.it.utils;

import com.github.kostyasha.it.rule.DockerRule;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.model.ExposedPort;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.model.Ports;
import hudson.cli.CLI;
import org.codehaus.plexus.logging.LoggerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

/**
 * @author Kanstantsin Shautsou
 */
public class JenkinsContainerUtil {
    private static final Logger LOG = LoggerFactory.getLogger(JenkinsContainerUtil.class);

    private JenkinsContainerUtil() {
    }


}
