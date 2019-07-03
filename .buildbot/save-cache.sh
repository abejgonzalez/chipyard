#!/bin/bash

# save the cached object

# prefix before the hash of the cached dir
PREFIX=$1

# relative to the $HOME
HASH_FILE=$HOME/$2
INSTALL_DIR=$HOME/$3

# full cached name
CACHE_NAME=$PREFIX-$(md5sum $HASH_FILE | awk '{print $1;}')

if [ ! -d "$BB_SHARE_DOCKER_DIR/$CACHE_NAME" ]; then
    echo "Saving $CACHE_NAME"
    sudo chown -R buildbot:buildbot $BB_SHARE_DOCKER_DIR
    mkdir -p $BB_SHARE_DOCKER_DIR/$CACHE_NAME
    cp -R $INSTALL_DIR $BB_SHARE_DOCKER_DIR/$CACHE_NAME
    echo "Done saving cache"
else
    echo "$CACHE_NAME already exists. Skipping"
fi
