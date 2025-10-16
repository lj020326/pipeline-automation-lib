package com.dettonville.pipeline.versioning

import spock.lang.Specification
import spock.lang.Unroll

class TestComparableSemanticVersion extends Specification {

    @Unroll
    def 'bump #version with #level (#details)'(String version, Bump level, String result, String details) {
        when:
        def semver = new ComparableSemanticVersion(version).bump(level)
        then:
        semver.text() == result
        where:
        version                  | level        | result   | details
        "0.2.1"                  | Bump.RELEASE | "0.2.1"  | "no-op"
        "0.2.1"                  | Bump.MAJOR   | "1.0.0"  | "major"
        "1.9.1"                  | Bump.MAJOR   | "2.0.0"  | "minor"
        "v0.2.1"                 | Bump.MAJOR   | "1.0.0"  | "v major"
        "V0.2.1"                 | Bump.MAJOR   | "1.0.0"  | "V major"
        "0.2.1"                  | Bump.MINOR   | "0.3.0"  | "minor"
        "1.9.1"                  | Bump.MINOR   | "1.10.0" | "patch"
        "0.2.1"                  | Bump.PATCH   | "0.2.2"  | "patch"
        "0.2.1-rc1.0"            | Bump.PATCH   | "0.2.1"  | "strip pre-release"
        "0.2.1-rc1.0+build-1234" | Bump.PATCH   | "0.2.1"  | "strip pre-release and build"
    }

    @Unroll
    def 'bump prerel #version - #details'(String version, String prerel, String result, String details) {
        when:
        def semver = new ComparableSemanticVersion(version).prerel(prerel)
        then:
        semver.text() == result
        where:
        version         | prerel | result       | details
        "0.2.1"         | "rc.1" | "0.2.1-rc.1" | "add prerel"
        "0.2.1-0.2+b13" | "rc.1" | "0.2.1-rc.1" | "replace and strip build metadata"
        "0.2.1+b13"     | "rc.1" | "0.2.1-rc.1" | "strip build metadata"
    }

    @Unroll
    def 'bump prerel #version - #prerel - failing'(String version, String prerel) {
        when:
        ComparableSemanticVersion.parse(version).prerel(prerel)
        then:
        thrown(IllegalArgumentException)
        where:
        version  | prerel
        "1.0.0"  | "x.7.z.092"
        "1.0.0"  | "x.=.z.92"
        "1.0.0"  | "x.7.z..92"
        "1.0.0"  | ".x.7.z.92"
        "1.0.0"  | "x.7.z.92."
        "1.00.0" | "x.7.z.92"
    }

    @Unroll
    def 'bump build #version - #details'(String version, String build, String result, String details) {
        when:
        def semver = new ComparableSemanticVersion(version).build(build)
        then:
        semver.text() == result
        where:
        version          | build       | result            | details
        "0.2.1+b13"      | "b.1"       | "0.2.1+b.1"       | "replace build metadata"
        "0.2.1-rc12+b13" | "b.1"       | "0.2.1-rc12+b.1"  | "preserve prerel, replace build metadata"
        "1.0.0"          | "x.7.z.092" | "1.0.0+x.7.z.092" | _
    }

    @Unroll
    def 'bump build #version - #build - failing #details'(String version, String build, String details) {
        when:
        ComparableSemanticVersion.parse(version).build(build)
        then:
        thrown(IllegalArgumentException)
        where:
        version  | build       | details
        "1.0.0"  | "x.=.z.92"  | _
        "1.0.0"  | "x.7.z..92" | _
        "1.0.0"  | ".x.7.z.92" | _
        "1.0.0"  | "x.7.z.92." | _
        "1.00.0" | "x.7.z.92"  | _
        "1.0.0"  | "7.z\$.92"  | "bump invalid character in build-metadata: \$"
        "1.0.0"  | "7.z.92._"  | "bump invalid character in build-metadata: _"
        "1.0.0"  | "7.z..92"   | "bump empty identifier in build-metadata (embedded)"
        "1.0.0"  | ".x.7.z.92" | "bump empty identifier in build-metadata (leading)"
        "1.0.0"  | "z.92."     | "bump empty identifier in build-metadata (trailing)"
    }

    def 'get major'() {
        when:
        def semver = new ComparableSemanticVersion("0.2.1-rc1.0+build-1234")
        then:
        semver.getMajor() == 0
    }

