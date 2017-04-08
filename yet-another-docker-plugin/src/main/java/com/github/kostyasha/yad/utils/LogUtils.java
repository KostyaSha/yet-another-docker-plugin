package com.github.kostyasha.yad.utils;

import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.ResponseItem;
import hudson.model.TaskListener;

import static java.util.Objects.nonNull;

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
                Long current = item.getProgressDetail().getCurrent();
                Long start = item.getProgressDetail().getStart();
                Long total = item.getProgressDetail().getTotal();
                if (nonNull(current)) {
                    stringBuffer.append(" current=").append(current);
                }
                if (nonNull(start)) {
                    stringBuffer.append(",start=").append(start);
                }
                if (nonNull(total)) {
                    stringBuffer.append(",total=").append(total);
                }
            }

            listener.getLogger().println(stringBuffer.toString());
        }
    }
}
