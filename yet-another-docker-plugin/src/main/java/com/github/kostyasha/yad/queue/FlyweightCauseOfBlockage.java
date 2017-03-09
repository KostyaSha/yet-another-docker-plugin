package com.github.kostyasha.yad.queue;

import hudson.model.queue.CauseOfBlockage;

/**
 * @author Kanstantsin Shautsou
 */
public class FlyweightCauseOfBlockage extends CauseOfBlockage {
    @Override
    public String getShortDescription() {
        return "Don't run FlyweightTask on Docker node";
    }
}
