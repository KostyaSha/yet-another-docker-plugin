package com.github.kostyasha.yad.DockerManagementLink

import com.github.kostyasha.yad.DockerManagementLink
import lib.FormTagLib
import lib.LayoutTagLib
import lib.YadTagLib

def l = namespace(LayoutTagLib)
def st = namespace("jelly:stapler")
def f = namespace(FormTagLib);
def yad = namespace(YadTagLib)

l.layout(permission: app.ADMINISTER) {
    l.header(title: my.displayName)
    l.main_panel {
        h1(my.displayName)
        f.entry(title: _("Links")) {
            it = new DockerManagementLink()
            yad.expandableTextbox(field: "linksString")
        }
    }
}