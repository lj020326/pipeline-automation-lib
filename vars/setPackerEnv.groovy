
def call(Map config=[:]) {
    List buildTagList = env.BUILD_TAG.split("-")
    buildTagList[-1] = env.BUILD_NUMBER.toString().padLeft(4, '0')

    // ref: https://mrhaki.blogspot.com/2011/09/groovy-goodness-take-and-drop-items.html
    buildTagList = buildTagList.drop(3)
    templateBuildTag = buildTagList.join("-")

//    echo "templateBuildTag=${templateBuildTag}"
    env.TEMPLATE_BUILD_ID = templateBuildTag
//    env.TEMPLATE_NAME = templateBuildTag.substring(0, templateBuildTag.lastIndexOf("-"))
}
