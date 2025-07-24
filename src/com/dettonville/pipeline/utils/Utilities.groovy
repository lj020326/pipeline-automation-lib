/*-
 * #%L
 * dcapi.dettonville.org
 * %%
 * Copyright (C) 2024 Dettonville DevOps
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.dettonville.pipeline.utils

import groovy.time.TimeCategory
import groovy.time.TimeDuration

import java.util.concurrent.TimeUnit

/**
 * Class for providing some generic utilities that can be used by any pipeline.
 */
class Utilities implements Serializable {
    private def jdkLinuxMap = [
            '1.7': 'Linux IBM JDK 1.7',
            '1.8': 'Linux IBM JDK 1.8'
    ]
    private def jdkWindowsMap = [
            '1.7': 'SUN JDK 1.7',
            '1.8':'SUN JDK 1.8',
            '1.7-ibm': 'IBM JDK 1.7',
    ]

    private def mavenLinuxMap = [
            '2.2.1': 'Linux Maven 2.2.1',
            '3.2.1': 'Linux Maven 3.2.1',
            '3.3.3': 'Linux Maven 3.3.3',
    ]

    private def mavenWindowsMap = [
            '2.2.1': 'Maven 2.2.1',
            '3.2.1': 'Maven 3.2.1',
            '3.3.3': 'Maven 3.3.3',
    ]

    def steps

    /**
     *
     * @param steps pipeline dsl context
     */
    Utilities(steps) {
        this.steps = steps

    }

    /**
     * Returns either the property of the value from the property container or the supplied default.
     * The propertiesContainer can be object received from readProperties dsl method.
     *
     * @param propertiesContainer object containing the properties
     * @param propertyKey key of the property you want to retrieve
     * @param defaultValue the default value in case the container doesn't contain the key
     * @return the property if it exists, or the defaultValue
     */
    String getPropertyOrDefault(propertiesContainer, String propertyKey, String defaultValue) {
        assert propertiesContainer: 'there is no propertiesContainer present'
        assert propertyKey: 'there is no propertyKey present'

        if (propertiesContainer[propertyKey]) {
            return propertiesContainer[propertyKey]
        }
        return defaultValue
    }

    /**
     * Returns whether or not this is a mainline branch.
     * In case of SVN, it is mainline if the branch is trunk.
     * In case of Git, it is mainline if the branch is master.
     * Else returns false.
     *
     * @param scmType scn type 'git', or 'svn'
     * @param branchName
     * @return
     */
    boolean isMainLineBranch(String scmType, String branchName) {
        if (scmType == 'git') {
            return 'main' == branchName
        }
        return 'trunk' == branchName
    }

    /**
     * Retrieve the JDK tool definition based upon desired version and platform.
     * For example, if you're on Linux and desiredVersion is 1.7, you get 'Linux IBM JDK 1.7'.
     *
     * @param desiredVersion the desiredVersion of the JDK (1.7, 1.8)
     * @return
     */
    void getJDKTool(String desiredVersion) {
        def jdk
        if (steps.isUnix()) {
            jdk = jdkLinuxMap.get(desiredVersion)
        } else {
            jdk = jdkWindowsMap.get(desiredVersion)
        }

        if (jdk) {
            def jdkTool = steps.tool name: jdk, type: 'jdk'
            return jdkTool
        } else {
            throw new IllegalArgumentException(String.format("No such jdk known: %s", desiredVersion))
        }
    }

    /**
     * Retrieve the Maven tool definition based upon desired version and platform.
     * For example, if you're on Linux and desiredVersion is 3.3.3, you get 'Linux Maven 3.3.3'.
     *
     * @param desiredVersion the desiredVersion of Maven (2.2.1, 3.2.1, 3.3.3)
     * @return the maven home for this version
     */
    String getMavenTool(String desiredVersion) {
        def maven
        if (steps.isUnix()) {
            maven = mavenLinuxMap.get(desiredVersion)
        } else {
            maven = mavenWindowsMap.get(desiredVersion)
        }

        if (maven) {
            def mavenTool = steps.tool name: maven, type: 'maven'
            return mavenTool
        } else {
            throw new IllegalArgumentException(String.format("No such maven known: %s", desiredVersion))
        }
    }

    /**
     * Uses the pipeline DSL to archive the given (Ant) file set in Jenkins.
     *
     * @param fileSet the Ant style list of files
     */
    void archiveFiles(String fileSet){
        assert fileSet: 'There is not fileSet present!'
        def fileSetArray = fileSet.split(';')

        /* for (String fileToArchive : fileSetArray){
         * have to use classic for loop, see: https://issues.jenkins-ci.org/browse/JENKINS-27421
         */
        for (int i =0; i <fileSetArray.size(); i++ ) {
            def fileToArchive = fileSetArray[i]
            println "fileToArchive=$fileToArchive"
            steps.archive fileToArchive
        }
        fileSetArray = null
    }

