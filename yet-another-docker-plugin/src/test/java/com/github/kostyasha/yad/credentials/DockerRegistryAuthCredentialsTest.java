package com.github.kostyasha.yad.credentials;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.security.ACL;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlSelect;
import org.htmlunit.util.NameValuePair;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DockerRegistryAuthCredentialsTest {
    @Rule public JenkinsRule j = new JenkinsRule();

    @Test
    public void createsControllerOnlyRegistryCredential() throws Exception {
        try (JenkinsRule.WebClient client = j.createWebClient()) {
            client.getOptions().setJavaScriptEnabled(false);
            var page = client.goTo("credentials/store/system/domain/_/dialog2?kind="
                    + DockerRegistryAuthCredentials.class.getName());
            var form = page.getFormByName("newCredentials");
            HtmlSelect scope = form.getSelectByName("_.scope");
            URI dialog = page.getUrl().toURI();
            WebRequest options = new WebRequest(dialog.resolve(scope.getAttribute("fillUrl")).toURL(), HttpMethod.POST);
            assertTrue(client.loadWebResponse(client.addCrumb(options)).getContentAsString().contains("\"SYSTEM\""));

            form.getInputByName("_.id");
            form.getInputByName("_.email");
            form.getInputByName("_.username");
            form.getInputByName("_.password");
            JSONObject submission = JSONObject.fromObject(Map.of("credentials", Map.of(
                    "stapler-class", DockerRegistryAuthCredentials.class.getName(),
                    "scope", "SYSTEM", "id", "test-registry", "email", "registry@example.test",
                    "username", "registry-user", "password", "registry-password")));
            WebRequest save = new WebRequest(dialog.resolve(form.getActionAttribute()).toURL(), HttpMethod.POST);
            save.setRequestParameters(List.of(new NameValuePair("json", submission.toString())));
            client.getPage(client.addCrumb(save));
        }

        DockerRegistryAuthCredentials credential = (DockerRegistryAuthCredentials)
                SystemCredentialsProvider.getInstance().getCredentials().get(0);
        assertEquals(CredentialsScope.SYSTEM, credential.getScope());
        assertEquals("test-registry", credential.getId());
        assertEquals("registry@example.test", credential.getEmail());
        assertEquals("registry-user", credential.getUsername());
        assertEquals("registry-password", credential.getPassword().getPlainText());
        assertTrue(CredentialsProvider.lookupCredentialsInItem(
                DockerRegistryAuthCredentials.class, j.createFreeStyleProject(), ACL.SYSTEM2).isEmpty());
    }
}
