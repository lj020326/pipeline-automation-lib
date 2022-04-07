
// deployer.groovy - Jenkins pipeline script for deploying build packages to
//                   testing workflows
//
def main() {
    def siblings
    def projects_and_versions
    def jnparts = parse_job_name(env.JOB_NAME)
    def reponame = jnparts[0]
    def repotype = jnparts[1]
    def all_succeeded

    try {
        if(params.GERRIT_PROJECT) {
            currentBuild.displayName += " [${params.GERRIT_PROJECT}]"
        }
        stage('Sleep for siblings') {
            echo "Sleeping a while to let sibling jobs get invoked"
            sleep 5
        }
        stage('Build job search') {
            siblings = find_sibling_builds(/_build-artifacts-/)
            if(siblings.size() > 0) {
                echo "Found sibling builds:\n${buildsToStr(siblings)}"
                projects_and_versions = builds_projecs_and_versions(siblings)
                currentBuild.displayName =
                    "${currentBuild.id} ${pnv2str(projects_and_versions)}"
            } else {
                echo 'Did not find any sibling builds'
                currentBuild.result = 'NOT_BUILT'
            }
        }
        if(siblings.size() <= 0) {
            return
        }
        stage('Wait for builds to finish') {
            waitUntil {
                update_builds_status(siblings)
                all_builds_dequeued(siblings)
            }
            all_succeeded = !any_builds_removed_from_queue(siblings)
            if(!all_succeeded) {
                echo 'Some build jobs were removed from build queue, aborting'
                currentBuild.result = 'UNSTABLE'
                return
            }
            waitUntil { all_builds_done(siblings) }
            all_succeeded = all_builds_succeeded(siblings)
            if(!all_succeeded) {
                echo 'Some build jobs failed, aborting'
                currentBuild.result = 'UNSTABLE'
            }
        }
        if(!all_succeeded) {
            return
        }
        stage("Deploying to $reponame '$repotype' repo") {
            deploy_to(reponame, repotype, projects_and_versions)
        }
        stage("Triggering OST") {
            trigger_ost(reponame, repotype, get_versions(projects_and_versions))
        }
    } catch(Exception e) {
        email_notify('FAILURE')
        throw(e)
    }
}

@NonCPS
def buildsToStr(builds) {
    return builds.findResults({
        if('build_id' in it) {
            "- job: ${it.job_name} build: ${it.build_id} (${it.build_url})"
        } else {
            "- job: ${it.job_name} (queued id: ${it.queue_id})"
        }
    }).join("\n")
}

def find_sibling_builds(job_pattern = /.*/) {
    if(params.containsKey('GERRIT_NAME') && params.containsKey('GERRIT_EVENT_HASH')) {
        print('I was invoked by a gerrit event')
        return find_gerrit_sibling_builds(job_pattern)
    } else {
        print('Cannot recognise the event I was invoked by to find siblings')
        return []
    }
}

@NonCPS
def find_gerrit_sibling_builds(job_pattern = /.*/) {
    find_gerrit_sibling_queued_builds(job_pattern) +
    find_gerrit_sibling_schedualed_builds(job_pattern)
}

@NonCPS
def find_gerrit_sibling_queued_builds(job_pattern = /.*/) {
    return jenkins.model.Jenkins.instance.queue.items.toList().findResults {
        if(it.task == currentBuild.rawBuild.parent) {
            // skip builds of this job
            return
        }
        if(!(it.task.name =~ job_pattern)) {
            // skip jobs that do not match given pattern
            return
        }
        def build_params = it.getAction(hudson.model.ParametersAction)
        if(!build_params) {
            return
        }
        if(
            build_params.getParameter('GERRIT_NAME')?.value == params['GERRIT_NAME'] &&
            build_params.getParameter('GERRIT_EVENT_HASH')?.value == params['GERRIT_EVENT_HASH']
        ) {
            return [
                job_name: it.task.name,
                job_url: it.task.url,
                queue_id: it.id,
            ]
        }
    }
}