    /**
     * Checks if the value giving is among the split segments of the original string, split based on splitValue.
     *
     * @param stringToSplit the original string to split
     * @param splitValue the value with which to split the original string
     * @param valueToCheck the value to check for among the split segments
     * @return true if the splitted string contains a segment that is equal to the valueToCheck
     */
    @NonCPS
    boolean splittedStringContainsValue(String stringToSplit, String splitValue, String valueToCheck){
        assert stringToSplit: 'stringToSplit data is not present!'
        assert splitValue: 'splitValue data is not present!'
        assert valueToCheck: 'valueToCheck data is not present!'

        def list = ((String)stringToSplit).split(splitValue)
        for (int i =0; i <list.size(); i++ ) {
            if (list[i].equals(valueToCheck) ){
                return true
            }
        }
        return false
    }

    /**
     * Gets the base url for given url string.
     *
     * @param urlString the original URL string to split
     * @return base URL for the provided string
     *
     * ref: https://stackoverflow.com/questions/20098739/match-base-url-regex
     * ref; https://groovyconsole.appspot.com/script/1109001
     * ref: https://coderwall.com/p/utgplg/regex-full-url-base-url
     */
    @NonCPS
    String getBaseUrl(String urlString) {

        return urlString.find(/(^.+?[^\/:](?=[?\/]|$))\.*/) { it[1] };
    }

    /**
     * Gets the time duration for the provided start and end date.
     *
     * @param startDate
     * @param endDate
     * @return time duration for the provided start and end date
     *
     * ref: https://issues.jenkins-ci.org/browse/JENKINS-40154
     * ref;
     * ref: https://coderwall.com/p/utgplg/regex-full-url-base-url
     */
    @NonCPS
    public static TimeDuration getDuration(Date startDate, Date endDate) {
        return TimeCategory.minus(endDate, startDate)
    }

    @NonCPS
    public static String getDurationString(Date startDate, Date endDate) {
        return TimeCategory.minus(endDate, startDate).toString()
    }


    @NonCPS
    static String getDurationString(long milliseconds) {
//    return String.format("%d min, %d sec",
//            TimeUnit.millisecondsECONDS.toMinutes(milliseconds),
//            TimeUnit.millisecondsECONDS.toSeconds(milliseconds) - TimeUnit.MINUTES.toSeconds(TimeUnit.millisecondsECONDS.toMinutes(milliseconds)))
//    return String.format("%d min, %d sec",
//            TimeUnit.toMinutes(milliseconds),
//            TimeUnit.toSeconds(milliseconds) - TimeUnit.toSeconds(TimeUnit.toMinutes(milliseconds)))

        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long remainingSeconds = seconds % 60;
        long remainingMinutes = minutes % 60;
        String duration = "${remainingSeconds} seconds."
        if (minutes > 0) {
            duration = "${minutes} minutes and ${remainingSeconds} seconds."
        }
        if (hours > 0) {
            duration = "${hours} hours and ${remainingMinutes} minutes and ${remainingSeconds} seconds."
        }
        return duration
//    return String.format("%d minutes and %d seconds.", minutes, remainingSeconds);
    }

    /**
     * Get a diff between two dates
     * @param date1 the oldest date
     * @param date2 the newest date
     * @param timeUnit the unit in which you want the diff
     * @return the diff value, in the provided unit
     */
    @NonCPS
    public static long getDateDiff(Date date1, Date date2, TimeUnit timeUnit) {
        long diffInMillies = date2.getTime() - date1.getTime();
        return timeUnit.convert(diffInMillies,TimeUnit.MILLISECONDS);
    }

    @NonCPS
    public static Map<TimeUnit,Long> getDateDiff(Date date1, Date date2) {

        long diffInMillies = date2.getTime() - date1.getTime();

        //create the list
        List<TimeUnit> units = new ArrayList<TimeUnit>(EnumSet.allOf(TimeUnit.class));
        Collections.reverse(units);

        //create the result map of TimeUnit and difference
        Map<TimeUnit,Long> result = new LinkedHashMap<TimeUnit,Long>();
        long milliesRest = diffInMillies;

        for ( TimeUnit unit : units ) {

            //calculate difference in millisecond
            long diff = unit.convert(milliesRest,TimeUnit.MILLISECONDS);
            long diffInMilliesForUnit = unit.toMillis(diff);
            milliesRest = milliesRest - diffInMilliesForUnit;

            //put the result in the map
            result.put(unit,diff);
        }

        return result;
    }

    static List getRepoBranchList(def dsl, String repoUrl, String defaultBranch="develop") {

        def gettags = dsl.sh(script: "git ls-remote -t -h ${repoUrl}", returnStdout: true)

        Set branches = gettags.readLines().collect {
            it.split()[1].replaceAll('refs/heads/', '').replaceAll('refs/tags/', '').replaceAll("\\^\\{\\}", '')
        }

        List branchList = branches.collect()
        branchList = branchList.findAll { !it.contains(defaultBranch) }

        branchList = [defaultBranch] + branchList

        return branchList

    }

    // ref: https://stackoverflow.com/questions/4052840/most-efficient-way-to-make-the-first-character-of-a-string-lower-case
    static String decapitalize(String string) {
        if (string == null || string.length() == 0) {
            return string;
        }
        return string.substring(0, 1).toLowerCase() + string.substring(1);
    }

    static String capitalize(String string) {
        if (string == null || string.length() == 0) {
            return string;
        }
        return string.substring(0, 1).toUpperCase() + string.substring(1);
    }

}