package com.github.kostyasha.yad.commons.DockerRemoveContainer

import lib.FormTagLib

def f = namespace(FormTagLib);

f.entry(title: _("Remove volumes"), field: "removeVolumes") {
    f.checkbox()
}

f.entry(title: _("Force"), field: "force") {
    f.checkbox()
}
