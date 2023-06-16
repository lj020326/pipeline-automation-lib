#!groovy

def registryData = null

def runPipeline(project, cookbook, checkoutClosure, cookbookDir='', dryRun=false) {

    def uploadedToEnv = []
    def userNotificationEmail = null
    def version = null
    def versionIntegrated
    String maxPromotionEnv

    if (dryRun) {
        figlet 'Dry Run Enabled!'
        echo 'Dry run enabled! This cookbook will be verified and built as normal, but the artifact will not be published.'
    }

    validateParameters(project, cookbook, checkoutClosure)
    environment = "DEV"

    stage 'Generate Artifact'
    checkpoint 'Generate Artifact'
    figlet 'Generate Artifact'
    node(nodeTag(maxPromotionEnv)) {
        if (isCookbookRegistered(cookbook)) {
            echo "Cookbook registration check passed"
            registryData = getCookbookRegistry(cookbook)
            userNotificationEmail = getMaintainerEmail(registryData)
            maxPromotionEnv = getMaxPromotionEnv(registryData)
        } else {
            error "Your cookbook is not registered in Cookbook Registry. Please register your cookbook before attempting integration."
        }
        if (!Arrays.asList('DEV', 'STAGE', 'PROD').contains(maxPromotionEnv)) {
            error "Cookbook environment check failed. Please correct 'max_promotion_env' value of your cookbook entry in Cookbook Registry"
        }
        stageGenerateArtifact(project, cookbook, maxPromotionEnv, checkoutClosure, cookbookDir, registryData)
    }

    stage 'Validate'
    checkpoint 'Validate'
    stageValidate(project, cookbook)

    stage 'Integration Test'
    stageIntegrationTest(project, cookbook)

    stage 'Promote Artifact To Dev'
    environment = "DEV"
    node(nodeTag(environment)) {
        restoreCookbookStash(cookbook)
        version = getVersion()
        versionIntegrated = isVersionIntegrated(cookbook, version, environment)
    }

    if (dryRun) {
        echo 'Dry run is enabled. Skipping cookbook artifact upload.'
    } else {
        if (!versionIntegrated) {
            figlet 'Promote Artifact To Dev'
            stageUploadToRepo(project, cookbook, environment)
            uploadedToEnv << environment
        }
    }

    if (Arrays.asList('STAGE', 'PROD').contains(maxPromotionEnv)) {
        environment = "STAGE"

        stage 'Compliance Test'
        stageComplianceTest(project, cookbook)

        node(nodeTag(environment)) {
            restoreCookbookStash(cookbook)
            version = getVersion()
            versionIntegrated = isVersionIntegrated(cookbook, version, environment)
        }

        if (dryRun) {
            echo 'Dry run is enabled. Skipping Cookbook Promotion.'
        } else {

            if (!versionIntegrated) {
                stage 'Promote Artifact To Stage'
                figlet 'Promote Artifact To Stage'
                stageUploadToRepo(project, cookbook, environment)
                uploadedToEnv << environment
            }
        }
    }
    if (maxPromotionEnv == 'PROD') {
        environment = "PROD"

        node(nodeTag(environment)) {
            restoreCookbookStash(cookbook)
            version = getVersion()
            versionIntegrated = isVersionIntegrated(cookbook, version, environment)
        }

        if (dryRun) {
            echo 'Dry run is enabled. Skipping Cookbook Promotion.'
        } else {

            if (!versionIntegrated) {
                def skipOpsReview = getOpsReviewFlag(registryData)
                if (!skipOpsReview) {
                    stage 'Operations Compliance Review'
                    checkpoint 'Operations Compliance Review'
                    stageOperationsComplianceReview(cookbook, project, version, environment, maxPromotionEnv, userNotificationEmail)
                }

                stage 'Promote Artifact To Production'
                figlet 'Promote Artifact'
                figlet 'To Production'
                stageUploadToRepo(project, cookbook, environment)
                uploadedToEnv << environment
            }
        }
    }
    return [cookbook: cookbook, version: version, env: uploadedToEnv, userNotificationEmail: userNotificationEmail, versionIntegrated: versionIntegrated]
}

/* Pipeline Stages */

