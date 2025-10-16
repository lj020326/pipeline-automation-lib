def call(String path) {
    // Remove the wildcard and any trailing slash
    def sanitizedPath = path.replaceFirst(/\*$/, '').replaceAll(/[\\/]$/, '')

    // Create a File object from the sanitized path
    def file = new File(sanitizedPath)

    // Get the parent directory, or if it's the root, return the name itself
    def dirName = file.parent ?: file.name
    return dirName
}
