#!/usr/bin/env groovy

def call(body, Map pipelineParams) {

	def vaultUtil = new com.dettonville.api.pipeline.utility.VaultUtil(this)
	def caasUtil = new com.dettonville.api.pipeline.utility.CaaSUtil(this)
	def pcfUtil = new com.dettonville.api.pipeline.utility.PCFUtil(this)
	def mvnUtil = new com.dettonville.api.pipeline.utility.MavenUtil(this)
	def parseUtil = new com.dettonville.api.pipeline.utility.ParseUtil(this)
	def keyMaps = [:]
//	def pipelineParams= [:]
	def config = [:]
	def deployToDev = true
	def deployToStage = true
	def deployToBelProd = true
	def deployToStlProd = true
	def deployToKscProd = true
	def deployServices = true
	def runTests = true
	def runJMeter = true
	def runSonar = false
	def runFindbugs = false
	def hasApps = true
	def runBuild = false
	def runArtifactory = false
	def runCreateRelease = false
	def runCreateSnapshot = false
	def doPublish = false
	def updatePOM = false
	def writeToStlDevVault = false
	def writeToStlStageVault = false
	def writeToStlProdVault = false
	def writeToBelProdVault = false
	def writeToKscProdVault = false

	body.resolveStrategy = Closure.DELEGATE_FIRST
//	body.delegate = pipelineParams

//	body()

	if(!pipelineParams?.ymlConfigFile && !pipelineParams?.yml==null) {
		echo "ymlConfigFile=${pipelineParams.ymlConfigFile}"
		echo "yml=${pipelineParams.yml}"
		error("This pipeline requires the pipelineParams Map to include either 'yml' containing valid pipeline yml syntax or 'ymlConfigFile' containing path of yml config file.")
	}

	pipeline {
		agent { label "QA-LINUX || PROD-LINUX" }
		stages {
			stage('Checkout') {
				when {
					expression { pipelineParams?.ymlConfigFile }
				}
				steps {
					script {
						deleteDir()
						checkout scm
						config = readYaml file: pipelineParams.ymlConfigFile
					}
				}
			}
			stage('Initialize template') {
				agent { label "M3" }
				steps {
					script {
						mvnUtil.gitPullSharedAnsibleFiles(this,'2.0.0-RELEASE')
						if (pipelineParams?.yml) {
							config = parseUtil.parseYaml(this,pipelineParams.yml)
						}

						if(!config.release.pcf_environments?.nyc_dev) deployToDev = false
						if(!config.release.pcf_environments?.nyc_stage) deployToStage = false
						if(!config.release.pcf_environments?.bel_prod) deployToBelProd = false
						if(!config.release.pcf_environments?.nyc_prod) deployToStlProd = false
						if(!config.release.pcf_environments?.jpn_prod) deployToKscProd = false
						if(!config.release?.integration_tests) runTests = false
						if(!config.release?.performance_tests) runJMeter = false
						if(!config.release?.services) deployServices = false
						if(!config.release?.spring_boot) hasApps = false
						config.release.spring_boot.each {
							if(it.sonar == null || it.sonar) runSonar = true
						}
						config.release.spring_boot.each {
							if(it.skip_build == null || !it?.skip_build) runBuild = true
						}
						config.release.spring_boot.each {
							if(it?.findbugs) runFindbugs = true
						}
						config.release.spring_boot.each {
							if(it?.artifactory_deploy) runArtifactory = true
						}
						config.release.spring_boot.each {
							if(it?.create_snapshot) runCreateSnapshot = true
						}
						config.release.spring_boot.each {
							if(it?.create_release) runCreateRelease = true
						}

						config.release.pcf_environments?.nyc_dev.each {
							if(it?.deploy_snapshot && it?.deploy_release) {
								error("NYC-DEV has conflicting deploy_snapshot and deploy_release in the config")
							}
						}
						config.release.pcf_environments?.nyc_stage.each {
							if(it?.deploy_snapshot && it?.deploy_release) {
								error("NYC-STAGE has conflicting deploy_snapshot and deploy_release in the config")
							}
						}
						config.release.pcf_environments?.nyc_prod.each {
							if(it?.deploy_snapshot && it?.deploy_release) {
								error("NYC-PROD has conflicting deploy_snapshot and deploy_release in the config")
							}
						}
						config.release.pcf_environments?.jpn_prod.each {
							if(it?.deploy_snapshot && it?.deploy_release) {
								error("jpn- has conflicting deploy_snapshot and deploy_release in the config")
							}
						}
						config.release.pcf_environments?.bel_prod.each {
							if(it?.deploy_snapshot && it?.deploy_release) {
								error("BEL-PROD has conflicting deploy_snapshot and deploy_release in the config")
							}
						}

						config.release.spring_boot.each {
							def useVault = false
							def vaultCredentialsId = ''
							def vaultAppRole = ''
							def vaultBackendId = ''
							if(it?.vault) {
								useVault = true
							}
						}

						if(config.release?.vault?.environments?.nyc_dev) writeToStlDevVault = true
						if(config.release?.vault?.environments?.nyc_stage) writeToStlStageVault = true
						if(config.release?.vault?.environments?.nyc_prod) writeToStlProdVault = true
						if(config.release?.vault?.environments?.bel_prod) writeToBelProdVault = true
						if(config.release?.vault?.environments?.jpn_prod) writeToKscProdVault = true


						config.release.spring_boot.each {
							keyMaps[it.apphost_name] = [:]
							mvnUtil.gitPull(this, it.gitrepo, it.branch)
							stash includes: '**', name: "${it.apphost_name}-pre-workspace"
							stash includes: '**', name: "${it.apphost_name}-workspace"
					  }
					}
				}
			}

			stage('Static Analysis - findbugs') {
				agent { label "QA-LINUX || PROD-LINUX" }
				when {
					expression { runFindbugs == true }
				}
				steps {
					script {
						def mvnHome = tool 'M3'
						config.release.spring_boot.each {
							if(it?.findbugs) {
								deleteDir()
								unstash "${it.apphost_name}-pre-workspace"

								def pomFile = ''
								if(it?.pom_file) pomFile = " -f ${it?.pom_file}"

								sh "${tool 'M3'}/bin/mvn clean compile ${pomFile} -Dfindbugs=true"

								def targetFolder = ''
								if(it?.pom_file && it?.pom_file.contains('/')) {
									targetFolder = it?.pom_file.substring(0,(it?.pom_file.lastIndexOf('/')+1))
								}
								targetFolder = "${targetFolder}target/findbugs"

								steps.publishHTML (target: [
						            allowMissing: true,
						            alwaysLinkToLastBuild: false,
						            keepAll: true,
						            reportDir: "${targetFolder}",
						            reportFiles: "findbugsXml.html",
						            reportName: "${it.apphost_name}-findbugs"
						    ])
								stash "${it.apphost_name}-pre-workspace"
							}
					  }
					}
				}
			}

			stage('Static Analysis - Sonar') {
				agent { label "QA-LINUX || PROD-LINUX" }
				when {
					expression { runSonar == true }
				}
				steps {
					script {
						def mvnHome = tool 'M3'

						config.release.spring_boot.each {
							if(it.sonar == null || it.sonar) {
								deleteDir()

								def pomFile = 'pom.xml'
								if(it?.pom_file) pomFile = it?.pom_file

								unstash "${it.apphost_name}-pre-workspace"
								sh "${tool 'M3'}/bin/mvn clean org.jacoco:jacoco-maven-plugin:0.7.4.201502262128:prepare-agent install -Dmaven.test.failure.ignore=true -f ${pomFile}"
								if(it?.sonar_exclusions) {
									mvnUtil.runSonarWithExclusions(this, it.branch,it.sonar_exclusions,pomFile)
								}else {
									mvnUtil.runSonar(this, it.branch,pomFile)
								}
							}
					  }
					}
				}
			}

			stage('Build') {
				agent { label "QA-LINUX || PROD-LINUX" }
				when {
					expression { runBuild == true }
				}
				steps {
					script {
						config.release.spring_boot.each {

							if(!it?.skip_build) {
								deleteDir()
								unstash "${it.apphost_name}-pre-workspace"

								def pomFile = 'pom.xml'
								if(it?.pom_file) pomFile = it?.pom_file

								if(it.skip_tests) {
									mvnUtil.mvnPackageNoKarma(this, pomFile,'-DskipTests=true')
								}else {
									mvnUtil.mvnPackageNoKarma(this, pomFile)
								}

								stash includes: '**', name: "${it.apphost_name}-workspace"
							}
						}
					}
				}
			}

			stage('Artifactory Deploy') {
				agent { label "QA-LINUX || PROD-LINUX" }
				when {
					expression { runArtifactory == true }
				}
				steps {
					script {

						echo "artficatory deploy worked"
						config.release.spring_boot.each {

							def artifactoryCreds = it?.artifactory_creds

							deleteDir()
							unstash "${it.apphost_name}-pre-workspace"

							def pomFile = 'pom.xml'
							if(it?.pom_file) pomFile = it?.pom_file

							mvnUtil.mvnDeployToArtifactory(this, artifactoryCreds,pomFile)
						}
					}
				}
			}

			stage('Create Snapshot') {
				agent { label "QA-LINUX || PROD-LINUX" }
				when {
					expression { runCreateSnapshot == true }
				}
				steps {
					script {

						config.release.spring_boot.each {

							def artifactoryCreds = it?.artifactory_creds
							def bitbucketCreds = it?.bitbucket_creds

							deleteDir()
							mvnUtil.gitPull(this, it.gitrepo, it.branch)

							def pomFile = 'pom.xml'
							if(it?.pom_file) pomFile = it?.pom_file
							def pom = readMavenPom file: pomFile

							if(pom.version.contains('RELEASE')) {
								error("You can not create a snapshot from a release version")
							}

							def versionNUmberToUpdate = pom.version

							def result = sh (returnStdout: true, script: "git ls-remote --heads ${it.gitrepo} ${versionNUmberToUpdate} | wc -l")
							echo "result is ${result}"
							if (result.contains('1')) {
								error("There is already a snapshot branch created for ${versionNUmberToUpdate}.")
							}else {

								def remoteOrigin = it.gitrepo.replace('https://','')

								withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: bitbucketCreds, usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD']]) {
									sh('git config --global user.email "jenkins@dettonville.com"')
									sh('git config --global user.name "Jenkins Pipeline"')
									sh("git remote set-url origin https://${GIT_USERNAME}:${GIT_PASSWORD}@${remoteOrigin}")
									sh("git checkout -b ${versionNUmberToUpdate}")
									sh("git push -u origin ${versionNUmberToUpdate}")

									mvnUtil.mvnDeployToArtifactory(this, artifactoryCreds,pomFile)
									updatePOM = true
								}
							}
						}
					}
				}
			}

			stage('Write To Vault - Dev') {
				agent { label "QA-M3" }
				when {
					expression { writeToStlDevVault == true}
				}
				steps {
					script {
						insertIntoVault(config,'nyc-dev',vaultUtil)
					}
				}
			}

			stage('Create Services - Dev') {
				agent { label "QA-CF-CLI" }
				when {
					expression { deployServices == true && deployToDev == true}
				}
				steps {
					script {
						deployServicesToPCF(config,'nyc-dev')
					}
				}
			}

			stage('Get Certs - nyc-dev') {
				agent { label "QA-CAAS-CLIENT" }
				when {
					expression { deployToDev == true && hasApps == true }
				}
				steps {
					script {
						getCertsForPCF(config,'nyc-dev',keyMaps,caasUtil)
					}
				}
			}

			stage('Deploy - nyc-dev') {
				agent { label "QA-CF-CLI" }
				when {
					expression { deployToDev == true && hasApps == true }
				}
				steps {
					script {
						deployAppToPCF(config,keyMaps,pcfUtil,mvnUtil,'nyc-dev')
					}
				}
			}

			stage('Run Spock Tests - nyc-dev') {
				agent { label "QA-M3" }
				when {
					expression { runTests == true && deployToDev == true }
				}
				steps {
					script {
						runSpockTestsInPCF(config,'nyc-dev')
					}
				}
			}

			stage('Run JMeter - nyc-dev') {
				agent { label "QA-M3" }
				when {
					expression { runJMeter == true && deployToDev == true }
				}
				steps {
					script {
						runPerformanceTestsInPCF(config,'nyc-dev')
					}
				}
			}

			stage('Write To Vault - Stage') {
					agent { label "QA-M3" }
					when {
						expression { writeToStlStageVault == true}
					}
					steps {
							script {
								insertIntoVault(config,'nyc-stage',vaultUtil)
							}
					}
			}

			stage('Create Services - Stage') {
					agent { label "QA-CF-CLI" }
					when {
						expression { deployServices == true && deployToStage == true }
					}
					steps {
							script {
								deployServicesToPCF(config,'nyc-stage')
							}
					}
			}

			stage('Get Certs - nyc-stage') {
				agent { label "QA-CAAS-CLIENT" }
				when {
					expression { deployToStage == true && hasApps == true }
				}
				steps {
					script {
						getCertsForPCF(config,'nyc-stage',keyMaps,caasUtil)
					}
				}
			}

			stage('Deploy - nyc-stage') {
				agent { label "QA-CF-CLI" }
				when {
					expression { deployToStage == true && hasApps == true }
				}
				steps {
					script {
						deployAppToPCF(config,keyMaps,pcfUtil,mvnUtil,'nyc-stage')
					}
				}
			}

			stage('Run Spock Tests - nyc-stage') {
				agent { label "QA-M3" }
				when {
					expression { runTests == true && deployToStage == true }
				}
				steps {
					script {
						runSpockTestsInPCF(config,'nyc-stage')
					}
				}
			}

			stage('Run JMeter - nyc-stage') {
				agent { label "QA-M3" }
				when {
					expression { runJMeter == true && deployToStage == true }
				}
				steps {
					script {
						runPerformanceTestsInPCF(config,'nyc-stage')
					}
				}
			}

			stage('Create Release') {
				agent { label "QA-LINUX || PROD-LINUX" }
				when {
					expression { runCreateRelease == true }
				}
				steps {
					script {

						 config.release.spring_boot.each {

						 	def artifactoryCreds = it?.artifactory_creds
						 	def bitbucketCreds = it?.bitbucket_creds

						 		deleteDir()
						 		mvnUtil.gitPull(this, it.gitrepo, it.branch)

						 		def pomFile = 'pom.xml'
						 		if(it?.pom_file) pomFile = it?.pom_file
						 		def pom = readMavenPom file: pomFile

						 		def newBranchName = "${pom.version}"
						 		newBranchName = newBranchName.replace('-SNAPSHOT','-RELEASE')

	 							def result = sh (returnStdout: true, script: "git ls-remote --heads ${it.gitrepo} ${newBranchName} | wc -l")
								echo "result is ${result}"
								if (result.contains('1')) {
									error("There is already a release branch created for ${newBranchName}.")
  								return
								}else {

									def remoteOrigin = it.gitrepo.replace('https://','')

									withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: bitbucketCreds, usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD']]) {
										sh('git config --global user.email "jenkins@dettonville.com"')
										sh('git config --global user.name "Jenkins Pipeline"')
										sh("git remote set-url origin https://${GIT_USERNAME}:${GIT_PASSWORD}@${remoteOrigin}")
										//sh("git push --set-upstream origin ${it.branch}")

										sh("git checkout -b ${newBranchName}")

										pom.version = newBranchName
										writeMavenPom model: pom

										sh("git add ${pomFile}")
										sh("git commit -m '[maven-release-plugin] updated version for ${newBranchName}'")
										sh("git push -u origin ${newBranchName}")
									}

									mvnUtil.mvnDeployToArtifactory(this, artifactoryCreds)
								}
						 }
					}
				}
			}

			stage('Write To Vault - bel-prod') {
					agent { label "M3" }
					when {
						expression { writeToBelProdVault == true}
					}
					steps {
							script {
								insertIntoVault(config,'bel-prod',vaultUtil)
							}
					}
			}

			stage('Write To Vault - nyc-prod') {
					agent { label "M3" }
					when {
						expression { writeToStlProdVault == true}
					}
					steps {
							script {
								insertIntoVault(config,'nyc-prod',vaultUtil)
							}
					}
			}

			stage('Write To Vault - jpn-') {
					agent { label "M3" }
					when {
						expression { writeToKscProdVault == true}
					}
					steps {
							script {
								insertIntoVault(config,'jpn-',vaultUtil)
							}
					}
			}

			stage('Create Services - bel-prod') {
					agent { label "CF-CLI" }
					when {
						expression { deployServices == true && deployToBelProd == true }
					}
					steps {
							script {
								deleteDir()
								deployServicesToPCF(config,'bel-prod')
					}
				}
			}

			stage('Create Services - nyc-prod') {
					agent { label "CF-CLI" }
					when {
						expression { deployServices == true && deployToStlProd == true }
					}
					steps {
							script {
								deleteDir()
								deployServicesToPCF(config,'nyc-prod')
							}
					}
			}

			stage('Create Services - jpn-') {
					agent { label "CF-CLI" }
					when {
						expression { deployServices == true && deployToKscProd == true }
					}
					steps {
							script {
								deleteDir()
                deployServicesToPCF(config,'jpn-')
							}
					}
			}

			stage('Get Certs - bel-prod') {
				agent { label "CAAS-CLIENT" }
				when {
					expression { deployToBelProd == true && hasApps == true }
				}
				steps {
					script {
						getCertsForPCF(config,'bel-prod',keyMaps,caasUtil)
					}
				}
			}

			stage('Get Certs - nyc-prod') {
				agent { label "CAAS-CLIENT" }
				when {
					expression { deployToStlProd == true && hasApps == true }
				}
				steps {
					script {
						getCertsForPCF(config,'nyc-prod',keyMaps,caasUtil)
					}
				}
			}

			stage('Get Certs - jpn-') {
				agent { label "CAAS-CLIENT" }
				when {
					expression { deployToKscProd == true && hasApps == true }
				}
				steps {
					script {
						getCertsForPCF(config,'jpn-',keyMaps,caasUtil)
					}
				}
			}

			stage('Deploy - bel-prod') {
				agent { label "CF-CLI" }
				when {
					expression { deployToBelProd == true && hasApps == true }
				}
				steps {
					script {
						deployAppToPCF(config,keyMaps,pcfUtil,mvnUtil,'bel-prod')
					}
				}
			}

			stage('Deploy - nyc-prod') {
				agent { label "CF-CLI" }
				when {
					expression { deployToStlProd == true && hasApps == true }
				}
				steps {
					script {
						deployAppToPCF(config,keyMaps,pcfUtil,mvnUtil,'nyc-prod')
					}
				}
			}

			stage('Deploy - jpn-') {
				agent { label "CF-CLI" }
				when {
					expression { deployToKscProd == true && hasApps == true }
				}
				steps {
					script {
						deployAppToPCF(config,keyMaps,pcfUtil,mvnUtil,'jpn-')
					}
				}
			}

			stage('Run Spock Tests - bel-prod') {
				agent { label "M3" }
				when {
					expression { runTests == true && deployToBelProd == true }
				}
				steps {
					script {
						runSpockTestsInPCF(config,'bel-prod')
					}
				}
			}

			stage('Run Spock Tests - nyc-prod') {
				agent { label "M3" }
				when {
					expression { runTests == true && deployToStlProd == true }
				}
				steps {
					script {
						runSpockTestsInPCF(config,'nyc-prod')
					}
				}
			}

			stage('Run Spock Tests - jpn-') {
				agent { label "M3" }
				when {
					expression { runTests == true && deployToKscProd == true }
				}
				steps {
					script {
						runSpockTestsInPCF(config,'jpn-')
					}
				}
			}

			stage('Run JMeter - bel-prod') {
				agent { label "M3" }
				when {
					expression { runJMeter == true && deployToBelProd == true }
				}
				steps {
					script {
						runPerformanceTestsInPCF(config,'bel-prod')
					}
				}
			}

			stage('Run JMeter - nyc-prod') {
				agent { label "M3" }
				when {
					expression { runJMeter == true && deployToStlProd == true }
				}
				steps {
					script {
						runPerformanceTestsInPCF(config,'nyc-prod')
					}
				}
			}

			stage('Run JMeter - jpn-') {
				agent { label "M3" }
				when {
					expression { runJMeter == true && deployToKscProd == true }
				}
				steps {
					script {
						runPerformanceTestsInPCF(config,'jpn-')
					}
				}
			}

			stage('Publish Results and update') {
				agent { label "M3" }
				when {
				expression { doPublish == true}
				}
				steps {
					script {
						echo "Not Implemented"
					}
				}
			}
		}

		post {
			always {
				script {
					if(updatePOM ) {
						config.release.spring_boot.each {
							def bitbucketCreds = it?.bitbucket_creds
							deleteDir()
							mvnUtil.gitPull(this, it.gitrepo, it.branch)

							def pomFile = 'pom.xml'
							if(it?.pom_file) pomFile = it?.pom_file
							def pom = readMavenPom file: pomFile

							def versionNUmberToUpdate = pom.version
							def lastPart = versionNUmberToUpdate.replace('-SNAPSHOT','').tokenize('.').last()
							int intVersion = -1
							try {
						  	intVersion = lastPart as Integer
							}catch(e) {
								error("unable to update POM version because there was an error converting version to integer(${pom.version})")
							}

							intVersion++
							int versionSize = versionNUmberToUpdate.tokenize('.').size()
							def versionArray = versionNUmberToUpdate.tokenize('.')

							def newVersion = ''
							for(int i=0;i<versionSize-1;i++) {
								newVersion += versionArray[i]
								newVersion += '.'
							}
							newVersion += intVersion
							newVersion += '-SNAPSHOT'

							def remoteOrigin = it.gitrepo.replace('https://','')

							withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: bitbucketCreds, usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD']]) {
								sh('git config --global user.email "jenkins@dettonville.com"')
								sh('git config --global user.name "Jenkins Pipeline"')
								sh("git remote set-url origin https://${GIT_USERNAME}:${GIT_PASSWORD}@${remoteOrigin}")

								pom.version = newVersion
								writeMavenPom model: pom

								sh("git add ${pomFile}")
								sh("git commit -m '[maven-release-plugin] updated version for ${it.branch}'")
								sh("git push --set-upstream origin ${it.branch}")
							}
						}
					}
					if(config.release?.notifications?.email?.address_list) {
						def addressList = ''
						config.release?.notifications?.email?.address_list.each {
							addressList += "${it}, "
						}
						if(addressList != '') {
							addressList = addressList[0..-3]
							echo "addressList is ${addressList}"
							if(config.release?.notifications?.email?.send_always) {
								notifyBuild(currentBuild.result,addressList,true)
							}else {
								notifyBuild(currentBuild.result,addressList)
							}

						}
					}
				}
			}
		}

	}
}

