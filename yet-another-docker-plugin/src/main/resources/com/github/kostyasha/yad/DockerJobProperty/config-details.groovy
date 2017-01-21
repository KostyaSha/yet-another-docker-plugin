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
    curSlaveJobConfig = instance.slaveJobConfig
    f.dropdownList(name: "slaveJobConfig", title: _("Docker agent configuration")) {
        // all possible descriptors.
        getAllSlaveJobConfigurationDescriptors().each { desc ->
            if (desc != null) {
                f.dropdownListBlock(
                        value: desc.clazz.name,
                        name: desc.displayName,
                        selected: instance.slaveJobConfig == null ?
                                "false" : instance.slaveJobConfig.descriptor.equals(desc),
                        title: desc.displayName
                ) {
                    descriptor = desc
//                    if (instance.slaveJobConfig != null && instance.slaveJobConfig.descriptor.equals(desc)) {
                        instance = instance.slaveJobConfig
//                    }
                    f.invisibleEntry() {
                        input(type: "hidden", name: "stapler-class", value: desc.clazz.name)
                    }
                    st.include(from: desc, page: desc.configPage, optional: "true")
                }
            } else {
                println("descriptor is null")
            }
        }
    }
}