def stageGenerateArtifact(project, cookbook, maxPromotionEnv, checkoutClosure, cookbookDir, registryData) {
    cleanup()
    if (maxPromotionEnv != 'PROD' && gitRepoBranch?.trim()) {
        gitBranch = gitRepoBranch?.trim()
    } else if (maxPromotionEnv == 'PROD' && gitRepoBranch?.trim() && gitRepoBranch?.trim().matches("(?i)release/.*")) {
        gitBranch = gitRepoBranch?.trim()
    } else {
        gitBranch = 'main'
    }
    checkoutClosure.delegate = [branch: gitBranch]
    checkoutClosure()
    checkCookbookOrigin(project, cookbook)
    validateCookbookSourceURL(project, cookbook, registryData)
    dir (cookbookDir) {
        if (project == "extcook") {
            modifyExtBerksFile()
        }

        generateArtifact(cookbook, maxPromotionEnv)
        generateInspecProfileBundle()
        createCookbookStash(cookbook)
    }
}

def stageValidate(project, cookbook) {
    if (project != "extcook") {
        figlet 'Validate'
        // Temporarily use node labeled `CHEF && DOCKER` for stageValidate()
        // so that unit tests requiring the `restclient` library are
        // executed successfully.
        node('CHEF && DOCKER') {
            restoreCookbookStash(cookbook)
            setHomePath(pwd())
            lintCheck()
            syntaxCheck(cookbook)
            unitTest()
        }
    } else {
        echo 'Skipping validation for external cookbook.'
    }
}

def stageIntegrationTest(project, cookbook) {
    if (project != "extcook") {
        figlet 'Integration Test'
        integrationTest(cookbook)
    } else {
        echo 'Skipping integration tests for external cookbooks.'
    }
}

def stageUploadToRepo(project, cookbook, environment) {
    checkpoint "Promote Artifact To ${environment} CHEF"
    node(nodeTag(environment)) {
        try {
            restoreCookbookStash(cookbook)
            configureKnifeClient(environment, "CHEF")
            uploadToChefServer(cookbook, environment)
        } catch (error) {
            retry(2) {
                sleep 2
                uploadToChefServer(cookbook, environment)
            }
        } finally {
            removeKnifeClient()
        }
    }

    checkpoint "Promote Artifact To ${environment} CHEF SUPERMARKET"
    node(nodeTag(environment)) {
        // This if block is temporary and can be removed when Supermarket server will be available in Stage Environment.
        if ( env."${environment}_SUPERMARKET_URL" != null) {
            try {
                restoreCookbookStash(cookbook)
                configureKnifeClient(environment, "SUPERMARKET")
                uploadToSupermarket(cookbook, environment)
            } catch (error) {
                retry(2) {
                    sleep 2
                    uploadToSupermarket(cookbook, environment)
                }
            } finally {
                removeKnifeClient()
            }
        }
    }

    checkpoint "Promote Artifact To ${environment} COOKBOOK ARCHIVE REPOSITORY"
    node(nodeTag(environment)) {
        try {
            restoreCookbookStash(cookbook)
            uploadToCookbookArchiveRepo(project, cookbook, environment)
        } catch (error) {
            retry(2) {
                sleep 2
                uploadToCookbookArchiveRepo(project, cookbook, environment)
            }
        }
    }
}

def stageComplianceTest(project, cookbook) {
    if (project != "extcook") {
        figlet 'Compliance Test'
        checkCompliance(cookbook)
    } else {
        echo 'Skipping automated compliance tests for external cookbooks.'
    }
}

def stageOperationsComplianceReview(cookbook, project, version, environment, maxPromotionEnv, userNotificationEmail) {
    figlet 'Operations Compliance'
    figlet 'Review'
    def infoForReview = getInformationForReview(cookbook, project, version, environment, maxPromotionEnv, 'OPERATIONS')
    manualComplianceReview(cookbook, "Operations", infoForReview['reviewerGroup'], infoForReview['reviewerEmail'], userNotificationEmail, environment, infoForReview['reviewContent'])
}

// Helper methods

def nodeTag(nodeEnv) {
    if (nodeEnv == "PROD") {
        'chef-cookbook-integration && PROD'
    } else {
        'chef-cookbook-integration && DTL'
    }
}

def getVersion() {
    sh "ruby -e \"require 'chef/cookbook/metadata'; metadata = Chef::Cookbook::Metadata.new; metadata.from_file('metadata.rb'); File.write('.getVersion', metadata.version)\""
    def commandOutput = readFile('.getVersion').trim()
    sh "rm -f .getVersion"
    return commandOutput.split('\\s+')[-1]
}