def insertIntoVault(config,env,vaultUtil) {

	def configEnv = env.replace('-','_')
	def vaultCredentials = config.release?.vault?.environments?."${configEnv}"?.vault_credentials
	def verbose = false
	if(config.release?.vault?.environments?."${configEnv}"?.verbose.equalsIgnoreCase('true')) {
		verbose = true
	}
	def directory = config.release?.vault?.environments?."${configEnv}"?.directory

	def insertMap = [:]
	config.release?.vault?.environments?."${configEnv}"?.insert.each {
		insertMap[it.key] = it.value
	}

	config.release?.vault?.environments?."${configEnv}"?.jenkins_credentials.each {
			withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${it.credentials_id}", usernameVariable: 'KEY', passwordVariable: 'VALUE']]) {
				insertMap[it.key] = it.value
		  }
	}

	withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${vaultCredentials}", usernameVariable: 'ROLE_ID', passwordVariable: 'SECRET_ID']]) {
		vaultUtil.writeSecrets(this, env, ROLE_ID, SECRET_ID, directory,insertMap,verbose)
	}

}

def getCertsForPCF(config,env,keyMaps,caasUtil) {

	def configEnv = env.replace('-','_')

	config.release.spring_boot.each {
		if(config.release.pcf_environments?."${configEnv}") {
			deleteDir()
			def keyMap = keyMaps[it.apphost_name]
			if(it.ou_name) {
				caasUtil.getJKSFromCaaS(this, it.apphost_name, env, keyMap, it.cn_name, it.ou_name)
			}else {
				caasUtil.getJKSFromCaaS(this, it.apphost_name, env, keyMap, it.cn_name)
			}
		}
	}
}

