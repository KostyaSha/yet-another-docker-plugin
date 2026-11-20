package com.github.kostyasha.yad;

import com.github.kostyasha.yad.connector.CredentialsYADockerConnector;
import com.github.kostyasha.yad.other.ConnectorType;
import hudson.util.XStream2;
import org.junit.Test;

import static com.github.kostyasha.yad.other.ConnectorType.HTTPCLIENT5;
import static com.github.kostyasha.yad.other.ConnectorType.NETTY;
import static org.junit.Assert.assertEquals;

public class ConnectorTransportTest {
    private final XStream2 xml = new XStream2();

    @Test
    public void newConnectorsUseHttpClient5() {
        assertEquals(HTTPCLIENT5, new DockerConnector("unix:///var/run/docker.sock").getConnectorType());
        assertEquals(HTTPCLIENT5, new CredentialsYADockerConnector().getConnectorType());
    }

    @Test
    public void oldConfigurationsWithoutTransportUseNetty() {
        assertSavedTransport("", NETTY);
    }

    @Test
    public void savedTransportIsPreserved() {
        for (ConnectorType type : ConnectorType.values()) {
            assertSavedTransport("<connectorType>" + type.name() + "</connectorType>", type);
        }
    }

    private void assertSavedTransport(String fields, ConnectorType expected) {
        assertEquals(expected, load(DockerConnector.class, fields).getConnectorType());
        assertEquals(expected, load(CredentialsYADockerConnector.class, fields).getConnectorType());
    }

    private <T> T load(Class<T> type, String fields) {
        String tag = type.getName();
        return type.cast(xml.fromXML("<" + tag + ">" + fields + "</" + tag + ">"));
    }
}
