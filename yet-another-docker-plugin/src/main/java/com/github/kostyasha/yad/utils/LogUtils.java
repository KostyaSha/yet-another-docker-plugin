package com.github.kostyasha.yad.utils;

import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.ResponseItem;
import hudson.model.TaskListener;

/**
 * @author Kanstantsin Shautsou
 */
public class LogUtils {
    private LogUtils() {
    }

    public static void printResponseItemToListener(TaskListener listener, ResponseItem item) {
        if (item != null && item.getStatus() != null) {
            if (item.isErrorIndicated()) {
                listener.error(item.getErrorDetail().toString());
            }

            final StringBuilder stringBuffer = new StringBuilder();

            if (item.getId() != null) {
                stringBuffer.append(item.getId()).append(": "); // Doesn't exist before "Digest"
            }

            stringBuffer.append(item.getStatus());

            if (item.getProgressDetail() != null) {
                stringBuffer.append(" ").append(item.getErrorDetail().toString());
            }

            listener.getLogger().println(stringBuffer.toString());
        }
    }
}