def modifyExtBerksFile() {
    unstash "Static Files"
    sh '''
  if [ ! -f Berksfile ]; then
    cp pipeline_files/Berksfile .
  else
    sed -e '/source/ s/^#*/#/' -i Berksfile
    echo "\nsource ENV['BERKSAPI_URL']" >> Berksfile
  fi
  '''
}

//Temporary fix to make Chef Server as the dependency Manager when Supermaket service is not available.
//We can remove this method once Supermaket service is available in Stage Environment.
def modifyBerksFile(target_env) {
    if (target_env == 'STAGE') {
        sh '''
      sed -e '/abort/ s/^#*/#/' -i Berksfile
      sed -e '/BERKSAPI_URL/ s/^#*/#/' -i Berksfile
      echo "\nsource :chef_server" >> Berksfile
    '''
    }
}

def configureKnifeClient(chefEnv, service) {
    echo "Configuring knife for ${chefEnv} ${service} Environment"
    env.CHEF_SERVER_URL = env."${chefEnv}_CHEF_SERVER_URL"
    env.SUPERMARKET_URL = env."${chefEnv}_SUPERMARKET_URL"
    sh "if [ ! -d .chef ]; then mkdir .chef; fi"
    unstash "Static Files"
    sh "mv pipeline_files/knife.rb .chef"
    withCredentials([[$class: 'FileBinding', credentialsId: env."${chefEnv}_${service}_KEY_ID", variable: 'SECRET']]) {
        sh ''' cp ${SECRET} .chef/clientkey.pem'''
    }

    if (service == "CHEF") {
        sh """knife ssl fetch"""
    } else {
        sh "knife ssl fetch $SUPERMARKET_URL"
    }
}

def removeKnifeClient() {
    sh 'rm -rf .chef'
}

def getMaxPromotionEnv(registryData) {
    return registryData['max_promotion_env'].toString().replaceAll("\\W", "").toUpperCase()
}

def getOpsReviewFlag(registryData) {
    return registryData['skip_ops_review'].toString().replaceAll('\\W', '').toBoolean()
}

def getMaintainerEmail(registryData) {
    return registryData['maintainers'].toString().replaceAll('\\[', '').replaceAll('\\]', '')
}

def getInformationForReview(cookbook, project, version, environment, maxPromotionEnv, reviewerTeam) {
    def integratedVersion
    def reviewerGroup
    def reviewerEmail
    def reviewContent
    node(nodeTag(maxPromotionEnv)) {
        integratedVersion = getIntegratedVersion(cookbook, maxPromotionEnv)
        reviewContent = getCookbookIncrementalChanges(cookbook, project, version, environment, maxPromotionEnv, integratedVersion)
        reviewerGroup = env."${reviewerTeam}_REVIEWER_GROUP"
        reviewerEmail = env."${reviewerTeam}_NOTIFICATION_EMAIL"
    }
    return ['reviewContent':reviewContent, 'reviewerGroup':reviewerGroup, 'reviewerEmail':reviewerEmail]
}

def getCookbookRegistry(cookbook) {
    unstash "Cookbook Registry"
    def cookbookRegistry = readYaml(file: "cookbooks.yaml")
    return cookbookRegistry[cookbook]
}

def isCookbookRegistered(cookbook) {
    def registryData = getCookbookRegistry(cookbook)
    if (registryData.grep()?.empty) {
        return false
    } else {
        return true
    }
}

def validateCookbookSourceURL(project, cookbook, registryData) {
    String registeredUrl = registryData['source_url']
    String registeredProject = getProject(registeredUrl)
    if (project == registeredProject) {
        echo "Cookbook source url validated"
    } else {
        error "Your cookbook source url does not match the registered source url in Cookbook Registry."
    }
}

def checkCookbookOrigin(project, cookbook) {
    if (cookbook =~ /(mc_.*)$/ || project == 'extcook') {
        echo 'Cookbook naming convention check passed'
    } else {
        error "Cookbook naming convention check failed - If this is an internal Dettonville cookbook then check that it's name matches the internal cookbook naming standards; if this is a third party cookbook it must be located in the appropriate GIT project."
    }
}

def setHomePath(path) {
    sh "if [ ! -d ${path}/.ssh ]; then mkdir ${path}/.ssh; fi"
    env.HOME = path
}

