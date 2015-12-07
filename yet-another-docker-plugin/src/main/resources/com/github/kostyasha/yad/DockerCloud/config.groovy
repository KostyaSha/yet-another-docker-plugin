package com.github.kostyasha.yad.DockerCloud

import lib.FormTagLib

def f = namespace(FormTagLib)

f.entry(title: _("Cloud Name"), field: "name") {
    f.textbox()
}

f.block(title: "Connector") {
    f.property(field: "connector")
}

f.entry(title: _("Max Containers"), field: "containerCap") {
    f.number(default: 50)
}

f.entry(title: _("Images"), description: _("List of Images to be launched as slaves")) {
    f.repeatableHeteroProperty(
            field: "templates",
            hasHeader: true,
            addCaption: _("Add Docker Template"),
            deleteCaption: _("Delete Docker Template")
    )
}
