package com.github.kostyasha.yad.commons.cmds.DockerBuildImage

import com.github.kostyasha.yad.commons.cmds.DockerBuildImage
import lib.FormTagLib

def f = namespace(FormTagLib);

if (instance == null) {
    instance = new DockerBuildImage()
}

f.advanced(title: _("Build Image Settings"), align: "left") {
    f.entry(title: _("No Cache"), field: "noCache") {
        f.checkbox()
    }

    f.entry(title: _("Remove"), field: "remove") {
        f.checkbox()
    }

    f.entry(title: _("Quiet"), field: "quiet") {
        f.checkbox()
    }

    f.entry(title: _("Pull"), field: "pull") {
        f.checkbox()
    }

    f.entry(title: _("Tags"), field: "volumesFromString") {
        f.expandableTextbox()
    }

    f.entry(title: _("Environment"), field: "environmentString") {
        f.expandableTextbox()
    }

    f.entry(title: _("Port bindings"), field: "bindPorts") {
        f.textbox()
    }
}