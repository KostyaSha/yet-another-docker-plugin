package com.github.kostyasha.it.other;

/**
 * Jenkins Docker image key values.
 * 48000 is hardcoded by data-image.
 * <p>
 * See https://github.com/jenkinsci/jenkins/commit/653fbdb65024b1b528e21f682172885f7111bba9
 * @author Kanstantsin Shautsou
 */
public class JenkinsDockerImage {
    public static final JenkinsDockerImage JENKINS_1_609_3 = new JenkinsDockerImage(
            "jenkins", "1.609.3", "/usr/share/jenkins/ref/", 8080, 48000, 50000
    );

    public static final JenkinsDockerImage JENKINS_1_625_3 = new JenkinsDockerImage(
            "jenkins", "1.625.3", "/usr/share/jenkins/ref/", 8080, 48000, 50000
    );

    public static final JenkinsDockerImage JENKINS_DEFAULT = JENKINS_1_625_3;

    public final String name;
    public final String tag;
    public final String homePath;
    public final int httpPort;
    public final int tcpPort;
    public int jnlpPort;

    public JenkinsDockerImage(final String name, final String tag, final String homePath, final int httpPort,
                              final int tcpPort, final int jnlpPort) {
        this.name = name;
        this.tag = tag;
        this.homePath = homePath;
        this.httpPort = httpPort;
        this.tcpPort = tcpPort;
        this.jnlpPort = jnlpPort;
    }

    public String getDockerImageName() {
        return name + ":" + tag;
    }
}
