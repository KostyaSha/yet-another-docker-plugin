package com.github.kostyasha.yad.launcher.DockerComputerSSHLauncher

import lib.FormTagLib

def f = namespace(FormTagLib);

f.property(field: "sshConnector")

f.entry(title: _("Restart slave on failure / daemon restart"), field: "restartSlave", default: false) {
    f.checkbox()
}