package com.github.kostyasha.yad.DockerSlave

import lib.FormTagLib
import lib.YadTagLib

def f = namespace(FormTagLib);
def yad = namespace(YadTagLib)


f.entry(title: _("Ctest"), field: "containerId") {
    f.readOnlyTextbox()
}