@NonCPS
def find_gerrit_sibling_schedualed_builds(job_pattern = /.*/) {
    return jenkins.model.Jenkins.instance.allItems(
        hudson.model.Job
    ).findResults { job ->
        if(job == currentBuild.rawBuild.parent) {
            // skip builds of this job
            return
        }
        if(!(job.name =~ job_pattern)) {
            // skip jobs that do not match given pattern
            return
        }
        // We assume sibling builds are scheduled between 10 hours before to 30
        // minutes after this build. It is necessary to limit the search
        // because searching all builds will be far too slow
        return job.builds.byTimestamp(
            currentBuild.timeInMillis - 10 * 60 * 60 * 1000,
            currentBuild.timeInMillis + 30 * 60 * 1000
        ).findResult { build ->
            def build_params = build.getAction(hudson.model.ParametersAction)
            if(!build_params) {
                return
            }
            if(
                build_params.getParameter('GERRIT_NAME')?.value == params['GERRIT_NAME'] &&
                build_params.getParameter('GERRIT_EVENT_HASH')?.value == params['GERRIT_EVENT_HASH']
            ) {
                return [
                    job_name: job.name,
                    job_url: job.url,
                    build_id: build.id,
                    build_url: build.url
                ]
            }
        }
    }
}

@NonCPS
def update_builds_status(builds) {
    builds.each {
        if(('build_id' in it) || !('queue_id' in it)) {
            return it
        }
        def job = Jenkins.instance.getItem(it.job_name)
        def build = job.builds.find { bld -> bld.queueId == it['queue_id'] }
        if(build == null) {
            if(Jenkins.instance.queue.getItem(it['queue_id']) != null) {
                return it
            }
        } else {
            it.putAll([
                build_id: build.id,
                build_url: build.url
            ])
            print "job: ${it.job_name} build: ${it.build_id} " +
                "(${it.build_url}) moved from queued to running"
        }
        it.remove('queue_id')
        return it
    }
}

@NonCPS
def all_builds_dequeued(builds) {
    return !builds.any {
        if('queue_id' in it) {
            print("${it.job_name} still queued")
            return true
        }
        return false
    }
}

@NonCPS
def any_builds_removed_from_queue(builds) {
    return builds.any {
        if((it.queue_id == null) && (it.build_id == null)) {
            print("${it.job_name} have been manually removed from queue")
            return true
        }
        return false
    }
}

@NonCPS
def all_builds_done(builds) {
    return !builds.any {
        if(Jenkins.instance.getItem(it.job_name).getBuild(it.build_id).isBuilding()) {
            print("${it.job_name} (${it.build_id}) still building")
            return true
        }
        return false
    }
}

@NonCPS
def all_builds_succeeded(builds) {
    return builds.every {
        def build = Jenkins.instance.getItem(it.job_name).getBuild(it.build_id)
        if(build.result.isBetterOrEqualTo(hudson.model.Result.SUCCESS)) {
            return true
        }
        print("${it.job_name} (${it.build_id}) failed building")
        return false
    }
}

@NonCPS
def builds_projecs_and_versions(builds) {
    builds.groupBy({ build_project_and_version(it)[0] }).collectEntries(
        { [it.key, it.value.groupBy({ build_project_and_version(it)[1] })] }
    )
}

@NonCPS
def build_project_and_version(build) {
    def match = (build.job_name =~ /(.*)_(.*)_build-artifacts.*/)
    if(match.asBoolean()) {
        return [match[0][1], match[0][2]]
    }
}

@NonCPS
def pnv2str(projects_and_versions) {
    projects_and_versions.collect({ project, versions ->
        "$project (${versions.collect({ it.key }).join(', ')})"
    }).join(', ')
}

@NonCPS
def parse_job_name(job_name) {
    (job_name =~ /^deploy-to-(.+)_(.+)$/)[0][1..2]
}

def deploy_to(reponame, repotype, projects_and_versions) {
    def repoman_confs = mk_repoman_confs(projects_and_versions)
    echo "Generated repoman configs:\n${'='*26}\n${repoman_confs_to_str(repoman_confs)}"
    save_repoman_confs(repoman_confs)
    run_repoman(reponame, repotype, repoman_confs)
}

