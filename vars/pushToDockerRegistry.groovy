
/**
 * Publish to Docker Private Registry. Currently this registry is hosted on Dev Cloud (https://artifactory.dev.dettonville.int/artifactory/),
 * but there are plans to have a formal Docker Private Registry in Production also.
 *
 * ref: https://fusion.dettonville.int/confluence/display/ART/Docker+Images
 */
void call(String imageName, String registryUrl, String username, String password, String path = ".") {

    final String formattedBranchName = "${BRANCH_NAME}".replace('/', '-')

    sh """
    	docker -v
        docker login --username ${username} --password ${password} ${registryUrl}

        docker build -t ${imageName}:${formattedBranchName} -f ${path} .
        docker tag -f ${imageName}:${formattedBranchName} ${registryUrl}/com-dettonville-api/${imageName}:${formattedBranchName}
        docker push ${registryUrl}/com-dettonville-api/${imageName}:${formattedBranchName}
                            
        docker logout ${registryUrl}
    """
}