def deployAppToPCF(config,keyMaps,pcfUtil,mvnUtil,env) {

	def configEnv = env.replace('-','_')
	def envLoc = env.split('-')[0]
	def envPhase = env.split('-')[1]

	config.release.spring_boot.each {
		if(config.release.pcf_environments?."${configEnv}") {
			deleteDir()
			def autoscaling_map = [:]
			def workspace = "${it.apphost_name}-workspace"
			def appHostName = it.apphost_name
			def manifestFile = (it?.manifest_file) ? it?.manifest_file : './manifest.yml'
			def springProfile = (config.release.env_profiles) ? "${envPhase}" : null
			def keyMap = keyMaps[it.apphost_name]
			def instanceCount = it?.instance_count ?: 1
			def gitrepo = it.gitrepo
			def branch = it.branch
			def pomFile = 'pom.xml'
			if(it?.pom_file) pomFile = it?.pom_file
			def useVault = false
			def vaultCredentialsId = ''
			def vaultAppRole = ''
			def vaultBackendId = ''
			def vaultApplicationName = ''

			if(it?.vault) {
				useVault = true
			  vaultCredentialsId = it?.vault?."${configEnv}"?.vault_credentials
			 	vaultAppRole = it?.vault?."${configEnv}"?.app_role
			  vaultBackendId = it?.vault?."${configEnv}"?.backend_id
			  vaultApplicationName = it?.vault?."${configEnv}"?.application_name
			}

			if(it?.environment_overrides?."${configEnv}") {
				it?.environment_overrides?."${configEnv}".each {
					keyMap["PCFENV_${it.key}"] = it.value
				}
			}

			// for legacy set the instance count
			if(instanceCount > 1) {
				echo "SETTING LEGACRY INSTANCE COUNT TO ${instanceCount}"
				keyMap["instance_count"] = "${instanceCount}"
			}

			//check to see if autoscaling is set up
			if(it?.autoscaling) {
				//update default instance count if its set
				if(it?.autoscaling?.default_instance_count) keyMap["instance_count"] = "${it?.autoscaling?.default_instance_count}"

				//go through env and see if any org space match current org space and then if they have instance count
				// and if so then put that into a map for later consumption
				it?.autoscaling?.pcf_environments?."${configEnv}".each {
						if(it?.instance_count) autoscaling_map["${it.pcf_org}${it.pcf_space}"] = it?.instance_count
				}
			}

			config.release.pcf_environments."${configEnv}".each {
				def key = "${it.pcf_org}${it.pcf_space}"
				def targeted_instance_count = autoscaling_map[key]
				if(targeted_instance_count) {
					echo "settting autoscaling on the key ${key} instance count to ${targeted_instance_count}"
					keyMap["instance_count"] = "${targeted_instance_count}"
				}else {
					echo "NOT SETTING INSTANCE COUNT NO VALUE FOUND FOR ${key}"
				}

				if(it?.deploy_from_snapshot || it?.deploy_from_release) {
					if(it?.deploy_from_snapshot && it?.deploy_from_release) {
						error("you can not set deploy from snapshot and deploy from release in the same env")
					}
					mvnUtil.gitPull(this, gitrepo, branch)
					def pom = readMavenPom file: pomFile
					def branchName = "${pom.version}"
					def baseURL = ''
					if(it?.deploy_from_snapshot) {
						branchName = branchName.replace('-RELEASE','-SNAPSHOT')
						baseURL = 'https://gitrepository.dettonville.int/artifactory/snapshots'
					}
					if(it?.deploy_from_release) {
						branchName = branchName.replace('-SNAPSHOT','-RELEASE')
						baseURL = 'https://gitrepository.dettonville.int/artifactory/releases'
					}
					deleteDir()
					mvnUtil.gitPull(this, gitrepo, branchName)

					pom = readMavenPom file: pomFile
					def jarSuffix = it?.jar_suffix ?: ''
					sh "curl -o ${pom.artifactId}-${pom.version}${jarSuffix}.${pom.packaging} ${baseURL}/${pom.groupId.replace('.','/')}/${pom.artifactId}/${pom.version}/${pom.artifactId}-${pom.version}${jarSuffix}.${pom.packaging}"

					def dirToMove = ''
					if(manifestFile.contains("/")) {
						dirToMove = manifestFile.substring(0, manifestFile.lastIndexOf("/"))
						sh "cp ${pom.artifactId}-${pom.version}${jarSuffix}.${pom.packaging} ${dirToMove}${pom.artifactId}-${pom.version}${jarSuffix}.${pom.packaging}"
					}

					sh "sed -i '/path:/c\\  path: ${pom.artifactId}-${pom.version}${jarSuffix}.${pom.packaging}' ${manifestFile}"
					echo "HERE IS NEW MANIFEST FILE"
					sh "cat ${manifestFile}"
				} else {
					unstash workspace
				}

				def suffix = it?.route_suffix ?: ''
				echo "USING SUFFIX ${suffix}"

				if(useVault) {
					if(vaultApplicationName) {
						keyMap["vaultApplicationName"] = "${vaultApplicationName}"
					}
					pcfUtil.deployToPCFGoRouter(this, "${appHostName}${suffix}", env,it.pcf_org,it.pcf_space,it.credentials_string,vaultCredentialsId,vaultBackendId, keyMap, true, true,springProfile,".",manifestFile)
				}else {
					pcfUtil.deployToPCFGoRouter(this, "${appHostName}${suffix}", env,it.pcf_org,it.pcf_space,it.credentials_string,'','', keyMap, true, false,springProfile,".",manifestFile)
				}
			}
		}
	}
}

