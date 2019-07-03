#!/bin/bash

# bring in the cached object

# name of the object
NAME=$1
HASHFILE=$2
LOCATION=$3

COMBINED_NAME=$NAME-$(md5sum $2 | awk '{print $1;}')

echo "$NAME"
echo "$(md5sum $2 | awk '{print $1;}')"
echo "$COMBINED_NAME"

if [ ! -d "$BB_SHARED_DOCKER_DIR/$COMBINED_NAME" ]; then
    echo "Failed to get $COMBINED_NAME from the cache"
else
    echo "Getting cache and moving it to $LOCATION"
    mkdir -p $LOCATION
    cp -R $BB_SHARED_DOCKER_DIR/$COMBINED_NAME $LOCATION
    echo "Done moving cache"
fi
