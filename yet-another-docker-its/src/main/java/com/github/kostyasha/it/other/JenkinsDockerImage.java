package com.github.kostyasha.it.other;

/**
 * @author Kanstantsin Shautsou
 */
public class JenkinsDockerImage {
    public static final JenkinsDockerImage JENKINS_1_609_3 = new JenkinsDockerImage("jenkins", "1.609.3",
            "/usr/share/jenkins/ref/");

    public final String name;
    public final String tag;
    public final String homePath;

    public JenkinsDockerImage(final String name, final String tag, final String homePath) {
        this.name = name;
        this.tag = tag;
        this.homePath = homePath;
    }

    public String getDockerImageName() {
        return name + ":" + tag;
    }
}
