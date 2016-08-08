package com.github.kostyasha.yad.connector.NettyDockerJavaConnector

import lib.FormTagLib

def f = namespace(FormTagLib)
def st = namespace("jelly:stapler")


st.include(page: "config", class: descriptor.clazz)
