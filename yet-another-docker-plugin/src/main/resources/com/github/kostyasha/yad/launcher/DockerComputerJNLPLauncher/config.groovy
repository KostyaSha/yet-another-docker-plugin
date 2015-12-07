package com.github.kostyasha.yad.launcher.DockerComputerJNLPLauncher

import com.github.kostyasha.yad.launcher.DockerComputerJNLPLauncher
import lib.FormTagLib

def f = namespace(FormTagLib);
def st = namespace("jelly:stapler")

if (instance == null) {
    instance = new DockerComputerJNLPLauncher()
}

f.entry(title: _("Linux user"), field: "user") {
    f.textbox()
}

f.entry(title: _("Launch timeout"), field: "launchTimeout") {
    f.number()
}

f.property(field: "jnlpLauncher")
