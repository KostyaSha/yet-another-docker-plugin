package com.github.kostyasha.yad.launcher.DockerComputerSSHLauncher

import lib.FormTagLib

def f = namespace(FormTagLib);

f.property(field: "sshConnector")

f.entry(title: _("Docker Network"), field: "dockerNetwork") {
    f.textbox()
}
