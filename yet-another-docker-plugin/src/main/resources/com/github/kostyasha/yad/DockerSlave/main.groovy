package com.github.kostyasha.yad.DockerSlave

import lib.FormTagLib

def f = namespace(FormTagLib);

f.entry(title: _("Ctest"), field: "containerId") {
    f.readOnlyTextbox()
}