package com.github.kostyasha.yad.utils;

import com.github.kostyasha.yad.DockerCloud;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

/**
 * @author Sam Gleske (@samrocketman on GitHub)
 */
@RunWith(MockitoJUnitRunner.class)
public class DockerCloudLoadComparatorTest {

    @Mock
    private DockerCloud mockCloudNoLoad;
    @Mock
    private DockerCloud mockCloudLoad;


    @Test
    public void testDockerCloudLoadComparison() throws Exception {
        //cloud with no load
        when(mockCloudNoLoad.countCurrentDockerSlaves(null)).thenReturn(0);
        //cloud with load
        when(mockCloudLoad.countCurrentDockerSlaves(null)).thenReturn(1);

        List<DockerCloud> unsortedClouds = Arrays.asList(mockCloudLoad, mockCloudNoLoad);
        List<DockerCloud> sortedClouds = Arrays.asList(mockCloudNoLoad, mockCloudLoad);

        //test sorting clouds using the DockerCloudLoadComparator
        Collections.sort(unsortedClouds, new DockerCloudLoadComparator());

        //unsortedClouds should now be sorted
        assertThat(sortedClouds, is(unsortedClouds));
    }

    @Test
    public void testSerialVersionUID() {
        //simple test for something which is not used but required by findbugs
        assertThat(DockerCloudLoadComparator.getSerialVersionUID(), is(42L));
    }
}