    def 'get minor'() {
        when:
        def semver = new ComparableSemanticVersion("0.2.1-rc1.0+build-1234")
        then:
        semver.getMinor() == 2
    }

    def 'get patch'() {
        when:
        def semver = new ComparableSemanticVersion("0.2.1-rc1.0+build-1234")
        then:
        semver.getPatch() == 1
    }

    @Unroll
    def 'get prerel #prerel from #version'(String version, String prerel) {
        when:
        def semver = new ComparableSemanticVersion(version)
        then:
        semver.getPrerel() == prerel
        where:
        version                  | prerel
        "0.2.1-rc1.0+build-1234" | "rc1.0"
        "1.0.0-alpha"            | "alpha"
        "1.0.0-alpha.1"          | "alpha.1"
        "1.0.0-0alpha.1"         | "0alpha.1"
        "1.0.0-0.3.7"            | "0.3.7"
        "1.0.0-x.7.z.92"         | "x.7.z.92"
        "1.0.0-x-.7.--z.92-"     | "x-.7.--z.92-"
    }

    @Unroll
    def 'get build #build from #version'(String version, String build) {
        when:
        def semver = new ComparableSemanticVersion(version)
        then:
        semver.getBuild() == build
        where:
        version                      | build
        "0.2.1-rc1.0+build-1234"     | "build-1234"
        "1.0.0-alpha+001"            | "001"
        "1.0.0+20130313144700"       | "20130313144700"
        "1.0.0-beta+exp.sha.5114f85" | "exp.sha.5114f85"
        "1.0.0+exp.sha.5114f85"      | "exp.sha.5114f85"
        "1.0.0-x.7.z.92+02"          | "02"
        "1.0.0-x.7.z.92+-alpha-2"    | "-alpha-2"
        "1.0.0-x.7.z.92+-alpha-2-"   | "-alpha-2-"
    }

    def 'get release'() {
        setup:
        def semver = new ComparableSemanticVersion("0.2.1-rc1.0+build-1234").release()
        expect:
        semver.text() == "0.2.1"
    }

    def 'bump major'() {
        when:
        def semver = new ComparableSemanticVersion("1.2.3").major()
        then:
        semver.major == 2
        and:
        semver.text() == "2.0.0"
    }

    def 'bump minor'() {
        when:
        def semver = new ComparableSemanticVersion("1.2.3").minor()
        then:
        semver.minor == 3
        and:
        semver.text() == "1.3.0"
    }

    def 'bump patch'() {
        when:
        def semver = new ComparableSemanticVersion("1.2.3").patch()
        then:
        semver.patch == 4
        and:
        semver.text() == "1.2.4"
    }

    def 'bump null'() {
        when:
        ComparableSemanticVersion.parse("1.2.3").bump(null)
        then:
        def ex = thrown(IllegalArgumentException)
        and:
        ex.message == "Bump ´null´ is unkown"
    }

    def 'bump release'() {
        when:
        def semver = new ComparableSemanticVersion("1.2.3").bump(Bump.RELEASE)
        then:
        semver.text() == "1.2.3"
    }

    @Unroll
    def '#message #version'(String message, String version) {
        when:
        new ComparableSemanticVersion(version)
        then:
        thrown(IllegalArgumentException)
        where:
        message       | version
        "bad version" | "foo"
        "bad minor"   | "1.2."
        "bad patch"   | "1.2.4-"
        "bad build"   | "1.2.4+"
    }

    @Unroll
    def 'validate fails #version'(String version) {
        when:
        def result = ComparableSemanticVersion.validate(version)
        then:
        !result
        where:
        version            | _
        "1."               | _
        "1.2"              | _
        ".2.3"             | _
        "01.9.1"           | _
        "1.09.1"           | _
        "1.9.01"           | _
        "1.9.00"           | _
        "1.9a.0"           | _
        "-1.9.0"           | _
        "1.0.0-x.7.z\$.92" | _
        "1.0.0-x_.7.z.92"  | _
        "1.0.0-x.7.z.092"  | _
        "1.0.0-x.07.z.092" | _
        "1.0.0-x.7.z..92"  | _
        "1.0.0-.x.7.z.92"  | _
        "1.0.0-x.7.z.92."  | _
        "1.0.0-x+7.z\$.92" | "invalid character in build-metadata: \$"
        "1.0.0-x+7.z.92._" | "invalid character in build-metadata: _"
        "1.0.0+7.z\$.92"   | "invalid character in build-metadata after patch"
        "1.0.0-x+7.z..92"  | "empty identifier in build-metadata (embedded)"
        "1.0.0+.x.7.z.92"  | "empty identifier in build-metadata (leading)"
        "1.0.0-x.7+z.92."  | "empty identifier in build-metadata (trailing)"

    }

