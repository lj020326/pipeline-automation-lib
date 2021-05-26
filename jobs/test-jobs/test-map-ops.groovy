#!/usr/bin/env groovy

Map config=[:]

//boolean runPostDeployTestsDefault = true

//config.runPostDeployTests=false

//boolean runPostDeployTestsDefault = config.runPostDeployTests ?: true
//boolean runPostDeployTestsDefault = config.get("runPostDeployTests", true)

// ref: https://stackoverflow.com/questions/48806509/groovy-map-getkey-default-mutates-the-map
boolean runPostDeployTestsDefault = config.getOrDefault("runPostDeployTests", true)

//println("config?.runPostDeployTests=${config?.runPostDeployTests}")

println("runPostDeployTestsDefault=${runPostDeployTestsDefault}")

println("config=${config}")
