package com.github.kostyasha.yad.DockerSlave

import lib.FormTagLib

def f = namespace(FormTagLib);

f.entry(title: _("Container Id"), field: "containerId") {
    f.readOnlyTextbox()
}

f.entry(title: "Cloud Name", field: "cloudId") {
    f.readOnlyTextbox()
}
