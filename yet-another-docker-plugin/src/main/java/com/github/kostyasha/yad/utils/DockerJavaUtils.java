package com.github.kostyasha.yad.utils;

import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.AuthConfig;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.AuthConfigurations;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.NameParser;

import javax.annotation.CheckForNull;

import static com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.NameParser.resolveRepositoryName;

/**
 * @author Kanstantsin Shautsou
 */
public final class DockerJavaUtils {
    private DockerJavaUtils() {
    }

    @CheckForNull
    public static AuthConfig getAuthConfig(String forImage, AuthConfigurations authConfigurations) {
        NameParser.ReposTag reposTag = NameParser.parseRepositoryTag(forImage);

        final NameParser.HostnameReposName hostnameReposName = resolveRepositoryName(reposTag.repos);

        return authConfigurations.getConfigs().getOrDefault(hostnameReposName.hostname, null);
    }
}
