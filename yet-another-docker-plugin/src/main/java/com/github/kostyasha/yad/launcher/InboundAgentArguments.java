package com.github.kostyasha.yad.launcher;

import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

final class InboundAgentArguments {
    private InboundAgentArguments() {}

    /**
     * An explicit URL must be passed directly to Remoting. The JNLP descriptor advertises the
     * controller's global URL. Encode a Java launcher argument file so paths and node names
     * survive both shell parsers without interpolation. The secret is supplied separately.
     */
    static String forAlternateUrl(String alternateUrl, SlaveComputer computer, JNLPLauncher launcher) {
        if (alternateUrl == null || alternateUrl.isBlank()) {
            return "";
        }
        List<String> args = new ArrayList<>(List.of("-jar", "slave.jar", "-url", alternateUrl,
                "-name", computer.getName()));
        if (launcher.getTunnel() != null) {
            args.add("-tunnel");
            args.add(launcher.getTunnel());
        }
        if (launcher.isWebSocket()) {
            args.add("-webSocket");
        }
        args.addAll(launcher.getWorkDirSettings().toCommandLineArgs(computer));

        StringBuilder file = new StringBuilder();
        for (String arg : args) {
            file.append('"').append(arg.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r")).append("\"\n");
        }
        return Base64.getEncoder().encodeToString(file.toString().getBytes(StandardCharsets.UTF_8));
    }
}
