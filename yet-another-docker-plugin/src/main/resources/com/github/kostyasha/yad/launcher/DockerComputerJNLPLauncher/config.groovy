package com.github.kostyasha.yad.launcher.DockerComputerJNLPLauncher

import com.github.kostyasha.yad.launcher.DockerComputerJNLPLauncher
import lib.FormTagLib

import static com.github.kostyasha.yad.launcher.DockerComputerJNLPLauncher.DEFAULT_TIMEOUT
import static com.github.kostyasha.yad.launcher.DockerComputerJNLPLauncher.DEFAULT_USER

def f = namespace(FormTagLib);

if (instance == null) {
    instance = new DockerComputerJNLPLauncher()
}

f.entry(title: _("Linux user"), field: "user", default: DEFAULT_USER) {
    f.textbox()
}

f.entry(title: _("Launch timeout"), field: "launchTimeout", default: DEFAULT_TIMEOUT) {
    f.number()
}

f.entry(title: _("Slave (slave.jar) options"), field: "slaveOpts") {
    f.textbox()
}

f.entry(title: _("Slave JVM options"), field: "jvmOpts") {
    f.textbox()
}

f.entry(title: _("JNLP tunnel"), field: "tunnel") {
    f.textbox()
}

f.entry(title: _("Different jenkins master URL"), field: "jenkinsUrl") {
    f.textbox()
}

f.entry(title: _("Ignore certificate check"), field: "noCertificateCheck", default: false) {
    f.checkbox()
}

f.entry(title: _("Reconnect"), field: "reconnect", default: false) {
    f.checkbox()
}