@NonCPS
def mk_repoman_confs(projects_and_versions) {
    def build_uid = get_build_uid()
    projects_and_versions.collectMany { project, versions ->
        versions.collect { version, builds ->
            def conf_name = "${project}_${version}.repoman.conf"
            def jobs = builds.collect {
                "${env.JENKINS_URL}${it.build_url}"
            }
            def conf = ([
                "repo-extra-dir:${version}",
                "repo-extra-dir:${project}_${build_uid}",
            ] + jobs).join("\n")
            return [conf_name, conf]
        }
    }
}

@NonCPS
def get_build_uid() {
    [
        params.GERRIT_PATCHSET_REVISION?.take(8),
        currentBuild.id,
    ].findAll().join('_')
}

@NonCPS
def repoman_confs_to_str(repoman_confs) {
    repoman_confs.collectMany({ conf_name, conf ->
        [ conf_name + ':', '-' * (conf_name.size() + 1), conf, '' ]
    }).join('\n')
}

def save_repoman_confs(repoman_confs) {
    dir('exported-artifacts') {
        deleteDir()
        for(i = 0; i < repoman_confs.size(); ++i) {
            writeFile file:repoman_confs[i][0], text:repoman_confs[i][1]
        }
    }
    archiveArtifacts artifacts: 'exported-artifacts/**'
}

def run_repoman(reponame, repotype, repoman_confs) {
    sshagent(['c7ecbee7-5352-46da-a3c4-5aea24cd3de0']) {
        dir('exported-artifacts') {
            for(i = 0; i < repoman_confs.size(); ++i) {
                sh """
                    ssh \
                        -o StrictHostKeyChecking=no \
                        'deploy-${reponame}-${repotype}@resources.dettonville.int' \
                        < '${repoman_confs[i][0]}'
                """
            }
        }
    }
}

@NonCPS
def get_versions(projects_and_versions) {
    projects_and_versions.collectMany([] as Set) { it.value.keySet() }
}

def trigger_ost(reponame, repotype, versions) {
    print "Triggering OST versions: ${versions}"
    def missing_flows = []
    for(i = 0; i < versions.size(); ++i) {
        try {
            build(
                job: "test-repo_${reponame}_${repotype}_${versions[i]}",
                wait: false
            )
        } catch(Exception e) {
            missing_flows << versions[i]
        }
    }
    if(missing_flows.size() > 0) {
        setGerritReview unsuccessfulMessage: """
            Deploying some artifacts to 'experimental' FAILED.

            This project includes 'build-artifacts' jobs for MC versions for
            which an 'experimental' testing flow does not currently exist. This
            is either because support for that version of MC had ended or
            because an 'experimental' flow was not yet created.

            Specifically, artifacts built for the following MC versions were
            not deployed:
              ${missing_flows.join(', ')}

            Please remove 'build-artifacts' jobs for obsolete MC versions.
        """.stripIndent()
        error "Attempted to deploy to missing 'experimental' flows:" +
            missing_flows.join(', ')
    }
}

def email_notify(status, recipients='infra@dettonville.int') {
    emailext(
        subject: "[MC Jenkins] ${env.JOB_NAME}" +
            " - Build #${env.BUILD_NUMBER} - ${status}!",
        body: [
            "Build: ${env.BUILD_URL}",
            "Build Name: ${currentBuild.displayName}",
            "Build Status: ${status}",
            "Gerrit change: ${params.GERRIT_CHANGE_URL}",
            "- title: ${params.GERRIT_CHANGE_SUBJECT}",
            "- project: ${params.GERRIT_PROJECT}",
            "- branch: ${params.GERRIT_BRANCH}",
            "- author: ${params.GERRIT_CHANGE_OWNER_NAME}" +
            " <${params.GERRIT_CHANGE_OWNER_EMAIL}>",
        ].join("\n"),
        to: recipients,
        mimeType: 'text/plain'
    )
}

// We need to return 'this' so the actual pipeline job can invoke functions from
// this script
return this
