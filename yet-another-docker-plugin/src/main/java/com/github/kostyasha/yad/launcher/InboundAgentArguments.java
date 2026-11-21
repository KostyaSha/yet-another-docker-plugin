package com.github.kostyasha.yad.launcher;

import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

final class InboundAgentArguments {
    private InboundAgentArguments() {}

    /**
     * Encode launch arguments so paths and node names survive both shell parsers.
     * An alternate URL connects directly because the JNLP descriptor advertises the global URL.
     */
    static String forLaunch(String alternateUrl, String rootUrl, String agentJarPath,
                            SlaveComputer computer, JNLPLauncher launcher) {
        boolean useAlternateUrl = StringUtils.isNotBlank(alternateUrl);
        if (!useAlternateUrl && agentJarPath.isEmpty()) {
            return "";
        }

        List<String> args = new ArrayList<>(List.of("-jar", agentJarPath.isEmpty() ? "slave.jar" : agentJarPath));
        if (useAlternateUrl) {
            args.addAll(List.of("-url", alternateUrl, "-name", computer.getName()));
            if (launcher.getTunnel() != null) {
                args.addAll(List.of("-tunnel", launcher.getTunnel()));
            }
            if (launcher.isWebSocket()) {
                args.add("-webSocket");
            }
            args.addAll(launcher.getWorkDirSettings().toCommandLineArgs(computer));
        } else {
            String jnlpUrl = StringUtils.removeEnd(rootUrl, "/") + "/"
                    + StringUtils.removeEnd(computer.getUrl(), "/") + "/slave-agent.jnlp";
            args.addAll(List.of("-jnlpUrl", jnlpUrl));
        }

        StringBuilder file = new StringBuilder();
        for (String arg : args) {
            file.append('"').append(arg.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r")).append("\"\n");
        }
        return Base64.getEncoder().encodeToString(file.toString().getBytes(StandardCharsets.UTF_8));
    }
}
