package com.github.kostyasha.yad.connector.DockerCloudConnectorId

import lib.FormTagLib

def f = namespace(FormTagLib)

f.entry(field: "cloudId", title: "YADocker Connector") {
    f.select(name: "cloudId")
}
