package com.github.kostyasha.yad.credentials.DockerRegistryAuthCredentials

import lib.FormTagLib

def f = namespace(FormTagLib)
def st = namespace("jelly:stapler")

f.entry(title: _("Email"), field: "email") {
    f.textbox()
}

st.include(page: "credentials", class: descriptor.clazz)
