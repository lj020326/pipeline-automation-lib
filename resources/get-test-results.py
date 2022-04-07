#!/usr/bin/env python

# get test result from jenkins job - jenkins http python API
# note: script can be called only after junit test results are loaded into job at end of test
##
## ref: https://wiki.jenkins.io/display/JENKINS/Remote+access+API
##

## https://cd.dettonville.int/jenkins/job/DCAPI/job/dcapi-test/job/master/lastCompletedBuild/api/python
## https://cd.dettonville.int/jenkins/job/DCAPI/job/dcapi-test/job/master/291/api/python

import urllib2
import base64
import os

# URL of job
#url = os.environ['BUILD_URL']
#jobname = os.environ['JOB_NAME']
#url="%s/testReport/api/python" % (url)
# baseurl="http://jenkinsserver:port"
# jobname="jobname"

# baseurl="https://cd.dettonville.int"
baseurl="https://cd.dettonville.int/jenkins/job/DCAPI/job/dcapi-test"
#jobname = os.environ['JOB_NAME']
jobname="DCAPI"
# buildnum = os.environ['BUILD_NUM']
# buildnum="lastCompletedBuild"
buildnum="291"

# url="%s/job/%s/lastCompletedBuild/testReport/api/python" % (baseurl,jobname)
url="%s/job/%s/lastCompletedBuild/testReport/api/python" % (baseurl,jobname, buildnum)
print "url: %s" % url

# username="..."
# password="..."
username = os.environ['JENKINS_USER']
password= os.environ['JENKINS_PWD']
base64string = base64.encodestring('%s:%s' % (username, password)).replace('\n', '')
request = urllib2.Request(url)
request.add_header("Authorization", "Basic %s" % base64string)
result = urllib2.urlopen(request)
# job dict
j=eval(result.read())

# keys of job:
# failCount suites skipCount empty duration passCount _class testActions
print "TOTAL test count: pass:%d fail:%d skip:%d" % (j['passCount'],j['failCount'],j['skipCount'])

oldClassName=""
suites=j['suites']
for s in suites:
    for c in s['cases']:
        if c['className'] != oldClassName:
            print "TEST CLASS: %s" % c['className']
            oldClassName = c['className']
        skipped = ""
        if c['skipped']:
            skipped = " SKIP(%s)" % c['skippedMessage']
        print "     TEST RESULT: %s%s NAME: %s" % ( c['status'], skipped, c['name'] )
        if c['errorDetails']:
            print "      ERR: %s" % c['errorDetails']
        #if c['errorStackTrace']:
        #    from pprint import pprint
        #    pprint(c['errorStackTrace'],indent=8)

#keys e.g. of test case:
#          "testActions" : [],
#          "age" : 0,
#          "className" : "testfile.testclass",
#          "duration" : 31.570633,
#          "errorDetails" : None,
#          "errorStackTrace" : None,
#          "failedSince" : 0,
#          "name" : "test_001",
#          "skipped" : False,
#          "skippedMessage" : None,
#          "status" : "PASSED",
#          "stderr" : "stuff",
#          "stdout" : "loads of stuff"