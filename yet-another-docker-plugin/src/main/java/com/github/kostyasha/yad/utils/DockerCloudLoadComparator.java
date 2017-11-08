package com.github.kostyasha.yad.utils;

import com.github.kostyasha.yad.DockerCloud;
import java.io.Serializable;
import java.util.Comparator;

import static com.github.kostyasha.yad.utils.DockerFunctions.countCurrentDockerSlaves;

/**
 * Class used to sort a List of DockerCloud objects by load.  Least loaded is
 * considered earlier in the sort order.
 *
 * @author Sam Gleske (@samrocketman on GitHub)
 */
public class DockerCloudLoadComparator implements Comparator<DockerCloud>, Serializable {

    private static final long serialVersionUID = 42L;

    public static long getSerialVersionUID() {
        return serialVersionUID;
    }

    @Override
    public int compare(DockerCloud a1, DockerCloud a2) {
        return countCurrentDockerSlaves(a1) < countCurrentDockerSlaves(a2) ? -1 : 1;
    }
}
