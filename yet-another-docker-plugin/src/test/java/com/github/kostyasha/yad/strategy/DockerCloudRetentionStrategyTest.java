package com.github.kostyasha.yad.strategy;

import com.github.kostyasha.yad.DockerCloud;
import com.github.kostyasha.yad.DockerSlaveTemplate;
import hudson.slaves.Cloud;
import hudson.slaves.RetentionStrategy;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerCloudRetentionStrategyTest {
    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @LocalData
    @Test
    public void testConfig() {
        final Cloud cloud = jenkinsRule.getInstance().getCloud("ff");
        assertThat(cloud, instanceOf(DockerCloud.class));

        final DockerCloud dockerCloud = (DockerCloud) cloud;
        final DockerSlaveTemplate template = dockerCloud.getTemplate("image");
        assertThat(template, notNullValue());

        final RetentionStrategy retentionStrategy = template.getRetentionStrategy();
        assertThat(retentionStrategy, instanceOf(DockerCloudRetentionStrategy.class));

        final DockerCloudRetentionStrategy strategy = (DockerCloudRetentionStrategy) retentionStrategy;
        assertThat(strategy.getIdleMinutes(), is(30));
    }

}
