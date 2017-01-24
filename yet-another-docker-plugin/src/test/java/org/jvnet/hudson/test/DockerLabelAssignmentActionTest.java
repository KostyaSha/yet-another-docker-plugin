package org.jvnet.hudson.test;

import com.github.kostyasha.yad.DockerConnector;
import com.github.kostyasha.yad.DockerSlaveTemplate;
import com.github.kostyasha.yad.action.DockerLabelAssignmentAction;
import hudson.model.FreeStyleProject;
import hudson.model.queue.QueueTaskFuture;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebXmlConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;

import javax.servlet.ServletContext;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.github.kostyasha.yad.other.ConnectorType.JERSEY;
import static com.github.kostyasha.yad.other.ConnectorType.NETTY;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerLabelAssignmentActionTest {
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(DockerLabelAssignmentActionTest.class);

    private static final String ADDRESS = "192.168.1.3";

    @Rule
    public JenkinsRule jRule = new JenkinsRule() {
        @Override
        public URL getURL() throws IOException {
            return new URL("http://" + ADDRESS + ":" + localPort + contextPath + "/");
        }

        @Override
        protected ServletContext createWebServer() throws Exception {
            server = new Server(new ThreadPoolImpl(new ThreadPoolExecutor(10, 10, 10L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r);
                    t.setName("Jetty Thread Pool");
                    return t;
                }
            })));

            WebAppContext context = new WebAppContext(WarExploder.getExplodedDir().getPath(), contextPath);
            context.setClassLoader(getClass().getClassLoader());
            context.setConfigurations(new Configuration[]{new WebXmlConfiguration()});
            context.addBean(new NoListenerConfiguration(context));
            server.setHandler(context);
            context.setMimeTypes(MIME_TYPES);
            context.getSecurityHandler().setLoginService(configureUserRealm());
            context.setResourceBase(WarExploder.getExplodedDir().getPath());

            ServerConnector connector = new ServerConnector(server);
            HttpConfiguration config = connector.getConnectionFactory(HttpConnectionFactory.class).getHttpConfiguration();
            // use a bigger buffer as Stapler traces can get pretty large on deeply nested URL
            config.setRequestHeaderSize(12 * 1024);
            connector.setHost(ADDRESS);
            if (System.getProperty("port") != null)
                connector.setPort(Integer.parseInt(System.getProperty("port")));

            server.addConnector(connector);
            server.start();

            localPort = connector.getLocalPort();
            LOGGER.info("Running on {}", getURL());

            return context.getServletContext();
        }
    };

    @Test
    public void actionTask() throws Exception {

        final FreeStyleProject project = jRule.createProject(FreeStyleProject.class, "test");

        final DockerSlaveTemplate slaveTemplate = new DockerSlaveTemplate();

        final DockerLabelAssignmentAction assignmentAction = new DockerLabelAssignmentAction("docker-slave");
        final DockerConnector dockerConnector = new DockerConnector("tcp://192.168.1.3:2376/");
        dockerConnector.setConnectorType(JERSEY);

        assignmentAction.setConnector(dockerConnector);

        final QueueTaskFuture<?> build2 = project.scheduleBuild2(0, assignmentAction);

        jRule.pause();
        jRule.waitUntilNoActivity();
    }
}