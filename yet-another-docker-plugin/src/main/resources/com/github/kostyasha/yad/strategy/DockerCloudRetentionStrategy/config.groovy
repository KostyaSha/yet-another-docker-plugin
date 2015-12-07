package com.github.kostyasha.yad.strategy.DockerCloudRetentionStrategy

import lib.FormTagLib

def f = namespace(FormTagLib);

f.entry(title: "Idle timeout", field: "idleMinutes") {
    f.number(default: 0)
}
