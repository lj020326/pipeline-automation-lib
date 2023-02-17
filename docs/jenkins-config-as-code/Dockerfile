FROM jenkins/jenkins:2.303.2

## ref: https://github.com/emnify/jenkins-casc-docker/blob/master/Dockerfile

# skip the setup wizard
ENV JAVA_OPTS "-Djenkins.install.runSetupWizard=false"

# install plugins
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --clean-download-directory --list --view-security-warnings -f /usr/share/jenkins/ref/plugins.txt
