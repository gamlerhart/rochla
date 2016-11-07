#!/usr/bin/env bash
set -e
lein uberjar
mv target/uberjar/rochla.jar deploy/rochla.jar
docker build --tag=gamlerhart/rochla ./deploy/
docker push gamlerhart/rochla