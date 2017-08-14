package com.github.kostyasha.yad.commons.DockerCreateContainer

import lib.FormTagLib
import com.github.kostyasha.yad.commons.DockerCreateContainer

def f = namespace(FormTagLib);
def st = namespace("jelly:stapler")

if (instance == null) {
    instance = new DockerCreateContainer()
}

def descriptorPath = "/descriptor/" + DockerCreateContainer.class.getName()

// until https://issues.jenkins-ci.org/browse/JENKINS-26407
// all expandableTextboxes are textarea
f.advanced(title: _("Create Container settings"), align: "left") {
    f.entry(title: _("Docker Command"), field: "command") {
        f.textbox()
    }

    f.entry(title: _("Hostname"), field: "hostname") {
        f.textbox()
    }

    f.entry(title: _("DNS"), field: "dnsString") {
        f.textbox()
    }

    f.entry(title: _("Volumes"), field: "volumesString") {
        f.textarea()
    }

    f.entry(title: _("Volumes From"), field: "volumesFromString") {
        f.textarea()
    }

    f.entry(title: _("Environment"), field: "environmentString") {
        f.textarea()
    }

    f.entry(title: _("Port bindings"), field: "bindPorts") {
        f.textbox()
    }

    f.entry(title: _("Bind all declared ports"), field: "bindAllPorts") {
        f.checkbox()
    }

    f.entry(title: _("Memory Limit in MB"), field: "memoryLimit") {
        f.number(name: "memoryLimit", min: "0", step: "1")
    }

    f.entry(title: _("CPU Shares"), field: "cpuShares") {
        f.number(name: "cpuShares", min: "0", step: "1")
    }

    f.entry(title: _("Run container privileged"), field: "privileged") {
        f.checkbox()
    }

    f.entry(title: _("Allocate a pseudo-TTY"), field: "tty") {
        f.checkbox()
    }

    f.entry(title: _("MAC address"), field: "macAddress") {
        f.textbox()
    }

    f.entry(title: _("Extra Hosts"), field: "extraHostsString") {
        f.textarea()
    }

    f.entry(title: _("Network Mode"), field: "networkMode") {
        f.textbox()
    }

    f.entry(title: _("Devices"), field: "devicesString") {
        f.textarea()
    }

    f.entry(title: _("Cpuset constraint: CPUs"), field: "cpusetCpus") {
        f.textbox()
    }

    f.entry(title: _("Cpuset constraint: MEMs"), field: "cpusetMems") {
        f.textbox()
    }

    f.entry(title: _("Links"), field: "linksString") {
        f.textarea()
    }

    f.entry(title: _("Restart policy"), field: "restartPolicy", help: "${descriptorPath}/help/restartSlavePolicy") {
        f.enum() {
            text("${my.name().toLowerCase().replace('_', '-')}")
        }
    }
}
