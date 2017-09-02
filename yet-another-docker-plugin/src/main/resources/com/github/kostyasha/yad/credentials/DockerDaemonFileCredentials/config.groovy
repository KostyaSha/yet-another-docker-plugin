package com.github.kostyasha.yad.credentials.DockerDaemonFileCredentials

import lib.FormTagLib

def f = namespace(FormTagLib)
def st = namespace("jelly:stapler")

f.entry(title: _("Path to directory with keys"), field: "dockerCertPath") {
    f.textbox()
}