def berksInstall(target_env) {
    sh "if [ ! -d .berkshelf ]; then mkdir .berkshelf; fi"
    // This is the fixes for SSL issue for berks
    writeFile file: '.berkshelf/config.json', text: '''{
    "ssl": {
      "verify": false
    },
    "ssl.verify": false
  }'''
    // This if block is temporary and can be removed when Supermarket server will be available in Stage Environment.
    if (env."${target_env}_SUPERMARKET_URL" == null) {
        echo "Resolving cookbook dependency from ${target_env} Chef Server"
        configureKnifeClient(target_env, "CHEF")
        env.CHEF_SERVER_URL = env."${target_env}_CHEF_SERVER_URL"
        env.CHEF_ORG = env."${target_env}_CHEF_ORG"
        env.CHEF_NODE_NAME = env."${target_env}_CHEF_NODE_NAME"
        modifyBerksFile(target_env)
        sh 'berks install'
        removeKnifeClient()
    } else {
        // TODO - should this fail if the version could not be determined?
        echo "Resolving cookbook dependency from ${target_env} Supermarket Server"
        env.BERKSAPI_URL = env."${target_env}_SUPERMARKET_URL"
        modifyBerksFile(target_env)
        sh 'berks install'
    }
}

def generateArtifact(cookbook, maxPromotionEnv) {
    def cookbook_version = getVersion()
    if (cookbook_version) {
        echo "Building version ${cookbook_version}"
    }
    setHomePath(pwd())
    // Stash the original Berksfile and put a clean/ generic Berksfile, which will help us to create the cookbook archive without test dependencies.
    stash name: "Cookbook Berksfile", includes: "Berksfile"
    sh "rm -f Berksfile"
    unstash "Static Files"
    sh "cp pipeline_files/Berksfile ."
    berksInstall(maxPromotionEnv)
    sh 'berks package'
    /* FIX for bug in berkshelf where the package command includes '.' in the
    * resulting tarball and its permissions are too restrictive, i.e., 0700.
    * see, https://github.com/berkshelf/berkshelf/issues/1483
    */
    sh 'tar -xzf cookbooks*.tar.gz --no-overwrite-dir'
    sh 'rm -rf *.tar.gz'
    sh 'find . -name Berksfile.lock | xargs chmod +r'
    sh 'find cookbooks/ \\( -name ".ssh" -o -name ".berkshelf" -o -name "pipeline_files" -o -name ".chef" \\) | xargs rm -rf'
    createCookbookArchive(cookbook)
    // Unstash the original berksfile and do a berks install so that, we can have all the test dependencies which is required for integration test.
    sh "rm -f Berksfile"
    unstash "Cookbook Berksfile"
    berksInstall(maxPromotionEnv)
    createIntegrationTestArchives(cookbook)
}

def generateInspecProfileBundle() {
    sh 'mkdir -p inspec_profiles'
    profile = env.INSPEC_PROFILES
    gitDir = profile.substring(profile.lastIndexOf('/')+1, profile.length())
    checkout changelog: false, poll: false, scm:([$class: 'GitSCM',
                                                  branches: [[name: '*/master']],
                                                  doGenerateSubmoduleConfigurations: false,
                                                  extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: "inspec_profiles/${gitDir}/"]],
                                                  submoduleCfg: [],
                                                  userRemoteConfigs: [[url: profile]]])
}

def lintCheck() {
    echo('Running lint check')
    // Ignore : Foodcritic rule FC064 (Ensures issues_url is set in metadata)
    // Excluding Deprecated rules FC003, FC020, FC023, FC035 ( Temporary fix until latest version of foodcritic gem is available in Jenkins agents)
    sh 'foodcritic -t ~FC003 -t ~FC020 -t ~FC023 -t ~FC035 -t ~FC064 -f any .'
}

def syntaxCheck(cookbook) {
    echo 'Running syntax check'
    sh "for f in `find cookbooks/${cookbook} -name '*.rb'`; do  ruby -c \${f}; if [ \$? -ne 0 ]; then export SIGNAL=1; fi; done"
}

def unitTest() {
    echo 'Running unit tests'
    sh 'rspec'
}

def kitchenTest(option, suite) {
    withCredentials([[$class: 'UsernamePasswordMultiBinding',
                      credentialsId: 'vcloud-integration-test-user',
                      passwordVariable: 'VCLOUD_PASSWORD',
                      usernameVariable: 'VCLOUD_USERNAME']]) {
        if (registryData['inspec_profiles']['skip_compilers_1']) {
            echo "Skipping Compilers Spec"
            sh "SKIP_COMPILERS_1=true kitchen ${option} ${suite}"
        } else {
            sh "kitchen ${option} ${suite}"
        }
    }
}