def runSpockTestsInPCF(config,env) {

	def configEnv = env.replace('-','_')
	def envLoc = env.split('-')[0]
	def envPhase = env.split('-')[1]
	def publishTests = true

	if(config.release?.integration_tests?.publish == false) {
		publishTests = false
	}

	if(config.release?.integration_tests) {
		if(config.release.pcf_environments?."${configEnv}") {
			steps.deleteDir()
			steps.git branch: "${config.release.integration_tests.branch}", url: "${config.release.integration_tests.gitrepo}"

			config.release.pcf_environments?."${configEnv}".each {
				def target_service_baseurl = " -Dtarget.service.base-url=\".apps.${envLoc}.pcf${envPhase}00.dettonville.int\""
				def app_route_suffix = ''
				def skipIntegrationTests = false
				if(it?.skip_integration_tests) {
					skipIntegrationTests = true
				}
				def title_suffix = it?.pcf_space
				if(it?.route_suffix) {
					app_route_suffix = " -Dapp.route.suffix=${it?.route_suffix}"
				}
				def testingParams = ''
				if(it?.testing_params) {
					testingParams = " ${it?.testing_params}"
				}

				def ignoreFailures = "false"
				if(config?.release?.integration_tests?.ignore_failures) {
					ignoreFailures = "true"
				}

				def failBuild = false

				if(!skipIntegrationTests) {
					try {
						steps.sh "${steps.tool 'M3'}/bin/mvn clean test -Dmaven.test.failure.ignore=${ignoreFailures}${target_service_baseurl}${app_route_suffix}${testingParams}"
					}catch(e) {
						failBuild = true
					}
					if(publishTests) {
						steps.publishHTML (target: [
							allowMissing: false,
							alwaysLinkToLastBuild: false,
							keepAll: true,
							reportDir: "target/spock-reports",
							reportFiles: "index.html",
							reportName: "${env}-${title_suffix}-${config.release.integration_tests.spockreport_title}"
						])

						steps.publishHTML (target: [
							allowMissing: false,
							alwaysLinkToLastBuild: false,
							keepAll: true,
							reportDir: "target/api-report",
							reportFiles: "index.html",
							reportName: "${env}-${title_suffix}-${config.release.integration_tests.timingreport_title}"
						])
					}
					if(failBuild) {
						error("build failed due to integration tests")
					}
				}
			}
		}
	}

}



