package com.github.kostyasha.yad.connector.DockerJavaConnector

import lib.CredentialsTagLib
import lib.FormTagLib

def f = namespace(FormTagLib)
def c = namespace(CredentialsTagLib)


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

f.entry(title: "Type", field: "connectorType") {
    f.enum() {
        text(my.name())
    }
}

f.validateButton(title: _("Test Connection"), progress: _("Testing..."),
        method: "testConnection",
        with: "serverUrl,credentialsId,version,connectTimeout,readTimeout"
)