def updateDockerImages() {
    images = getKitchenImages()
    for (item in images) {
        echo "Pulling base docker image ${item} prior to Test Kitchen"
        sh "docker pull ${item}"
    }
}

def executeTestSuite(actions, suite) {
    try {
        echo "\nRunning Kitchen suite ${suite}\n"
        for (j = 0; j < actions.size(); j++) {
            action = actions.get(j)
            kitchenTest(action,suite)
        }
    } finally {
        kitchenTest("destroy",suite)
    }
}

def integrationTest(cookbook) {
    def suites

    node('CHEF && DOCKER') {
        restoreCookbookStash(cookbook)
        suites = getKitchenSuites()
        echo "Running ${suites.size()} Kitchen suites."
    }

    for (i = 0; i < suites.size(); i++) { // avoid loop closures in CPS
        checkpoint "Integration Test Suite - ${suites.get(i)}"

        node('CHEF && DOCKER') {
            restoreCookbookStash(cookbook)
            setHomePath(pwd())
            updateDockerImages()
            writeLocalKitchenFile()
            executeTestSuite(["verify","converge","verify"], suites.get(i))
        }
    }
}

def checkCompliance(cookbook) {
    def suites

    node('CHEF && DOCKER && COMPLIANCE') {
        restoreCookbookStash(cookbook)
        writeInspecVerifierToLocalKitchenFile()
        regenerateLocalKitchenFile()
        suites = getKitchenSuites()
        echo "Running ${suites.size()} Compliance Validation."
    }

    for (i = 0; i < suites.size(); i++) { // avoid loop closures in CPS
        checkpoint "Compliance Test Suite - ${suites.get(i)}"

        node('CHEF && DOCKER && COMPLIANCE') {
            restoreCookbookStash(cookbook)
            setHomePath(pwd())
            writeInspecVerifierToLocalKitchenFile()
            regenerateLocalKitchenFile()
            executeTestSuite(["verify"], suites.get(i))
        }
    }
}

def writeLocalKitchenFile() {
    unstash "Static Files"
    def source = readFile 'pipeline_files/integration.kitchen.local.yml'
    writeFile file: '.kitchen.local.yml', text: source
}

def writeInspecVerifierToLocalKitchenFile() {
    unstash "Static Files"
    def source = readFile 'pipeline_files/compliance.kitchen.local.yml'
    writeFile file: '.kitchen.local.yml', text: source
    profile = env.INSPEC_PROFILES //Get inspec profile URL
    gitDir = profile.substring(profile.lastIndexOf('/')+1, profile.length()) // Retrieve inspec repo name from URL
    sh "echo '    - inspec_profiles/${gitDir}/' >> .kitchen.local.yml" // Add inspec profile location to .kitchen.local.yml. Refer generateInspecProfileBundle()
}

def getKitchenImages() {
    sh '''cat .kitchen.yml | grep -e ${DEV_DOCKER_REGISTRY} | awk '{print $2}' | tr -d '"' > .kimages.txt'''
    readFile('.kimages.txt').readLines()
}

def regenerateLocalKitchenFile() {
    unstash "Helpers"
    sh "ruby -e \"load 'modules/test_kitchen_helper.rb'; TestKitchenHelper.regenerate_local_kitchen_file('.kitchen.yml','.kitchen.local.yml')\""
}

def getReviewInput(reviewerTeam, reviewerGroup, environment) {
    input id: 'Proceed',
            message: "Review by ${reviewerTeam} for promotion of cookbook to ${environment} Environment",
            ok: 'OK',
            parameters: [
                    [$class: 'ChoiceParameterDefinition', choices: "Approve\nReject", description: '', name: "Decision"],
                    [$class: 'TextParameterDefinition', defaultValue: '', description: "Review Guidelines: https://fusion.dettonville.int/confluence/x/OychDQ", name: "Comments"] ],
            submitterParameter: 'submitter',
            submitter: reviewerGroup
}