    @Unroll
    def 'validate success #version'(String version) {
        when:
        def result = ComparableSemanticVersion.validate(version)
        then:
        result
        where:
        version         | _
        "0.0.0"         | _
        "1.2.3-alpha01" | _
    }

    @Unroll
    def '#v1 #operator #v2'(def v1, char operator, def v2) {
        setup:
        v1 = ComparableSemanticVersion.parse(v1)
        v2 = ComparableSemanticVersion.parse(v2)
        expect:
        switch (operator) {
            case '<':
                v1 <=> v2 == -1
                and:
                v2 <=> v1 == 1
                break;
            case '>':
                v1 <=> v2 == +1
                and:
                v2 <=> v1 == -1
                break
            case '=':
                v1 <=> v2 == 0
                and:
                v2 <=> v1 == 0
                break
            default:
                throw new IllegalArgumentException("Unknown operator '${operator}'")
        }
        where:
        v1                  | operator | v2
        "1.2.3"             | '<'      | "2.2.3"
        "1.0.0-alpha"       | '<'      | "1.0.0-alpha.1"
        "1.0.0-alpha.1"     | '<'      | "1.0.0-alpha.beta"
        "1.0.0-alpha.beta"  | '<'      | "1.0.0-beta"
        "1.0.0-beta"        | '<'      | "1.0.0-beta.2"
        "1.0.0-beta.2"      | '<'      | "1.0.0-beta.11"
        "1.0.0-beta.2.4"    | '>'      | "1.0.0-beta.2.3"
        "1.0.0-beta.2.4"    | '<'      | "1.0.0-beta.2.4.0"
        "1.0.0-beta.2.ab"   | '<'      | "1.0.0-beta.2.ab.0"
        "1.0.0-beta.2.ab.1" | '>'      | "1.0.0-beta.2.ab.0"
        "1.0.0-beta.11"     | '<'      | "1.0.0-rc.1"
        "1.0.0-rc.1"        | '<'      | "1.0.0"
        "1.0.0"             | '>'      | "1.0.0-rc.1"
        "1.0.0-alpha"       | '>'      | "1.0.0-666"
        "1.0.0"             | '='      | "1.0.0"
        "1.0.1"             | '>'      | "1.0.0-rc1"
        "1.0.0-beta2"       | '>'      | "1.0.0-beta11"
        "1.0.0-2"           | '<'      | "1.0.0-11"
        "1.0.0-beta1+a"     | '<'      | "1.0.0-beta2+z"
        "1.0.0-beta2+x"     | '='      | "1.0.0-beta2+y"
        "1.0.0-12.beta2+x"  | '>'      | "1.0.0-11.beta2+y"
        "1.0.0+x"           | '='      | "1.0.0+y"
        "0.2.1"             | '<'      | "0.2.2"
        "1.2.1"             | '='      | "1.2.1"
        "0.3.1"             | '>'      | "0.2.5"
        "1.0.0+hash"        | '<'      | "1.0.0"
    }

    def 'equals same object '() {
        setup:
        def v = ComparableSemanticVersion.parse("1.2.3")
        when:
        def v2 = v
        then:
        v == v2
    }

    def 'test equals same values '() {
        setup:
        def v1 = ComparableSemanticVersion.parse("1.2.3")
        def v2 = ComparableSemanticVersion.parse("1.2.3")
        expect:
        v1 == v2
    }


    def 'test clone'() {
        setup:
        def v1 = ComparableSemanticVersion.parse("1.2.3-rc1+abc")
        when:
        def v2 = v1.clone()
        then:
        v1 == v2
        and:
        v2.text() == "1.2.3-rc1+abc"
    }

    def 'test toString'() {
        setup:
        def v1 = ComparableSemanticVersion.parse("1.2.3-rc1+abc")
        expect:
        v1.text() == v1.toString()
    }
}
