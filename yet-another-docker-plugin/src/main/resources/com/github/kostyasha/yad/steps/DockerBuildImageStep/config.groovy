package com.github.kostyasha.yad.steps.DockerBuildImageStep

import lib.FormTagLib

def f = namespace(FormTagLib);
def st = namespace("jelly:stapler")


f.property(field: "buildImage")