def waitForReview(cookbook, reviewerTeam, reviewerGroup, reviewerEmail, userNotificationEmail, environment, reviewContent, timeOutInDays, reminder) {
    def didTimeout = false
    def reviewResult = null
    String emailContent = "Cookbook [${cookbook}] is awaiting a manual review from the ${reviewerTeam} team as a part of the Cookbook Integration Pipeline. The review process is documented on the Integration Pipeline - Manual Review < https://fusion.dettonville.int/confluence/x/FXQhDQ> wiki page.\n\nPlease see below for information on the Cookbook being integrated and proceed with the review.\n${reviewContent}\n\nWhen you have conducted the review, please use the link below to Approve or Reject the cookbook promotion to ${environment} environment:\n\n${env.BUILD_URL}/input/\n"

    sendEmail(reviewerEmail,
            "${reminder}${reviewerTeam} Manual Review Pending for Cookbook [${cookbook}]",
            emailContent)

    try {
        timeout(time: timeOutInDays, unit: 'DAYS') {
            reviewResult = getReviewInput(reviewerTeam, reviewerGroup, environment)
        }
    } catch(err) {
        def user = err.getCauses()[0].getUser()
        if('SYSTEM' == user.toString()) { // SYSTEM means timeout.
            didTimeout = true
        } else {
            echo "Aborted by: [${user}]"
        }
    } finally {
        return ['didTimeout':didTimeout, 'reviewResult':reviewResult]
    }
}

def postReviewNotifications(cookbook, reviewerTeam, reviewerEmail, didTimeout, reviewResult, userNotificationEmail, environment) {
    if (didTimeout) {
        sendEmail(reviewerEmail,
                "Timeout : ${reviewerTeam} Manual Review Pending for Cookbook [${cookbook}]",
                "Cookbook [${cookbook}] was awaiting a manual review from the ${reviewerTeam} team as a part of the Cookbook Integration Pipeline. The manual review was not completed within the 5 day window. Please visit - Integration Pipeline - Manual Review <https://fusion.dettonville.int/confluence/x/FXQhDQ> wiki page for information on what the next steps are.")

        error "\n\nNo review was conducted by ${reviewerTeam} team before timeout. Please follow up with respective team for your cookbook's review"
    } else if (reviewResult != null) {
        if (reviewResult['Decision']== "Approve") {
            echo "Your cookbook is approved for promotion to ${environment} environment"
            echo "Review Comments: ${reviewResult['Comments']}"
            sendEmail(userNotificationEmail,
                    "${reviewerTeam} Review Results for Cookbook [${cookbook}]",
                    "Cookbook [${cookbook}] has been approved for promotion to the ${environment} Cookbook Repositories.\n\nReviewer: ${reviewResult['submitter']}\n\nReview Comments:\n${reviewResult['Comments']}\n\nPipeline Job: ${env.BUILD_URL}/consoleFull.")
        } else if (reviewResult['Decision']== "Reject") {
            echo "Promotion of Cookbook [${cookbook}] to the ${environment} environment has been rejected."
            echo "Review Comments:\n${reviewResult['Comments']}"
            sendEmail(userNotificationEmail,
                    "${reviewerTeam} Review Results for Cookbook [${cookbook}]",
                    "Promotion of Cookbook [${cookbook}] to the ${environment} environment has been rejected.\n\nReviewer: ${reviewResult['submitter']}\n\nReview Comments:\n${reviewResult['Comments']}\n\nPipeline Job: ${env.BUILD_URL}/consoleFull.")
            error "Rejected"
        }
    } else {
        error "Build terminated"
    }
}

def manualComplianceReview(cookbook, reviewerTeam, reviewerGroup, reviewerEmail, userNotificationEmail, environment, reviewContent) {
    def result = null
    try {
        // Initial 3 days for review
        result = waitForReview(cookbook, reviewerTeam, reviewerGroup, reviewerEmail, userNotificationEmail, environment, reviewContent, 3, '')
        // Reminder for review
        if (result['didTimeout'] == true) {
            result = waitForReview(cookbook, reviewerTeam, reviewerGroup, reviewerEmail, userNotificationEmail, environment, reviewContent, 2, "Reminder : ")
        }
    } finally {
        postReviewNotifications(cookbook, reviewerTeam, reviewerEmail, result['didTimeout'], result['reviewResult'], userNotificationEmail, environment)
    }
}

def getKitchenSuites() {
    sh '''kitchen list | awk '{if (NR!=1) {print $1}}' > .ksuites.txt'''
    readFile('.ksuites.txt').readLines()
}

def createCookbookArchive(cookbook) {
    // Create the cookbook archive which will be uploaed to the cookbook archive repositories in latter stages.
    sh "tar -czf ${cookbook}-${version}.tar.gz cookbooks/ --remove-files"
    captureChecksum("${cookbook}-${version}.tar.gz")
}

