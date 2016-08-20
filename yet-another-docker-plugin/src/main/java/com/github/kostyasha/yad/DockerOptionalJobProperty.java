package com.github.kostyasha.yad;

import hudson.Extension;
import hudson.model.Job;
import jenkins.model.OptionalJobProperty;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.CheckForNull;

import static com.github.kostyasha.yad.docker_java.org.apache.commons.lang.BooleanUtils.isTrue;
import static java.util.Objects.isNull;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerOptionalJobProperty extends OptionalJobProperty<Job<?, ?>> {

    private Boolean injectVars = true;

    @DataBoundConstructor
    public DockerOptionalJobProperty() {
    }

    @CheckForNull
    public Boolean getInjectVars() {
        return injectVars;
    }

    public boolean isInjectVars() {
        return isTrue(injectVars);
    }

    public void setInjectVars(Boolean injectVars) {
        this.injectVars = injectVars;
    }

    public Object readResolve() {
        if (isNull(injectVars)) {
            // default behaviour
            injectVars = true;
        }
        return this;
    }

    @Extension
    public static final class DescriptorImpl extends OptionalJobPropertyDescriptor {
        @Override
        public String getDisplayName() {
            return "Yet Another Docker";
        }
    }
}