def deployServicesToPCF(config,env) {
	unstash 'ansible-workspace'

	def configEnv = env.replace('-','_')
	def envLoc = env.split('-')[0]
	def envPhase = env.split('-')[1]

	config.release.pcf_environments?."${configEnv}".each {

		def org = "${it.pcf_org}"
		def space = "${it.pcf_space}"
		def credentialsString = "${it.credentials_string}"

		if(config.release?.services?.cloud_config) {
			def configRepo = "${config.release.services.cloud_config.gitrepo}"
			def giteaBranch = "${config.release.services.cloud_config.branch}"
			def configServerName = "${config.release.services.cloud_config.name}"

			def createOnly = false
			if(config.release.services.cloud_config?.create_only) {
				createOnly = true
			}

			withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${credentialsString}", usernameVariable: 'PCF_USERNAME', passwordVariable: 'PCF_PASSWORD']]) {
				ansiblePlaybook extras: "--extra-vars @shared-ansible/env/${env}.yml --extra-vars \"configRepo=${configRepo} create_only=${createOnly} configServerName=${configServerName} giteaBranch=${giteaBranch} pcf_org=${org} pcf_space=${space} pcf_username=$PCF_USERNAME pcf_password=$PCF_PASSWORD\"", installation: 'Ansible2', playbook: 'shared-ansible/pcf/deploy-spring-cloud-config.yml'
			}
		}
		if(config.release?.services?.pcf_services) {
			config.release?.services?.pcf_services.each {
				def service_name = it.service_name
				def service = it.service
				def plan = it.plan
				def arbitrary_params = it?.arbitrary_params ?: ''

				withEnv(["CF_HOME=."]) {
					withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${credentialsString}", usernameVariable: 'PCF_USERNAME', passwordVariable: 'PCF_PASSWORD']]) {
						sh "cf login -a api.system.${envLoc}.pcf${envPhase}00.dettonville.int -u ${PCF_USERNAME} -p ${PCF_PASSWORD} -o ${org} -s ${space}"
					}
					sh "cf create-service ${arbitrary_params} ${service} ${plan} ${service_name}"

					sh "cf logout"
				}
			}
		}

	 if(config.release?.services?.user_defined_services) {
		 config.release?.services?.user_defined_services.each {

			 def service_name = it?.service_name
			 def param =  it?.param
			 if(it?.environment_overrides?."${configEnv}"?.param) {
				 param = it?.environment_overrides?."${configEnv}"?.param
			 }

			 echo "PARAM ${param} for ${configEnv}"

			 if(it.use_credentials) {
				 def service_credential_string = it.credentials_string
				 if(it?.environment_overrides?."${configEnv}"?.credentials_string) {
					 service_credential_string = it?.environment_overrides?."${configEnv}"?.credentials_string
				 }

				 withEnv(["CF_HOME=."]) {
					withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${credentialsString}", usernameVariable: 'PCF_USERNAME', passwordVariable: 'PCF_PASSWORD']]) {
						sh "cf login -a api.system.${envLoc}.pcf${envPhase}00.dettonville.int -u ${PCF_USERNAME} -p ${PCF_PASSWORD} -o ${org} -s ${space}"
					}
					withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${service_credential_string}", usernameVariable: 'UNAME', passwordVariable: 'PWD']]) {
						param = param.replace('#UNAME',"${UNAME}")
						param = param.replace('#PWD',"${PWD}")
						try {
							sh "cf cups ${service_name} ${param}"
						}catch(e) {
							sh "cf uups ${service_name} ${param}"													}
						}
					sh "cf logout"
				}
			 }else {
				 withEnv(["CF_HOME=."]) {
					withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${credentialsString}", usernameVariable: 'PCF_USERNAME', passwordVariable: 'PCF_PASSWORD']]) {
						sh "cf login -a api.system.${envLoc}.pcf${envPhase}00.dettonville.int -u ${PCF_USERNAME} -p ${PCF_PASSWORD} -o ${org} -s ${space}"
					}
					try {
						sh "cf cups ${service_name} ${param}"
					}catch(e) {
						sh "cf uups ${service_name} ${param}"													}
					}
					sh "cf logout"
			 }
			}
		}
	}
}

