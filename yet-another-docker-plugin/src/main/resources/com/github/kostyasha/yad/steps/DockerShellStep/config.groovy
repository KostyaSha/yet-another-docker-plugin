package com.github.kostyasha.yad.steps.DockerShellStep

import com.github.kostyasha.yad.steps.DockerShellStep
import lib.FormTagLib

import static com.github.kostyasha.yad.connector.YADockerConnector.YADockerConnectorDescriptor.allDockerConnectorDescriptors

def f = namespace(FormTagLib);
def st = namespace("jelly:stapler")

if (instance == null) {
    instance = new DockerShellStep()
}

// prepare env
f.entry(title: _("Executor Script"), field: "executorScript") {
    f.textarea(
            //                class: "fixed-width",
            'codemirror-mode': 'shell',
            'codemirror-config': "mode: 'text/x-sh', lineNumbers: true"
    )
}

// script itself
f.entry(title: _("Shell Script"), field: "shellScript") {
    f.textarea(
            //                class: "fixed-width",
            'codemirror-mode': 'shell',
            'codemirror-config': "mode: 'text/x-sh', lineNumbers: true"
    )
}

// connection
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

// container lifecycle
f.property(field: "containerLifecycle")
