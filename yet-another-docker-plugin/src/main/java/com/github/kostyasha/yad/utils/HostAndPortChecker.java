package com.github.kostyasha.yad.utils;

import com.github.kostyasha.yad.docker_java.com.google.common.net.HostAndPort;
import com.trilead.ssh2.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import static com.github.kostyasha.yad.docker_java.com.google.common.base.Preconditions.checkState;
import static java.util.concurrent.TimeUnit.SECONDS;

public class HostAndPortChecker {
    private static final Logger LOG = LoggerFactory.getLogger(HostAndPortChecker.class);

    private final HostAndPort hostAndPort;

    private int retries = 10;
    private int sshTimeoutMillis = (int) SECONDS.toMillis(2);

    private HostAndPortChecker(HostAndPort hostAndPort) {
        this.hostAndPort = hostAndPort;
    }

    /**
     * @param hostAndPort hostname and port to connect to
     * @return util class to check connection
     */
    public static HostAndPortChecker create(HostAndPort hostAndPort) {
        return new HostAndPortChecker(hostAndPort);
    }

    public static HostAndPortChecker create(String host, int port) {
        return new HostAndPortChecker(HostAndPort.fromParts(host, port));
    }

    public HostAndPortChecker withRetries(int retries) {
        this.retries = retries;
        return this;
    }

    public HostAndPortChecker withSshTimeout(int time, TimeUnit units) {
        this.sshTimeoutMillis = (int) units.toMillis(time);
        return this;
    }

    /**
     * @return true if socket opened successfully, false otherwise
     */
    public boolean openSocket() {
        try (Socket ignored = new Socket(hostAndPort.getHostText(), hostAndPort.getPort())) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Use {@link #openSocket()} to check.
     * Retries while attempts reached with delay
     *
     * @return true if socket opened successfully, false otherwise
     */
    public boolean withEveryRetryWaitFor(int time, TimeUnit units) {
        for (int i = 1; i <= retries; i++) {
            if (openSocket()) {
                return true;
            }
            sleepFor(time, units);
        }
        return false;
    }

    /**
     * Connects to sshd on host:port
     * Retries while attempts reached with delay
     * First with tcp port wait, then with ssh connection wait
     *
     * @throws IOException if no retries left
     */
    public void bySshWithEveryRetryWaitFor(int time, TimeUnit units) throws IOException {
        checkState(withEveryRetryWaitFor(time, units), "Port %s is not opened to connect to", hostAndPort.getPort());

        for (int i = 1; i <= retries; i++) {
            Connection connection = new Connection(hostAndPort.getHostText(), hostAndPort.getPort());
            try {
                connection.connect(null, 0, sshTimeoutMillis, sshTimeoutMillis);
                LOG.info("SSH port is open on {}:{}", hostAndPort.getHostText(), hostAndPort.getPort());
                return;
            } catch (IOException e) {
                LOG.error("Failed to connect to {}:{} (try {}/{}) - {}",
                        hostAndPort.getHostText(), hostAndPort.getPort(), i, retries, e.getMessage());
                if (i == retries) {
                    throw e;
                }
            } finally {
                connection.close();
            }
            sleepFor(time, units);
        }
    }

    /**
     * Blocks current thread for {@code time} of {@code units}
     *
     * @param time  number of units
     * @param units to convert to millis
     */
    private static void sleepFor(int time, TimeUnit units) {
        try {
            LOG.trace("Sleeping for {} {}", time, units.toString());
            Thread.sleep(units.toMillis(time));
        } catch (InterruptedException e) {
            // no-op
        }
    }

}
