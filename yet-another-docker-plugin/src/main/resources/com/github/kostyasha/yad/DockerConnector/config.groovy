package com.github.kostyasha.yad.DockerConnector

import com.github.kostyasha.yad.DockerConnector
import lib.CredentialsTagLib
import lib.FormTagLib

def f = namespace(FormTagLib)
def c = namespace(CredentialsTagLib)

if (instance == null) {
    instance = new DockerConnector("tcp://localhost:2375")
}

f.entry(title: _("Docker URL"), field: "serverUrl") {
    f.textbox()
}

f.entry(title: _("Docker API version"), field: "apiVersion") {
    f.textbox()
}

f.entry(title: _("TLS verify"), field: "tlsVerify") {
    f.checkbox()
}

f.entry(title: _("Host Credentials"), field: "credentialsId") {
    c.select()
}

f.validateButton(title: _("Test Connection"), progress: _("Testing..."),
        method: "testConnection",
        with: "serverUrl,credentialsId,version,connectTimeout,readTimeout"
)
