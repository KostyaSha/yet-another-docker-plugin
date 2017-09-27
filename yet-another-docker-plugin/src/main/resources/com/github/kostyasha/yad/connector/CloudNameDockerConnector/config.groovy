package com.github.kostyasha.yad.connector.CloudNameDockerConnector

import lib.FormTagLib

def f = namespace(FormTagLib)

f.entry(field: "cloudName", title: "Connector from YADocker Cloud") {
    f.select(name: "cloudName")
}
