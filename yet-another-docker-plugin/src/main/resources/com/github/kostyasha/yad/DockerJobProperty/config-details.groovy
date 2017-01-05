package com.github.kostyasha.yad.DockerJobProperty

import com.github.kostyasha.yad.DockerJobProperty
import lib.FormTagLib

import static com.github.kostyasha.yad.connector.YADockerConnector.YADockerConnectorDescriptor.allDockerConnectorDescriptors
import static com.github.kostyasha.yad.jobconfig.SlaveJobConfig.SlaveJobConfigDescriptor.allSlaveJobConfigurationDescriptors

def f = namespace(FormTagLib);
def st = namespace("jelly:stapler")

if (instance == null) {
    instance = new DockerJobProperty()
}

f.section() {
    f.dropdownList(name: "slaveJobConfig", title: _("Docker agent configuration")) {
        // all possible descriptors.
        getAllSlaveJobConfigurationDescriptors().each { sd ->
            if (sd != null) {
                f.dropdownListBlock(value: sd.clazz.name, name: sd.displayName,
                        selected: instance.slaveJobConfig == null ?
                                "false" : instance.slaveJobConfig.descriptor.equals(sd),
                        title: sd.displayName) {
                    descriptor = sd
                    if (instance.slaveJobConfig != null && instance.slaveJobConfig.descriptor.equals(sd)) {
                        instance = instance.slaveJobConfig
                    }
                    f.invisibleEntry() {
                        input(type: "hidden", name: "stapler-class", value: sd.clazz.name)
                    }
                    st.include(from: sd, page: sd.configPage, optional: "true")
                }
            }
        }
    }
}
