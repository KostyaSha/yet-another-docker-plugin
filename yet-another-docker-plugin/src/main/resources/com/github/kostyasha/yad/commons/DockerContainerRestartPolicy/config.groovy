package com.github.kostyasha.yad.commons.DockerContainerRestartPolicy

import lib.FormTagLib

def f = namespace(FormTagLib);


f.entry(title: _("Restart policy"), field: "policyName") {
    f.enum() {
        text("${my.name().toLowerCase().replace('_', '-')}")
    }
}

f.entry(title: _("Max retry count"), field: "maximumRetryCount") {
    f.number(name: "maximumRetryCount", min: "0", step: "1", default: "0")
}
