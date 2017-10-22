package com.github.kostyasha.yad.commons;

import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.command.ListImagesCmd;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.Image;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.github.kostyasha.yad.commons.DockerImagePullStrategy.PULL_ALWAYS;
import static com.github.kostyasha.yad.commons.DockerImagePullStrategy.PULL_LATEST;
import static com.github.kostyasha.yad.commons.DockerImagePullStrategy.PULL_NEVER;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Slawomir Jaranowski
 */
@RunWith(Parameterized.class)
public class DockerPullImageTest {
    private final List<Image> imageList;
    private final String imageName;
    private final DockerImagePullStrategy pullStrategy;
    private final boolean shouldPull;

    @Parameterized.Parameters(name = "existing image: ''{0}'', image to pull: ''{1}'', strategy: ''{2}''")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"docker.io/kostyasha/yet-another-docker-plugin:wget-master",
                        "kostyasha/yet-another-docker-plugin:wget-master", PULL_LATEST, false},
                {"kostyasha/yet-another-docker-plugin:wget-master",
                        "kostyasha/yet-another-docker-plugin:wget-master", PULL_LATEST, false},
                {"", "repo/name", PULL_LATEST, true},
                {"repo/name:latest", "repo/name", PULL_LATEST, true},
                {"", "repo/name:latest", PULL_LATEST, true},
                {"repo/name:latest", "repo/name:latest", PULL_LATEST, true},
                {"", "repo/name:1.0", PULL_LATEST, true},
                {"repo/name:1.0", "repo/name:1.0", PULL_LATEST, false},

                {"", "repo/name", PULL_ALWAYS, true},
                {"repo/name:latest", "repo/name", PULL_ALWAYS, true},
                {"", "repo/name:latest", PULL_ALWAYS, true},
                {"repo/name:latest", "repo/name:latest", PULL_ALWAYS, true},
                {"", "repo/name:1.0", PULL_ALWAYS, true},
                {"repo/name:1.0", "repo/name:1.0", PULL_ALWAYS, true},


                {"", "repo/name", PULL_NEVER, false},
                {"repo/name:latest", "repo/name", PULL_NEVER, false},
                {"", "repo/name:latest", PULL_NEVER, false},
                {"repo/name:latest", "repo/name:latest", PULL_NEVER, false},
                {"", "repo/name:1.0", PULL_NEVER, false},
                {"repo/name:1.0", "repo/name:1.0", PULL_NEVER, false},

        });
    }

    @Test
    public void shouldPullImageTest() {
        DockerPullImage dockerPullImage = new DockerPullImage();
        dockerPullImage.setPullStrategy(pullStrategy);
        DockerClient client = mockDockerClient();
        assertEquals(shouldPull, dockerPullImage.shouldPullImage(client, imageName));
    }

    public DockerPullImageTest(String existedImage, String imageName, DockerImagePullStrategy pullStrategy,
                               boolean shouldPull) {
        imageList = Collections.singletonList(mockImage(existedImage));
        this.imageName = imageName;
        this.pullStrategy = pullStrategy;
        this.shouldPull = shouldPull;
    }

    private DockerClient mockDockerClient() {

        ListImagesCmd listImagesCmd = mock(ListImagesCmd.class);

        when(listImagesCmd.exec()).thenReturn(imageList);

        DockerClient dockerClient = mock(DockerClient.class);
        when(dockerClient.listImagesCmd()).thenReturn(listImagesCmd);

        return dockerClient;
    }

    private Image mockImage(String repoTag) {
        Image img = mock(Image.class);
        when(img.getRepoTags()).thenReturn(new String[]{repoTag});
        return img;
    }
}
