package com.github.kostyasha.yad;

import com.github.kostyasha.yad.strategy.DockerCloudRetentionStrategy;
import com.github.kostyasha.yad.strategy.DockerOnceRetentionStrategy;
import hudson.model.Descriptor;
import hudson.slaves.RetentionStrategy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static com.github.kostyasha.yad.DockerProvisioningStrategy.notAllowedStrategy;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

/**
 * @author Kanstantsin Shautsou
 */
@RunWith(MockitoJUnitRunner.class)
public class DockerProvisioningStrategyTest {

    @Mock
    private DockerSlaveTemplate mockTemplate;

    @Mock
    private DockerOnceRetentionStrategy mockDockerOnce;

    @Test
    public void nullTemplate() {
        assertThat(notAllowedStrategy(null), is(true));
    }

    @Test
    public void nullRetention() throws Descriptor.FormException {
        final DockerSlaveTemplate template = new DockerSlaveTemplate("id");
        template.setRetentionStrategy(null);

        assertThat(notAllowedStrategy(template), is(true));
    }

    @Test
    public void dockerOnceRetention1() throws Descriptor.FormException {
        final DockerSlaveTemplate template = new DockerSlaveTemplate("id");
        template.setRetentionStrategy(new DockerOnceRetentionStrategy(3));

        assertThat(notAllowedStrategy(template), is(false));
    }

    @Test
    public void dockerOnceRetention2() {
        when(mockTemplate.getRetentionStrategy()).thenReturn(mockDockerOnce);
        when(mockTemplate.getNumExecutors()).thenReturn(2);

        assertThat(notAllowedStrategy(mockTemplate), is(true));
    }


    @Test
    public void demandRetention() throws Descriptor.FormException {
        final DockerSlaveTemplate template = new DockerSlaveTemplate("id");
        template.setRetentionStrategy(new RetentionStrategy.Demand(2L, 4L));

        assertThat(notAllowedStrategy(template), is(false));
    }

    @Test
    public void otherRetentions() throws Descriptor.FormException {
        final DockerSlaveTemplate template = new DockerSlaveTemplate("id");
        template.setRetentionStrategy(new DockerCloudRetentionStrategy(3));

        assertThat(notAllowedStrategy(template), is(true));
    }
}