def runPerformanceTestsInPCF(config,env) {

	def configEnv = env.replace('-','_')
	def envLoc = env.split('-')[0]
	def envPhase = env.split('-')[1]

	if(config.release?.performance_tests) {
		if(config.release.pcf_environments?."${configEnv}") {
			steps.deleteDir()
			steps.git branch: "${config.release.performance_tests.branch}", url: "${config.release.performance_tests.gitrepo}"

			config.release.pcf_environments?."${configEnv}".each {
				def target_service_baseurl = " -Dtarget.service.base-url=\".apps.${envLoc}.pcf${envPhase}00.dettonville.int\""
				def app_route_suffix = ''
				def skipPerformanceTests = false
				if(it?.skip_performance_tests) {
					skipPerformanceTests = true
				}
				def title_suffix = it?.pcf_space
				if(it?.route_suffix) {
					app_route_suffix = " -Dapp.route.suffix=${it?.route_suffix}"
				}
				def performanceParams = ''
				if(it?.performance_params) {
					performanceParams = " ${it?.performance_params}"
				}

				if(!skipPerformanceTests) {
					echo "skipping performance tests in space ${title_suffix}"

					def failBuild = false
					try {
						steps.sh "${tool 'M3'}/bin/mvn clean verify -Pperformance -Dmaven.test.failure.ignore=true${target_service_baseurl}${app_route_suffix}${performanceParams}"
					}catch(e) {
						failBuild = true
					}


					sh "cp -r target/jmeter/reports/*/* target/jmeter/reports"

					steps.publishHTML (target: [
								allowMissing: false,
								alwaysLinkToLastBuild: false,
								keepAll: true,
								reportDir: "target/jmeter/reports",
								reportFiles: "index.html",
								reportName: "${env}-${title_suffix}-${config.release.performance_tests?.publish_title}"
					])

					if(failBuild) {
						error("build failed due to performance tests")
					}
				}
			}
		}
	}
}

def notifyBuild(String buildStatus, String emailList,Boolean onSuccessEveryTime=false) {

    def sendMail = false
    def lastBuildResult = currentBuild?.getPreviousBuild()?.getResult()
    buildStatus = buildStatus ?: 'SUCCESS'

		if(onSuccessEveryTime) {
			sendMail = true
		}
    if(!lastBuildResult) {
        sendMail = true
    } else {
        if(!'SUCCESS'.equals(lastBuildResult)) {
          if('SUCCESS'.equals(buildStatus)) {
            buildStatus = 'FIXED'
            sendMail = true
          }
        }
    }

    if(!'SUCCESS'.equals(buildStatus)) sendMail = true

    if(sendMail) {
      def subject = "${buildStatus}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'"
      def details = """${subject} (${env.BUILD_URL})

      STARTED: Job ${env.JOB_NAME} [${env.BUILD_NUMBER}]:

      Check console output at ${env.BUILD_URL}console"""
      def hostname = sh (returnStdout: true, script: 'hostname')
      def emailFrom = "${hostname.trim()}@dettonville.com"

      mail bcc: '', body: details, cc: '', from: emailFrom, replyTo: '', subject: subject, to: emailList
    }
}
