# PatternMatching

[Credentials](credentials.md) and [ManagedFiles](managed-files.md)
supports pattern matching. During pattern matching the `pattern` defined
in
[`PatternMatchable`](../src/com/dettonville/dcapi/pipeline/model/PatternMatchable.groovy)
objects is used as regular expression and evaluated against a search
value.

This mechanism is used in [`execMaven`](../vars/execMaven.groovy) and
[`checkoutScm`](../vars/checkoutScm.groovy) step in order to auto lookup
maven settins, npm repository settings, ruby bundler settings or
credentials for repository urls.

The class
[`PatternMatcher`](../src/com/dettonville/dcapi/pipeline/utils/PatternMatcher.groovy)
can be used to get items from a PatternMatchable list.

# Table of contents
* [`getBestMatch` mechanism](#getbestmatch-mechanism)
  * [Example for basic matching](#example-for-basic-matching)
  * [Example for specific project matching](#example-for-specific-project-matching)
* [Related classes](#related-classes)

## `getBestMatch` mechanism

Given a JSON at `resources/credentials/scm/credentials-example.json` with this content

```json
[
  {
    "pattern": "domain.tld[:/]group",
    "id": "group-credentials-id"
  },
  {
    "pattern": "domain.tld[:/]group/specific-project",
    "id": "specific-project-credentials-id"
  }
]
```

And you loaded and parsed this json by using the `JsonLibraryResource`
and the `CredentialParser` by using this snippet (without import statements)

```groovy
// load the json
JsonLibraryResource jsonRes = new JsonLibraryResource((DSL) this.steps, CredentialConstants.SCM_CREDENTIALS_PATH)
JSON credentialJson = jsonRes.load()
// parse the credentials
CredentialParser parser = new CredentialParser()
List<Credential> credentials = parser.parse(credentialJson)
// try to find matching credential and return the credential
PatternMatcher matcher = new PatternMatcher()
```

### Example for basic matching

When you call `matcher.getBestMatch` with "git@domain.tld:group/project.git"
```groovy
Credential result = matcher.getBestMatch("git@domain.tld:group/project.git", credentials)
```
The resulting Credential will be the `group-credentials-id` object from
the json.

### Example for specific project matching

When you call `matcher.getBestMatch` with
"git@domain.tld:group/specific-project.git"
```groovy
Credential result = matcher.getBestMatch("git@domain.tld:group/specific-project.git", credentials)
```
The result Credential will be the `specific-project-credentials-id`
object from the json.

Even when the first entry matched, the more specific pattern from the
second entry had a better match, so this Credential will be returned.

## Related classes
* [Credential](../src/com/dettonville/dcapi/pipeline/credentials/Credential.groovy)
* [CredentialParser](../src/com/dettonville/dcapi/pipeline/credentials/CredentialParser.groovy)
* [ManagedFile](../src/com/dettonville/dcapi/pipeline/managedfiles/ManagedFile.groovy)
* [ManagedFileConstants](../src/com/dettonville/dcapi/pipeline/managedfiles/ManagedFileConstants.groovy)
* [ManagedFileParser](../src/com/dettonville/dcapi/pipeline/managedfiles/ManagedFileParser.groovy)
* [PatternMatchable](../src/com/dettonville/dcapi/pipeline/model/PatternMatchable.groovy)
* [PatternMatcher](../src/com/dettonville/dcapi/pipeline/utils/PatternMatcher.groovy)
