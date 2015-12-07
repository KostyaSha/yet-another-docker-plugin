package com.github.kostyasha.yad.DockerContainerLifecycle

import lib.FormTagLib

def f = namespace(FormTagLib);

f.entry(title: _("Docker Image Name"), field: "image") {
    f.textbox()
}

f.section(title: "Pull Image Settings") {
    f.property(field: "pullImage")
}

//TODO ideally should be collapsible section
f.section(title: _("Create Container Settings")) {
    f.property(field: "createContainer")
}

f.section(title: "Stop Container Settings") {
    f.property(field: "stopContainer")
}

f.section(title: "Remove Container Settings") {
    f.property(field: "removeContainer")
}

//TODO allow pushing to default server?