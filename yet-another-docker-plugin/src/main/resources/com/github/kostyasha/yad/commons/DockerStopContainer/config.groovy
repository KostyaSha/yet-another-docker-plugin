package com.github.kostyasha.yad.commons.DockerStopContainer

import lib.FormTagLib

def f = namespace(FormTagLib);

f.entry(title: _("Timeout"), field: "timeout") {
    f.number(default: "10")
}
