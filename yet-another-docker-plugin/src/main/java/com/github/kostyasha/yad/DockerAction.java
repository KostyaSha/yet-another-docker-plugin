package com.github.kostyasha.yad;

import com.github.kostyasha.yad_docker_java.org.apache.commons.lang.builder.ToStringBuilder;
import hudson.model.InvisibleAction;

import static com.github.kostyasha.yad_docker_java.org.apache.commons.lang.builder.EqualsBuilder.reflectionEquals;
import static com.github.kostyasha.yad_docker_java.org.apache.commons.lang.builder.HashCodeBuilder.reflectionHashCode;
import static com.github.kostyasha.yad_docker_java.org.apache.commons.lang.builder.ToStringStyle.SHORT_PREFIX_STYLE;

/**
 * Storage attached to job. Atm invisible, something could be visualised later.
 *
 * @author Kanstantsin Shautsou
 */
public class DockerAction extends InvisibleAction {
    private String remoteFSMapping = null;

    public String getRemoteFSMapping() {
        return remoteFSMapping;
    }

    public void setRemoteFSMapping(String remoteFSMapping) {
        this.remoteFSMapping = remoteFSMapping;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, SHORT_PREFIX_STYLE);
    }

    @Override
    public boolean equals(Object o) {
        return reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return reflectionHashCode(this);
    }
}
