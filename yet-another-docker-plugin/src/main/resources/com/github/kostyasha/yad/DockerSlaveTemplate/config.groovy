package com.github.kostyasha.yad.DockerSlaveTemplate

import com.github.kostyasha.yad.DockerSlaveTemplate
import com.github.kostyasha.yad.utils.DockerFunctions
import hudson.model.Slave
import lib.FormTagLib

def f = namespace(FormTagLib)
def st = namespace("jelly:stapler")
def descriptorPath = "/descriptor/" + DockerSlaveTemplate.class.getName()

// when added to heteroList
if (instance == null) {
    instance = new DockerSlaveTemplate();
}

f.entry(title: "Internal template ID") {
    f.textbox(field: "id", readonly: "true")
}

f.entry(title: _("Max Instances"), field: "maxCapacity") {
    f.number()
}

f.section(title: _("Docker Container Lifecycle")) {
    f.block() {
        f.property(field: "dockerContainerLifecycle")
    }
}

f.section(title: _("Jenkins Slave Config")) {

    f.entry(title: _("Remote Filing System Root"), field: "remoteFs") {
        f.textbox()
    }

    f.entry(title: _("Labels"), field: "labelString",
            help: descriptorPath + "/help/labelString") {
        f.textbox()
    }

    f.slave_mode(name: "mode", node: instance)

    f.advanced(title: _("Experimental Options"), align: "left") {
        f.dropdownList(name: "retentionStrategy", title: _("Availability"),
                help: "/help/system-config/master-slave/availability.html") {
            DockerFunctions.getRetentionStrategyDescriptors().each { sd ->
                if (sd != null) {
                    def prefix = sd.displayName.equals("Docker Once Retention Strategy") ? "" : "Experimental: "

                    f.dropdownListBlock(value: sd.clazz.name, name: sd.displayName,
                            selected: instance.retentionStrategy == null ?
                                    false : instance.retentionStrategy.descriptor.equals(sd),
                            title: prefix + sd.displayName) {
                        descriptor = sd
                        if (instance.retentionStrategy != null && instance.retentionStrategy.descriptor.equals(sd)) {
                            instance = instance.retentionStrategy
                        }
                        f.invisibleEntry() {
                            input(type: "hidden", name: "stapler-class", value: sd.clazz.name)
                        }
                        st.include(from: sd, page: sd.configPage, optional: "true")
                    }
                }
            }
        }

        f.entry(title: _("# of executors"), field: "numExecutors") {
            f.number(default: "1")
        }
    }

    f.dropdownList(name: "launcher", title: _("Launch method"),
            help: descriptor.getHelpFile('launcher')) {
        DockerFunctions.dockerComputerLauncherDescriptors.each { ld ->
            if (ld != null) {
                f.dropdownListBlock(value: ld.clazz.name,
                        name: ld.displayName,
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

//    f.descriptorList(
//            title: _("Node Properties"),
//            descriptors: DockerFunctions.getNodePropertyDescriptors(Slave.class),
//            field: "nodeProperties"
//    )
    f.entry(title: _("Node Properties")) {
        f.hetero_list(
                descriptors: DockerFunctions.getNodePropertyDescriptors(Slave.class),
                items: instance.nodeProperties,
                oneEach: true,
                hasHeader: true,
                name: "nodeProperties"
        )
    }

    f.entry(title: _("Remote FS Root Mapping"), field: "remoteFsMapping") {
        f.textbox()
    }
}

