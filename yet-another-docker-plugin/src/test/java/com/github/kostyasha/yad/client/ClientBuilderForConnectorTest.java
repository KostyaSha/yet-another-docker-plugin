package com.github.kostyasha.yad.client;

import com.github.kostyasha.yad.other.ConnectorType;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.api.DockerClient;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.core.DockerClientImpl;
import com.github.kostyasha.yad_docker_java.com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ClientBuilderForConnectorTest {
    @Test
    public void httpClient5RequestsDockerVersion() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1.44/version", exchange -> {
            byte[] body = "{\"ApiVersion\":\"1.44\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var response = exchange.getResponseBody()) {
                response.write(body);
            }
        });
        server.start();
        try (DockerClient client = ClientBuilderForConnector.newClientBuilderForConnector()
                .withConnectorType(ConnectorType.HTTPCLIENT5)
                .withConnectTimeout(3000)
                .withReadTimeout(5000)
                .forServer("tcp://127.0.0.1:" + server.getAddress().getPort(), "1.44")
                .build()) {
            assertTrue(((DockerClientImpl) client).getHttpClient() instanceof ApacheDockerHttpClient);
            assertEquals("1.44", client.versionCmd().exec().getApiVersion());
        } finally {
            server.stop(0);
        }
    }
}
