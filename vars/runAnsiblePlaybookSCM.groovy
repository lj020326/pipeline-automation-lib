#!/usr/bin/env groovy

def call(Map params=[:]) {

    git credentialsId: 'bitbucket-ssh-lj020326', url: 'git@bitbucket.org:lj020326/ansible-datacenter.git'
    runAnsiblePlaybookSCM(params)

}