package edu.unh.cs980.experiment

import net.sourceforge.argparse4j.inf.Subparser

interface ExperimentInterface {
    fun register(methodType: String, parser: Subparser)
}