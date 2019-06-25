package com.github.kostyasha.yad.utils;

import com.github.kostyasha.yad.DockerSlaveSingle;
import jenkins.model.Jenkins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class DockerUtils {
    private static final Logger LOG = LoggerFactory.getLogger(DockerUtils.class);

    private DockerUtils() {
    }

    public static void jenkinsAddNodeWithRetry(DockerSlaveSingle slave) throws IOException,
            InterruptedException {
        IOException lastException = null;
        int retries = 6;

        while (retries >= 0) {
            try {
                Jenkins.getInstance().addNode(slave);
                return;
            } catch (IOException ex) {
                lastException = ex;
                if (retries <= 0) {
                    throw ex;
                }
                LOG.trace("Failed to add DockerSlaveSingle node to Jenkins, retrying...", ex);
                Thread.sleep(1000);
            } finally {
                retries--;
            }
        }

        throw lastException;
    }


}
