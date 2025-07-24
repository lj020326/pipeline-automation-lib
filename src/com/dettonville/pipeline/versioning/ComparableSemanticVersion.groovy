package com.dettonville.pipeline.versioning

/////////////////////////
//
// ref: https://github.com/Wonno/groovy-semver-tool/blob/master/src/main/groovy/com/github/wonno/semver/Semver.groovy
//
// usage examples:
// import com.dettonville.pipeline.versioning.Semver
// 
// //version validation
// assert !Semver.validate("1.2.invalid")
// 
// //version change
// assert Semver.parse("1.2.3+abcd").prerel("rc1").minor().text()=="1.3.0"
// 
// //version comparison
// assert Semver.parse("1.0.7+acf430") < new Semver("1.0.6").patch()
// 
/////////////////////////

import groovy.transform.AutoClone
import groovy.transform.EqualsAndHashCode
import static groovy.transform.AutoCloneStyle.SERIALIZATION

import java.util.regex.Matcher
import java.util.regex.Pattern

enum Bump {
    MINOR,
    MAJOR,
    PATCH,
    RELEASE,
}

@EqualsAndHashCode
@AutoClone(style = SERIALIZATION)
class ComparableSemanticVersion implements Comparable<ComparableSemanticVersion>, Serializable {

    final int major
    final int minor
    final int patch
    final String prerel
    final String build

    private final static String NAT = '0|[1-9][0-9]*'
    private final static String ALPHANUM = '[0-9]*[A-Za-z-][0-9A-Za-z-]*'
    private final static String IDENT = "$NAT|$ALPHANUM"
    private final static String FIELD = '[0-9A-Za-z-]+'
    private final static Pattern SEMVER_REGEX = ~"^[vV]?($NAT)\\.($NAT)\\.($NAT)(\\-(${IDENT})(\\.(${IDENT}))*)?(\\+${FIELD}(\\.${FIELD})*)?\$"

    ComparableSemanticVersion(String version) {
// Matcher groups
//        0. ALL
//        1. MAJOR
//        2. MINOR
//        3. PATCH
//        4. PRERELEASE
//        8. BUILD_METADATA
        Matcher matcher = SEMVER_REGEX.matcher(version)
        if (matcher.matches()) {
            this.major = matcher.group(1).toInteger()
            this.minor = matcher.group(2).toInteger()
            this.patch = matcher.group(3).toInteger()
            this.prerel = matcher.group(4)?.substring(1)
            this.build = matcher.group(8)?.substring(1)
        } else {
            throw new IllegalArgumentException("Invalid semantic version ´${version}´")
        }
    }

    private ComparableSemanticVersion(int major, int minor, int patch) {
        this.major = major
        this.minor = minor
        this.patch = patch
    }

    static ComparableSemanticVersion parse(String version) throws IllegalArgumentException {
        return new ComparableSemanticVersion(version)
    }

    static boolean validate(String version) {
        try {
            parse(version)
            true
        } catch (IllegalArgumentException ignored) {
            false
        }
    }

    ComparableSemanticVersion patch() {
        return bump(Bump.PATCH)
    }

    ComparableSemanticVersion minor() {
        return bump(Bump.MINOR)
    }

    ComparableSemanticVersion major() {
        return bump(Bump.MAJOR)
    }

    ComparableSemanticVersion prerel(String prerel) {
        return new ComparableSemanticVersion(versionCore() + opt(prerel, '-'))
    }

    ComparableSemanticVersion build(String build) {
        return new ComparableSemanticVersion(versionCore() + opt(prerel, '-') + opt(build, '+'))
    }

    @NonCPS
    String text() {
        return versionCore() + opt(prerel, '-') + opt(build, '+')
    }

    @NonCPS
    private static String opt(String value, String sep) {
        return (value ? "${sep}${value}" : "");
    }

    ComparableSemanticVersion release() {
        return new ComparableSemanticVersion(versionCore())
    }

    @Override
    @NonCPS
    int compareTo(ComparableSemanticVersion v) {
        if (major != v.major) {
            return major <=> v.major
        }
        if (minor != v.minor) {
            return minor <=> v.minor
        }
        if (patch != v.patch) {
            return patch <=> v.patch
        }
        if (prerel != v.prerel) {
            if (prerel == null) {
                return 1
            }
            if (v.prerel == null) {
                return -1
            }

            // Precedence for two pre-release versions with the same major, minor, and patch version MUST be determined
            // by comparing each dot separated identifier from left to right until a difference is found as follows:
            List<String> lIdentifiers = prerel.tokenize('.')
            List<String> rIdentifiers = v.prerel.tokenize('.')

            // Compare element by element - until differnce is found, but only until the shorter identifiers list is consumed
            for (int i = 0; i < Math.min(lIdentifiers.size(), rIdentifiers.size()); i++) {
                String l = lIdentifiers[i]
                String r = rIdentifiers[i]
                if (l != r) {
                    // identifiers consisting of only digits are compared numerically and
                    if (l.isInteger() && r.isInteger()) {
                        return l.toInteger() <=> r.toInteger()
                    } else {
                        // identifiers with letters or hyphens are compared lexically in ASCII sort order.
                        return l <=> r
                    }
                }
            }
            //Numeric identifiers always have lower precedence than non-numeric identifiers.
            //A larger set of pre-release fields has a higher precedence than a smaller set,
            //if all of the preceding identifiers are equal.
            return lIdentifiers.size() <=> rIdentifiers.size()
        }

        if (build != v.build) {
            if (build == null) {
                return 1
            }
            if (v.build == null) {
                return -1
            }
        }
        return 0
    }

    @Override
    @NonCPS
    String toString() {
        return text()
    }

    @NonCPS
    private String versionCore() {
        return "${major}.${minor}.${patch}"
    }

    private ComparableSemanticVersion bump(Bump bump) {
        ComparableSemanticVersion result
        switch (bump) {
            case Bump.PATCH:
                if (prerel || build) {
                    result = new ComparableSemanticVersion(major, minor, patch)
                } else {
                    result = new ComparableSemanticVersion(major, minor, patch + 1)
                }
                break
            case Bump.MINOR:
                result = new ComparableSemanticVersion(major, minor + 1, 0)
                break
            case Bump.MAJOR:
                result = new ComparableSemanticVersion(major + 1, 0, 0)
                break
            case Bump.RELEASE:
                result = new ComparableSemanticVersion(major, minor, patch)
                break
            default:
                throw new IllegalArgumentException("Bump ´${bump}´ is unkown")
        }
        return result
    }
}
