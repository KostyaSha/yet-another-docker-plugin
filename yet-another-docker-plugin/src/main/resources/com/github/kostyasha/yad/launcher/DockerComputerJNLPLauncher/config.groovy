package com.github.kostyasha.yad.launcher.DockerComputerJNLPLauncher

import com.github.kostyasha.yad.launcher.DockerComputerJNLPLauncher
import lib.FormTagLib

import static com.github.kostyasha.yad.launcher.DockerComputerJNLPLauncher.DEFAULT_TIMEOUT
import static com.github.kostyasha.yad.launcher.DockerComputerJNLPLauncher.DEFAULT_USER

def f = namespace(FormTagLib);
def st = namespace("jelly:stapler")

if (instance == null) {
    instance = new DockerComputerJNLPLauncher()
}

f.entry(title: _("Linux user"), field: "user", default: DEFAULT_USER) {
    f.textbox()
}

f.entry(title: _("Launch timeout"), field: "launchTimeout", default: DEFAULT_TIMEOUT) {
    f.number()
}

f.entry(title: _("Slave JNLP Options"), field: "jnlpOpts") {
    f.textbox()
}

f.entry(title: _("Slave JVM Options"), field: "jvmOpts") {
    f.textbox()
}

f.entry(title: _("Slave to connect to specific jenkins URL"), field: "jenkinsUrl") {
    f.textbox()
}

f.entry(title: _("Ignore certificate check"), field: "noCertificateCheck", default: false) {
    f.checkbox()
}

// f.property(field: "jnlpLauncher")
