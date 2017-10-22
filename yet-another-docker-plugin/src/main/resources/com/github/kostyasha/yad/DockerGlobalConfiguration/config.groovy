package com.github.kostyasha.yad.DockerGlobalConfiguration

import static com.github.kostyasha.yad.other.cloudorder.DockerCloudOrder.DockerCloudOrderDescriptor.allDockerCloudOrderDescriptors

f.dropdownList(name: "cloudOrder", title: _("Docker Cloud Provisioning Strategy Order")) {
    allDockerCloudOrderDescriptors().each { sd ->
        if (sd != null) {
            f.dropdownListBlock(value: sd.clazz.name, name: sd.displayName,
                    selected: instance.cloudOrder == null ?
                            false : instance.cloudOrder.descriptor.equals(sd),
                    title:  sd.displayName) {
                descriptor = sd
                if (instance.cloudOrder != null && instance.cloudOrder.descriptor.equals(sd)) {
                    instance = instance.cloudOrder
                }
                f.invisibleEntry() {
                    input(type: "hidden", name: "stapler-class", value: sd.clazz.name)
                }
                st.include(from: sd, page: sd.configPage, optional: "true")
            }
        }
    }
}