def createIntegrationTestArchives(cookbook) {
    // Create an archive of the standalone cookbook so we can easily capture a single checksum
    sh "tar --exclude ${cookbook}.tar.gz --exclude pipeline_files --exclude inspec_profiles --exclude .git --exclude .berkshelf -cf ${cookbook}-standalone.tar.gz ."
    // Create an archive of .berkshelf folder to use in latter stages and not to use berks install command
    sh "tar -czf ${cookbook}-berkshelf.tar.gz .berkshelf/ --remove-files"
    // Capture checksums of cookbook content archives
    captureChecksum("${cookbook}-standalone.tar.gz")
    captureChecksum("${cookbook}-berkshelf.tar.gz")
}

def createCookbookStash(cookbook) {
    stash name: "${cookbook} cookbook standalone", includes: "${cookbook}-standalone.tar.gz"
    stash name: "${cookbook} inspec profiles", includes:'inspec_profiles/'
    stash name: "${cookbook} cookbook archive", includes: "${cookbook}-${version}.tar.gz"
    stash name: "${cookbook} berkshelf archive", includes: "${cookbook}-berkshelf.tar.gz"
}

def restoreCookbookStash(cookbook) {
    cleanup()
    unstash "${cookbook} cookbook standalone"
    validateChecksum("${cookbook}-standalone.tar.gz")
    sh "tar -xf ${cookbook}-standalone.tar.gz"
    unstash "${cookbook} inspec profiles"
    unstash "${cookbook} cookbook archive"
    validateChecksum("${cookbook}-${version}.tar.gz")
    sh "tar -xf ${cookbook}-${version}.tar.gz"
    unstash "${cookbook} berkshelf archive"
    validateChecksum("${cookbook}-berkshelf.tar.gz")
    sh "tar -xf ${cookbook}-berkshelf.tar.gz"
}

def uploadToSupermarket(cookbook, chefEnv) {
    env.SUPERMARKET_URL = env."${chefEnv}_SUPERMARKET_URL"
    echo "Uploading ${cookbook} to Supermarket"
    sh "cp metadata.rb cookbooks/${cookbook}"
    sh "if [ -f cookbooks/${cookbook}/metadata.json ]; then rm  cookbooks/${cookbook}/metadata.json; fi"
    sh "knife supermarket share ${cookbook} -o ./cookbooks -V"
}

def uploadToChefServer(cookbook, chefEnv) {
    echo "Uploading ${cookbook} to Chef server"
    env.CHEF_SERVER_URL = env."${chefEnv}_CHEF_SERVER_URL"
    env.CHEF_NODE_NAME = env."${chefEnv}_CHEF_NODE_NAME"
    env.CHEF_ORG = env."${chefEnv}_CHEF_ORG"
    sh "knife cookbook upload ${cookbook} --force --cookbook-path cookbooks/ -V"
}

def uploadToCookbookArchiveRepo(project, cookbook, environment) {
    echo "upload ${cookbook} package to Artifactory"
    def server = Artifactory.newServer url: env.ARTIFACTORY_URL, credentialsId: "${environment}_ARTIFACTORY_CRED_ID"
    groupId = getArtifactGroupId(project)
    repoId = env."${environment}_ARTIFACT_REPO_ID"
    def uploadSpec = """{
    "files": [
      {
        "pattern": "${cookbook}-${version}.tar.gz",
        "target": "${repoId}/${groupId}/${cookbook}/${version}/",
        "recursive": "false"
      }
    ]
  }"""
    server.upload spec: uploadSpec
}

def getIntegratedVersion(cookbook, chefEnv) {
    env.SUPERMARKET_URL = env."${chefEnv}_SUPERMARKET_URL"
    env.CHEF_SERVER_URL = env."${chefEnv}_CHEF_SERVER_URL"
    env.CHEF_NODE_NAME = env."${chefEnv}_CHEF_NODE_NAME"
    env.CHEF_ORG = env."${chefEnv}_CHEF_ORG"
    // This if block is temporary and can be removed when Supermarket server will be available in Stage Environment.
    if (env."${chefEnv}_SUPERMARKET_URL" != null) {
        configureKnifeClient(chefEnv, "SUPERMARKET")
        sh "knife supermarket show ${cookbook} | grep latest_version | rev | cut -d'/' -f1 | rev > .getVersionIntegrated"
    } else {
        configureKnifeClient(chefEnv, "CHEF")
        sh "knife cookbook show ${cookbook} | cut -d' ' -f4 > .getVersionIntegrated"
    }
    def commandOutput = readFile('.getVersionIntegrated').trim()
    sh "rm -f .getVersionIntegrated"
    removeKnifeClient()
    return commandOutput.split('\\s+')[-1]
}

