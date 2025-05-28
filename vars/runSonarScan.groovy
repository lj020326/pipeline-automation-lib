
/**
 * Run Sonar code scan. The results of the analysis will be available on SonarQube (https://fusion.dettonville.int/sonar/)
 */
void call(def branchName = null) {

    final String branchParam = (branchName == null ? '' : "-Dsonar.branch=${branchName}")

    withCredentials([usernamePassword(credentialsId: 'dcapi_ci_vcs_user', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
        sh """
            mvn clean org.jacoco:jacoco-maven-plugin:0.7.4.201502262128:prepare-agent install -Dmaven.test.failure.ignore=true --batch-mode
            mvn --batch-mode -Dsonar.login="${USER}" \
                -Dsonar.password="${PASS}" \
                sonar:sonar \
                ${branchParam}
        """
    }
}