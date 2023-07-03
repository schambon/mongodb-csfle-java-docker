# Sample Docker app using MongoDB CSFLE

Setup your environment in `rsc`:
- `openssl rand 96 > master-key.txt`
- edit `config.json` to add your MongoDB URI

_Important_: if you run on ARM (eg Apple Silicon Mac or AWS Graviton) update the Dockerfile to replace all occurrences of `x86_64` by `aarch64`.

* Build the app: `./mvnw clean package`
* Then package it: `docker build -t csfle-demo:latest`
* Run: `docker run -t csfle-demo:latest`

This sample app uses a local master key. Don't do that in production! Instead configure a proper KMS.
