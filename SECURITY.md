# Security

If something here can be used against a person, against the project, or against a phone that runs
it, report it in private and let it be fixed before it is written about.

## How to report it

Use the repository's own channel: the Security tab on the repository page, then **Report a
vulnerability**. That opens a private advisory that only you and the maintainer can read, and it
stays private until a fix is out. A pull request is the wrong place for a report that has not been
fixed yet, and an issue is the wrong place for one at all.

Say what you found, what an attacker can do with it, and how to reproduce it. A phone model, the
Android version, the version of the app and the steps are enough. A proof is better than a
description.

If the advisory form is not what you want, open an issue that says only that you want to talk
about something privately, and the details follow from there.

## What not to post in public

- the release signing key, the keystore, or either password
- a signed download link, which is a credential for the file it points at until it expires
- the fine-grained token used for publishing
- the name, the address or the number of another person

## The one that matters most

**The release signing key is the app's identity.** It is a 4,096-bit RSA key, self-signed
`CN=Aura Store`, valid until 21 March 2054. The keystore, the store password and the key password
together are enough to sign an APK that Android accepts as an update of Aura Store, with the
permissions the app asks for, which include handing other APKs to the installer.

If any of those three values is exposed, say so at once, and do not post the value itself.

What follows a leak cannot be undone by changing the passwords: the passwords only open the
keystore, and the keystore would already be out. The key itself has to change, and with it the
application id, because Android ties an update to the signing key. A new id installs beside the old
build rather than replacing it, so every phone that has the old copy needs the new one installed
again. That is the cost, and it is better than the alternative.

## Supported versions

| Version | Supported |
|---|---|
| 1.1.0 | yes |

A fix lands in the next release. There is no long term support branch.

## What the app promises, and what it does not

The download chain proves that a file arrived intact, that it is a valid package, and that it
carries the same signing key as the copy installed before it. It does not prove that the app inside
is safe, and no signature can. The README says the same thing in full.

Transport is TLS only, the manifest forbids cleartext traffic, every HTTP client in the app refuses
an address that is not `https`, and backup is switched off so the queue and its signed links stay
on the phone.
