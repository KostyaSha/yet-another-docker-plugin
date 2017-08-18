package com.github.kostyasha.yad.commons.DockerRegistryCredential

import lib.CredentialsTagLib
import lib.FormTagLib

def f = namespace(FormTagLib)
def c = namespace(CredentialsTagLib)

f.entry(title: "Registry host", field: "registryAddr") {
    f.textbox()
}

f.entry(title: _("Registry Credentials"), field: "credentialsId") {
    c.select()
}

