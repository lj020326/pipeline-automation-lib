/**
 * Return the version number contained in pom.xml
 */
String call() {

    def pom = readMavenPom file: 'pom.xml'
    return pom.version
}