def isVersionIntegrated(cookbook, version, chefEnv) {
    env.SUPERMARKET_URL = env."${chefEnv}_SUPERMARKET_URL"
    env.CHEF_SERVER_URL = env."${chefEnv}_CHEF_SERVER_URL"
    env.CHEF_NODE_NAME = env."${chefEnv}_CHEF_NODE_NAME"
    env.CHEF_ORG = env."${chefEnv}_CHEF_ORG"
    // This if block is temporary and can be removed when Supermarket server will be available in Stage Environment.
    if (env.SUPERMARKET_URL != "null") {
        configureKnifeClient(environment, "SUPERMARKET")
        sh "knife supermarket show ${cookbook} ${version} >/dev/null && echo \$?; echo \$? > .upload_version_check"
        version_result = readFile('.upload_version_check').trim()
        sh "rm .upload_version_check"
    } else {
        configureKnifeClient(environment, "CHEF")
        sh "knife cookbook show ${cookbook} ${version} >/dev/null && echo \$?; echo \$? > .upload_version_check"
        version_result = readFile('.upload_version_check').trim()
        sh "rm .upload_version_check"
    }

    removeKnifeClient()

    if (version_result == '0') {
        echo "Cookbook '${cookbook}' version ${version} is already integrated. Skipping artifact upload."
        return true
    }

    echo "Cookbook '${cookbook}' version ${version} will be uploaded to the appropriate repos."
    return false
}

def cleanup() {
    sh 'rm -rf *'
    sh 'rm -rf .[^.] .??*'
}

def validateParameters(project, cookbook, checkoutClosure) {
    if (!project) {
        error 'Project must be passed to runPipeline()!'
    }
    if (!cookbook) {
        error 'Cookbook must be passed to runPipeline()!'
    }
    if (!checkoutClosure) {
        error 'Checkout closure must be passed to runPipeline()!'
    }
}

def sendEmail(toAddress, subject, body='') {
    mail(to: toAddress, from: 'Chef_Cookbook_Integration_Pipeline', subject: subject, body: body)
}

def getProject(gitRepo) {
    gitRepo.tokenize('/')[-2].toLowerCase()
}

def getCookbook(gitRepo) {
    gitRepo.tokenize('/')[-1].tokenize('.').get(0)
}

def getArtifactGroupId(project) {
    project == 'extcook' ? 'io/chef' : 'com/dettonville/chef'
}

def captureChecksum(file) {
    sh "sha256sum ${file} > ${file}.sha"
    stash name: "${file} checksum", includes: "${file}.sha"
}

def validateChecksum(file) {
    unstash "${file} checksum"
    sh "sha256sum ${file} > ${file}.cmp"
    currentChecksum = readFile("${file}.cmp").trim()
    origChecksum = readFile("${file}.sha").trim()
    if ( currentChecksum != origChecksum ) {
        error "Checksum for ${file} doesn't match the one on record - pipeline ending due to possible interference with ${file} content."
    }
}

@NonCPS
def getCookbookIncrementalChanges(cookbook, project, version, environment, maxPromotionEnv, integratedVersion) {
    def reviewContent = ""
    def changeLogSets = currentBuild.changeSets
    def changeString = ""
    for (int i = 0; i < changeLogSets.size(); i++) {
        def entries = changeLogSets[i].items
        for (int j = 0; j < entries.length; j++) {
            def entry = entries[j]
            changeString += "https://fusion.dettonville.int/stash/projects/${project}/repos/${cookbook}/commits/${entry.commitId}\n"
        }
    }

    if (!integratedVersion) {
        versionInfo = "The Cookbook is not yet promoted into ${maxPromotionEnv}. The proposed Cookbook version to be promoted to ${maxPromotionEnv} is ${version}.\n\nThe Source Code commits of the Cookbook version to be integrated can be reviewed here:\n"
    } else {
        versionInfo = "The current version of the Cookbook in ${maxPromotionEnv} is version ${integratedVersion}.  The proposed Cookbook version to be promoted to ${maxPromotionEnv} is ${version}.\n\nThe Source Code commits of the Cookbook version to be integrated can be reviewed here:\n"
    }

    reviewContent += "\nCookbook Review Summary\n\n" + versionInfo

    if (!changeString) {
        reviewContent += "https://fusion.dettonville.int/stash/projects/${project}/repos/${cookbook}/commits"
    } else {
        reviewContent += changeString
    }

    return reviewContent
}

this
