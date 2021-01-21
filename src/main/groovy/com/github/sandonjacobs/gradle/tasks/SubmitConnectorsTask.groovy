package com.github.sandonjacobs.gradle.tasks

import com.github.sandonjacobs.gradle.ConnectRest
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

@Slf4j
class SubmitConnectorsTask extends DefaultTask {

    @Input
    @Optional
    @Option(option = "connector-endpoint", description = "override the connector config endpoint here.")
    String connectorEndpoint = project.extensions.kafkaConnect.connectEndpoint

    @Input
    @Optional
    @Option(option = "print-only", description = "only prints the connector configs, if present do not post to endpoint")
    Boolean dryRunFlag = false

    @Input
    @Option(option = "connector-subdir", description = "Only project a given subdirectory of the `sourceBase/connectorSourceName` directory")
    String connectorSubDir = project.extensions.kafkaConnect.defaultConnectorSubDirectory

    @Input
    @Optional
    @Option(option = "jsonnet-tla-str-args", description = "Additional jsonnet args, maps to --tla-str <var>[=<val>].")
    String tlaStringArgs

    SubmitConnectorsTask() {
        group = project.extensions.kafkaConnect.taskGroup
        description = "Load Kafka Connector Configuration files."

        outputs.upToDateWhen { false }
    }

    @TaskAction
    def loadConnectors() {
        logger.debug("input param connect-endpoint => {}", connectorEndpoint)
        def rest = new ConnectRest()
        rest.setRestUrl(connectorEndpoint)

        def pathBase = project.extensions.kafkaConnect.getConnectorsPath()
        def connectorPath = "$pathBase/$connectorSubDir"

        logger.debug("connectorPath => {}", connectorPath)
        def listing = new File(connectorPath)
        listing.eachFile { f ->
            logger.debug(f.name)
            if (!f.isDirectory() && f.name.endsWith(".jsonnet")) {
                def jsonnetOutput = jsonnet(f).toString()
                def name = new JsonSlurper().parseText(jsonnetOutput).name
                processPayload(name, jsonnetOutput)
            }
        }
    }

    def processPayload(String name, String payload) {
        def rest = new ConnectRest()
        rest.setRestUrl(connectorEndpoint)

        println "Connector Name => $name"
        println "Connect Endpoint => ${rest.getRestUrl()}"
        if (dryRunFlag) {
            println "*** DRY RUN ***"
            println payload
        }
        else {
            println payload
            rest.execCreateConnector(name, payload, 10000, [:]) // todo: temp empty map
        }
    }

    def jsonnet(File f) {
        def sout = new StringBuilder(), serr = new StringBuilder()
        def command = "jsonnet ${f.path} ${addTlaArgs()}"
        print(command)

        def proc = command.execute()
        proc.consumeProcessOutput(sout, serr)
        proc.waitForOrKill(1000)
        logger.debug "out> $sout\nerr> $serr"

        if (serr.length()  > 0) {
            throw new GradleException("ERROR Calling JSONNET for file ${f.name}: $serr")
        }
        else {
            return sout
        }
    }

    def addTlaArgs() {
        def prefix = "--tla-str"
        if (tlaStringArgs != null && tlaStringArgs.length() > 0) {
            def args = tlaStringArgs.tokenize('|')

            def result = args.inject { acc, val -> "--tla-str $acc --tla-str $val" }
            println("**** POST INJECT *******" + result)
            return result
        }
        else return ""
    }


}
