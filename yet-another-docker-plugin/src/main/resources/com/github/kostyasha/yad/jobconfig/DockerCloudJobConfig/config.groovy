package com.github.kostyasha.yad.jobconfig.DockerCloudJobConfig

import com.github.kostyasha.yad.DockerSlaveTemplate
import com.github.kostyasha.yad.jobconfig.DockerCloudJobConfig
import lib.FormTagLib

def f = namespace(FormTagLib)

if (instance == null) {
    instance = new DockerCloudJobConfig()
}

if (instance.template == null) {
    instance.template = new DockerSlaveTemplate()
    instance.template.setMaxCapacity(1)
    instance.template.setMode(hudson.model.Node.Mode.EXCLUSIVE)
    instance.template.setLabelString("automatically-changed")
}

f.entry {
    f.property(field: "connector")
}

f.entry {
    f.property(field: "template")
}
