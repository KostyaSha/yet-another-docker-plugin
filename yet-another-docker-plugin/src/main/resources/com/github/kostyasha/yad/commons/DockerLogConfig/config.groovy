package com.github.kostyasha.yad.commons.DockerLogConfig

import lib.FormTagLib

def f = namespace(FormTagLib);


f.entry(title: _("Logging Type"), field: "loggingType") {
    f.enum() {
        text(my.name())
    }
}

// No good UI for binding.
//
//f.entry(title: _("Logging Config"), field: "configStr") {
//    f.expandableTextbox()
//}
