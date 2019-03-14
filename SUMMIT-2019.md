# Summit 2019 Instructions

This repo provides the version of Syndesis used for the Summit demo.

## Creating the images

**NOTE**: Camel K Runtime SNAPSHOTS needs to be installed in the local maven repo before building Syndesis 

```
# Full build of the project
syndesis build -b

# Building the server docker image
syndesis build -c -f -m server --docker

# Building the meta docker image
syndesis build -c -f -m meta --docker

# Pushing server to quay.io (you need to join the redhatdemo team)
docker tag syndesis/syndesis-server:latest quay.io/redhatdemo/syndesis-server:latest
docker push quay.io/redhatdemo/syndesis-server:latest

# Pushing meta to quay.io (you need to join the redhatdemo team)
docker tag syndesis/syndesis-meta:latest quay.io/redhatdemo/syndesis-meta:latest
docker push quay.io/redhatdemo/syndesis-meta:latest
```
