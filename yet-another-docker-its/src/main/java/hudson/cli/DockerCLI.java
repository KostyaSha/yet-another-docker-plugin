/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.cli;

import hudson.cli.client.Messages;
import hudson.remoting.Channel;
import hudson.remoting.ChannelBuilder;
import hudson.remoting.RemoteInputStream;
import hudson.remoting.RemoteOutputStream;
import hudson.remoting.SocketChannelStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static hudson.remoting.RemoteInputStream.Flag.NOT_GREEDY;
import static java.nio.charset.Charset.defaultCharset;

/**
 * !!Copy-paste from {@link CLI} !!!
 * https://github.com/jenkinsci/jenkins/pull/1961
 *
 * @author Kanstantsin Shautsou
 */
public class DockerCLI {
    private static final Logger LOG = LoggerFactory.getLogger(CLI.class);

    public final ExecutorService pool;
    private final Channel channel;
    public final CliEntryPoint entryPoint;
    private final boolean ownsPool;
    private final List<Closeable> closables = new ArrayList<>(); // stuff to close in the close method
    private final int exposedPort;
    public final URL jenkins;


    public DockerCLI(CLIConnectionFactory factory, int exposedPort) throws IOException, InterruptedException {
        LOG.debug("Initializing jenkins CLI");
        jenkins = factory.jenkins;
        this.exposedPort = exposedPort;

        String url = jenkins.toExternalForm();
        if (!url.endsWith("/")) url += '/';

        LOG.trace("Jenkins {}, URL {}", jenkins, url);

        ownsPool = true;
        pool = Executors.newCachedThreadPool();

        this.channel = connectViaCliPort(jenkins, getCliTcpPort(url));

        // execute the command
        entryPoint = (CliEntryPoint) channel.waitForRemoteProperty(CliEntryPoint.class.getName());

        if (entryPoint.protocolVersion() != CliEntryPoint.VERSION) {
            throw new IOException(Messages.CLI_VersionMismatch());
        }
    }

    private Channel connectViaCliPort(URL jenkins, CliPort cliPort) throws IOException {
        LOG.debug("Trying to connect directly via TCP/IP to {}", cliPort.endpoint);
        final Socket s = new Socket();
        // this prevents a connection from silently terminated by the router in between or the other peer
        // and that goes without unnoticed. However, the time out is often very long (for example 2 hours
        // by default in Linux) that this alone is enough to prevent that.
        s.setKeepAlive(true);
        // we take care of buffering on our own
        s.setTcpNoDelay(true);

        s.connect(cliPort.endpoint, 3000);
        OutputStream out = SocketChannelStream.out(s);

        closables.add(s::close);

        Connection c = new Connection(SocketChannelStream.in(s), out);

        DataInputStream dis = new DataInputStream(s.getInputStream());
        DataOutputStream dos = new DataOutputStream(s.getOutputStream());
        dos.writeUTF("Protocol:CLI2-connect");
        String greeting = dis.readUTF();
        if (!greeting.equals("Welcome")) {
            throw new IOException("Handshaking failed: " + greeting);
        }

        try {
            byte[] secret = c.diffieHellman(false).generateSecret();
            SecretKey sessionKey = new SecretKeySpec(Connection.fold(secret, 128 / 8), "AES");
            c = c.encryptConnection(sessionKey, "AES/CFB8/NoPadding");

            // validate the instance identity, so that we can be sure that we are talking to the same server
            // and there's no one in the middle.
            byte[] signature = c.readByteArray();

            if (cliPort.identity != null) {
                Signature verifier = Signature.getInstance("SHA1withRSA");
                verifier.initVerify(cliPort.getIdentity());
                verifier.update(secret);
                if (!verifier.verify(signature))
                    throw new IOException("Server identity signature validation failed.");
            }
        } catch (GeneralSecurityException e) {
            throw (IOException) new IOException("Failed to negotiate transport security").initCause(e);
        }

        final Channel channel = new ChannelBuilder("CLI connection to " + jenkins, pool)
                .withMode(Channel.Mode.BINARY)
                .withBaseLoader(null)
                .withArbitraryCallableAllowed(true)
                .withRemoteClassLoadingAllowed(true)
                .build(new BufferedInputStream(c.in), new BufferedOutputStream(c.out));

        LOG.trace("Returning channel: {}.", channel);

        return channel;

//        return new Channel(
//                "CLI connection to " + jenkins,  // name
//                pool, //exec
//                Channel.Mode.BINARY,
//                new BufferedInputStream(c.in),
//                new BufferedOutputStream(c.out),
//                null,
//                false,
//                null
//        );
    }

    /**
     * If the server advertises CLI endpoint, returns its location.
     */
    protected CliPort getCliTcpPort(String jenkinsAddr) throws IOException {
        URL jenkinsUrl = new URL(jenkinsAddr);
        if (jenkinsUrl.getHost() == null || jenkinsUrl.getHost().length() == 0) {
            throw new IOException("Invalid URL: " + jenkinsAddr);
        }
        URLConnection head = jenkinsUrl.openConnection();
        try {
            head.connect();
        } catch (IOException e) {
            throw (IOException) new IOException("Failed to connect to " + jenkinsAddr).initCause(e);
        }

        String h = head.getHeaderField("X-Jenkins-CLI-Host");
        if (h == null) h = head.getURL().getHost();

        String identity = head.getHeaderField("X-Instance-Identity");

        flushURLConnection(head);

        return new CliPort(new InetSocketAddress(h, exposedPort), identity, 2);
    }

    /**
     * Flush the supplied {@link URLConnection} input and close the
     * connection nicely.
     *
     * @param conn the connection to flush/close
     */
    private void flushURLConnection(URLConnection conn) {
        byte[] buf = new byte[1024];
        try {
            InputStream is = conn.getInputStream();
            while (is.read(buf) >= 0) {
                // Ignore
            }
            is.close();
        } catch (IOException e) {
            try {
                InputStream es = ((HttpURLConnection) conn).getErrorStream();
                if (es != null) {
                    while (es.read(buf) >= 0) {
                        // Ignore
                    }
                    es.close();
                }
            } catch (IOException ex) {
                // Ignore
            }
        }
    }

    /**
     * Shuts down the channel and closes the underlying connection.
     */
    public void close() throws IOException, InterruptedException {
        channel.close();
        channel.join();
        if (ownsPool)
            pool.shutdown();
        for (Closeable c : closables)
            c.close();
    }

    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr) {
        return entryPoint.main(args, Locale.getDefault(),
                new RemoteInputStream(stdin, NOT_GREEDY),
                new RemoteOutputStream(stdout),
                new RemoteOutputStream(stderr));
    }

    public Channel getChannel() {
        return channel;
    }

    /**
     * Attempts to lift the security restriction on the underlying channel.
     * This requires the administer privilege on the server.
     *
     * @throws SecurityException If we fail to upgrade the connection.
     */
    public void upgrade() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (execute(Arrays.asList("groovy", "="),
                new ByteArrayInputStream(
                        "hudson.remoting.Channel.current().setRestricted(false)"
                                .getBytes(defaultCharset())),
                out, out) != 0) {
            throw new SecurityException(out.toString()); // failed to upgrade
        }
    }
}
