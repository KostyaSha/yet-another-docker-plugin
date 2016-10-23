package com.github.kostyasha.yad.DockerNodeProperty

import com.github.kostyasha.yad.DockerNodeProperty
import lib.FormTagLib

def f = namespace(FormTagLib);
def st = namespace("jelly:stapler")

if (instance == null) {
    instance = new DockerNodeProperty()
}

// intend somehow
f.block() {
    f.optionalBlock(
            title: _("Docker Container ID"),
            field: "containerIdCheck",
            inline: true
    ) {
        f.entry(title: "Variable name") {
            f.textbox(field: "containerId")
        }
    }

    f.optionalBlock(
            title: _("Docker Cloud ID"),
            field: "cloudIdCheck",
            inline: true
    ) {
        f.entry(title: "Variable name") {
            f.textbox(field: "cloudId")
        }
    }

    f.optionalBlock(
            title: _("Docker Host URL"),
            field: "dockerHostCheck",
            inline: true
    ) {
        f.entry(title: "Variable name") {
            f.textbox(field: "dockerHost")
        }
    }
}
