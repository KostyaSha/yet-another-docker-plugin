package com.github.kostyasha.yad.DockerOptionalJobProperty

import lib.FormTagLib

def f = namespace(FormTagLib);

f.entry(title: _("Inject cloud specific variables"),
        field: "injectVars") {
    f.checkbox()
}
