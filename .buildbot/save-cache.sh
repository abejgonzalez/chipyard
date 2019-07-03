#!/bin/bash

# save the cached object

# name of the object
NAME=$1
HASHFILE=$2
LOCATION=$3

COMBINED_NAME=$NAME-$(md5sum $2 | awk '{print $1;}')

if [ ! -d "$BB_SHARED_DOCKER_DIR/$COMBINED_NAME" ]; then
    echo "Saving $COMBINED_NAME"
    mkdir -p $LOCATION
    cp -R $LOCATION $BB_SHARED_DOCKER_DIR/$COMBINED_NAME
    echo "Done moving cache"
else
    echo "Already exists"
fi
