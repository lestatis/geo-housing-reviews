# Security and privacy rules

- Treat review evidence, identity documents and exact private location as highly sensitive.
- Never log, snapshot-test, fixture or commit real personal data.
- Use synthetic fixtures that cannot be mistaken for real documents.
- Check object-level authorization for every endpoint that accepts an identifier.
- Strip EXIF/GPS from public images.
- Private media requires short-lived authorization; no predictable public URLs.
- New data fields require classification, purpose and retention consideration.
- New admin actions require audit events.
- Automated risk decisions must have a review/appeal path when consequential.
