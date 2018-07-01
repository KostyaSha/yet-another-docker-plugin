package com.github.kostyasha.yad.launcher.DockerComputerIOLauncher

import com.github.kostyasha.yad.launcher.DockerComputerIOLauncher
import lib.FormTagLib

import static com.github.kostyasha.yad.launcher.DockerComputerJNLPLauncher.DEFAULT_USER

def f = namespace(FormTagLib);

if (instance == null) {
    instance = new DockerComputerIOLauncher()
}

f.entry(title: _("Java path"), field: "javaPath") {
    f.textbox()
}

f.entry(title: _("JVM Options"), field: "jvmOpts") {
    f.textbox()
}