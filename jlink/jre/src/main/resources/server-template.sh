#!/bin/bash

usage() {
    echo
    echo "Start ${mainJarName} from this runtime image."
    echo
    echo "Usage: ${scriptName} <options> [serverArg]..."
    echo
    echo "Options:"
    echo "     -j | --jvm <option>     Add a JVM option."
    echo "     -d | --debug            Add JVM debug options. Uses JAVA_DEBUG env var if present, or a default if not."
    echo "     -c | --cds              Use the CDS archive."
    echo
    exit 0
}

main() {
    init "$@"
    start
}

start() {
    ${javaCommand} ${jvmOptions} -jar ${mainJar} ${serverOptions}
}

init() {
    readonly mainJarName="<MAIN_JAR_NAME>"
    readonly scriptName=$(basename "${0}")
    readonly binDir=$(dirname "${0}")
    readonly homeDir=$(cd "${binDir}"/..; pwd)
    readonly javaCommand="${binDir}/java"
    readonly mainJar="${homeDir}/app/${mainJarName}"
    jvmOptions=
    serverOptions=

    while (( ${#} > 0 )); do
        case "${1}" in
            -h | --help) usage ;;
            -c | --cds) setCds ;;
            -j | --jvm) shift; appendVar jvmOptions "${1}" ;;
            *) appendVar serverOptions "${1}" ;;
        esac
        shift
    done
}

setCds() {
    readonly cdsArchive="${homeDir}/lib/server.jsa"
    [[ -e ${cdsArchive} ]] || fail "${cdsArchive} not found"
    appendVar jvmOptions "-XX:SharedArchiveFile=${cdsArchive}"
}

setDebug() {
    if [[ ${JAVA_DEBUG} ]]; then
        appendVar jvmOptions "${JAVA_DEBUG}"
    else
        appendVar jvmOptions "-Xdebug -Xnoagent -Djava.compiler=none -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"
    fi
}

appendVar() {
    local var=${1}
    local value=${2}
    local sep=${3:- }
    export ${var}="${!var:+${!var}${sep}}${value}"
}

fail() {
    echo "${1}"
    exit 1
}

main "$@"
