package com.github.kostyasha.yad.DockerCloud

import com.github.kostyasha.yad.utils.DockerFunctions
import lib.FormTagLib

def f = namespace(FormTagLib)

f.entry(title: _("Cloud Name"), field: "name") {
    f.textbox()
}

f.block(title: "Connector") {
    f.property(field: "connector")
}


f.dropdownList(name: "Connector", title: _("Connector")) {
    DockerFunctions.dockerComputerLauncherDescriptors.each { ld ->
        if (ld != null) {
            f.dropdownListBlock(value: ld.clazz.name, name: ld.displayName,
                    selected: instance.launcher == null ? false : instance.launcher.descriptor.equals(ld),
                    title: ld.displayName) {
                descriptor = ld
                if (instance.launcher != null && instance.launcher.descriptor.equals(ld)) {
                    instance = instance.launcher
                }
                f.invisibleEntry() {
                    input(type: "hidden", name: "stapler-class", value: ld.clazz.name)
                }
                st.include(from: ld, page: ld.configPage, optional: "true")
            }
        }
    }
}

f.entry(title: _("Max Containers"), field: "containerCap") {
    f.number(default: 50)
}

f.entry(title: _("Images"), description: _("List of Images to be launched as slaves")) {
    f.repeatableHeteroProperty(
            field: "templates",
            hasHeader: true,
            addCaption: _("Add Docker Template"),
            deleteCaption: _("Delete Docker Template")
    )
}
