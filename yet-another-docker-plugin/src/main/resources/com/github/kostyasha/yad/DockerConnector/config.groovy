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

f.entry(title: _("Host Credentials"), field: "credentialsId") {
    c.select()
}

f.entry(title: "Type", field: "connectorType") {
    f.enum() {
        text(my.name())
    }
}

f.entry(title: _("Connect timeout"), field: "connectTimeout", default: "0") {
    f.number()
}

f.validateButton(title: _("Test Connection"), progress: _("Testing..."),
        method: "testConnection",
        with: "serverUrl,credentialsId,version,connectorType"
)
