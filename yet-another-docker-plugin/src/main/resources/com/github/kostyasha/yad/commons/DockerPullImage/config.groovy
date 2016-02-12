package com.github.kostyasha.yad.commons.DockerPullImage

import lib.CredentialsTagLib
import lib.FormTagLib

def f = namespace(FormTagLib);
def c = namespace(CredentialsTagLib)

f.entry(title: _("Pull strategy"), field: "pullStrategy") {
    f.enum() {
        text(my.description)
    }
}

f.entry(title: _("Registry Credentials"), field: "credentialsId") {
    c.select()
}
