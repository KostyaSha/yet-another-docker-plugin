package com.github.kostyasha.yad.DockerConnector

import com.github.kostyasha.yad.DockerConnector
import lib.CredentialsTagLib
import lib.FormTagLib

def f = namespace(FormTagLib)
def c = namespace(CredentialsTagLib)

if (instance == null) {
    instance = new DockerConnector("http://localhost:2375")
}

f.entry(title: _("Docker URL"), field: "serverUrl") {
    f.textbox()
}

f.entry(title: _("Credentials"), field: "credentialsId") {
    c.select()
}

f.entry(title: _("Connection Timeout (seconds)"), field: "connectTimeout") {
    f.textbox(default: "0")
}

f.entry(title: _("Read Timeout (seconds)"), field: "readTimeout") {
    f.textbox(default: "0")
}

f.validateButton(title: _("Test Connection"), progress: _("Testing..."),
        method: "testConnection",
        with: "serverUrl,credentialsId,version,connectTimeout,readTimeout"
)
