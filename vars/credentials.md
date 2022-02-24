# credentials

This part of the pipeline library provides easy to use functions for credential auto lookup.

# Table of contents

* [`lookupHttpCredential`](#lookuphttpcredentialstring-uri)
* [`lookupScmCredential`](#lookupscmcredentialstring-uri)
* [`lookupSshCredential`](#lookupsshcredentialstring-uri)
* [Related classes](#related-classes)

## `lookupHttpCredential(String uri)`

Performs an auto lookup for HTTP (username/password) credentials for the
given `uri`.

## `lookupScmCredential(String uri)`

Performs an auto lookup for SCM credentials for the given `uri`.

## `lookupSshCredential(String uri)`

Performs an auto lookup for SSH credentials for the given `uri`.

## Related classes
* [`Credential.groovy`](../src/com/dettonville/dcapi/pipeline/credentials/Credential.groovy)
* [CredentialParser.groovy](../src/com/dettonville/dcapi/pipeline/credentials/CredentialParser.groovy)
* [PatternMatcher.groovy](../src/com/dettonville/dcapi/pipeline/utils/PatternMatcher.groovy)
