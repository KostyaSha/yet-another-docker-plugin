package com.github.kostyasha.yad.DockerConnector

import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials
import com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl

import jenkins.model.Jenkins

def l = namespace("/lib/layout")

l.ajax() {
    div(
        String.format("Docker server connection credentials. '%s' and '%s' automatically enables TLS connection.",
                Jenkins.getActiveInstance().getDescriptor(DockerServerCredentials).getDisplayName(),
                Jenkins.getActiveInstance().getDescriptor(CertificateCredentialsImpl.class).getDisplayName()
        )
    )
}
