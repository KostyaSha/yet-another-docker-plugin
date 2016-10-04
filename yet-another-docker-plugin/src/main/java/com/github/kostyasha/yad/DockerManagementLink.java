package com.github.kostyasha.yad;

import com.cloudbees.plugins.credentials.GlobalCredentialsConfiguration;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.model.Link;
import com.google.common.base.Predicate;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.BulkChange;
import hudson.Extension;
import hudson.Functions;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.ManagementLink;
import hudson.util.FormApply;
import hudson.util.FormValidation;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.github.kostyasha.yad.utils.BindUtils.joinToStr;
import static com.github.kostyasha.yad.utils.BindUtils.splitAndFilterEmpty;
import static java.util.Objects.isNull;

/**
 * @author Kanstantsin Shautsou
 */
@Extension
public class DockerManagementLink extends ManagementLink implements Describable<DockerManagementLink> {

    public static final Predicate<GlobalConfigurationCategory> FILTER = input -> input instanceof GlobalCredentialsConfiguration.Category;

    @Override
    public String getIconFileName() {
        return "clipboard.png";
    }

    @Override
    public String getDisplayName() {
        return "display name";
    }

    @Override
    public String getUrlName() {
        return "yad";
    }

    @CheckForNull
    private List<String> links = Collections.singletonList("sdfsdfsdf");

    public DockerManagementLink() {
    }

    // links
    @Nonnull
    public List<String> getLinks() {
        return isNull(links) ? Collections.EMPTY_LIST : links;
    }

    public void setLinks(List<String> links) {
        this.links = links;
    }

    public String getLinksString() {
        return joinToStr(getLinks());
    }

    @DataBoundSetter
    public void setLinksString(String devicesString) {
        setLinks(splitAndFilterEmpty(devicesString));
    }


    @RequirePOST
    @NonNull
    @Restricted(NoExternalUse.class)
    @SuppressWarnings("unused") // stapler web method binding
    public synchronized HttpResponse doConfigure(@NonNull StaplerRequest req) throws IOException, ServletException,
            Descriptor.FormException {
        Jenkins jenkins = Jenkins.getActiveInstance();
        jenkins.checkPermission(Jenkins.ADMINISTER);
        // logically this change starts from Jenkins
        BulkChange bc = new BulkChange(jenkins);
        try {
            boolean result = configure(req, req.getSubmittedForm());
//            LOGGER.log(Level.FINE, "credentials configuration saved: " + result);
            jenkins.save();
            return FormApply
                    .success(result ? req.getContextPath() + "/manage" : req.getContextPath() + "/" + getUrlName());
        } finally {
            bc.commit();
        }
    }

    /**
     * Performs the configuration.
     *
     * @param req  the request.
     * @param json the JSON object.
     * @return {@code false} to keep the client in the same config page.
     * @throws Descriptor.FormException if something goes wrong.
     */
    private boolean configure(StaplerRequest req, JSONObject json) throws Descriptor.FormException {
        // for compatibility reasons, the actual value is stored in Jenkins
        Jenkins j = Jenkins.getActiveInstance();
        j.checkPermission(Jenkins.ADMINISTER);

        // persist all the provider configs
        boolean result = true;
        for (Descriptor<?> d : Functions.getSortedDescriptorsForGlobalConfig(FILTER)) {
            result &= configureDescriptor(req, json, d);
        }

        return result;
    }

    /**
     * Performs the configuration of a specific {@link Descriptor}.
     *
     * @param req  the request.
     * @param json the JSON object.
     * @param d    the {@link Descriptor}.
     * @return {@code false} to keep the client in the same config page.
     * @throws Descriptor.FormException if something goes wrong.
     */
    private boolean configureDescriptor(StaplerRequest req, JSONObject json, Descriptor<?> d) throws
            Descriptor.FormException {
        // collapse the structure to remain backward compatible with the JSON structure before 1.
        String name = d.getJsonSafeClassName();
        JSONObject js = json.has(name) ? json.getJSONObject(name) : new JSONObject();
        // if it doesn't have the property, the method returns invalid null object.
        json.putAll(js);
        return d.configure(req, js);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Descriptor<DockerManagementLink> getDescriptor() {
        return Jenkins.getActiveInstance().getDescriptorOrDie(getClass());
    }

    /**
     * Our {@link Descriptor}.
     */
    @Extension
    public static final class DescriptorImpl extends Descriptor<DockerManagementLink> {
        @Override
        public String getDisplayName() {
            return "display bane";
        }

        public FormValidation doCheckLinksString(@QueryParameter String linksString) {
            final List<String> links = splitAndFilterEmpty(linksString);
            for (String linkString : links) {
                try {
                    Link.parse(linkString);
                } catch (Exception ex) {
                    return FormValidation.error("Bad link configuration", ex);
                }
            }

            return FormValidation.ok();
        }
    }
}
