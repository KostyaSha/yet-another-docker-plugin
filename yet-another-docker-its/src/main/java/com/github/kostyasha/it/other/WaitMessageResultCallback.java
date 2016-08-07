package com.github.kostyasha.it.other;

import com.github.kostyasha.yad.docker_java.com.github.dockerjava.api.model.Frame;
import com.github.kostyasha.yad.docker_java.com.github.dockerjava.core.command.LogContainerResultCallback;
import com.github.kostyasha.yad.docker_java.org.apache.commons.lang.StringUtils;

import static java.nio.charset.Charset.defaultCharset;

/**
 * Class that waits for certain message.
 *
 * @author Kanstantsin Shautsou
 */
public class WaitMessageResultCallback extends LogContainerResultCallback {
    private final String message;
    private boolean found = false;

    public WaitMessageResultCallback(String message) {
        this.message = message;
    }

    public boolean getFound() {
        return found;
    }

    @Override
    public void onNext(Frame frame) {
        super.onNext(frame);
        final String payloadMsg = new String(frame.getPayload(), defaultCharset()).trim();
        if (StringUtils.contains(payloadMsg, message)) {
            found = true;
            onComplete();
        }
    }
}
