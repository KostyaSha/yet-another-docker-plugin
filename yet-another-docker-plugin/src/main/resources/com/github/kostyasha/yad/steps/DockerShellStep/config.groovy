package com.github.kostyasha.yad.steps.DockerShellStep

import lib.FormTagLib

import static com.github.kostyasha.yad.connector.YADockerConnector.YADockerConnectorDescriptor.allDockerConnectorDescriptors

def f = namespace(FormTagLib);
def st = namespace("jelly:stapler")


f.dropdownList(name: "connector", title: _("Connection Source"),
        help: descriptor.getHelpFile('connector')) {
    allDockerConnectorDescriptors().each { ld ->
        if (ld != null) {
            f.dropdownListBlock(value: ld.clazz.name,
                    name: ld.displayName,
                    selected: instance.connector == null ? false : instance.connector.descriptor.equals(ld),
                    title: ld.displayName) {
                descriptor = ld
                if (instance.connector != null && instance.connector.descriptor.equals(ld)) {
                    instance = instance.connector
                }
                f.invisibleEntry() {
                    input(type: "hidden", name: "stapler-class", value: ld.clazz.name)
                }
                st.include(from: ld, page: ld.configPage, optional: "true")
            }
        }
    }
}

f.property(field: "containerLifecycle")