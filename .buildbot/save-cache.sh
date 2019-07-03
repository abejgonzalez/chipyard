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
    mkdir -p $BB_SHARE_DOCKER_DIR/$CACHE_NAME
    chown -R buildbot:buildbot /path/to/volume
    ls -al $INSTALL_DIR
    ls -al $BB_SHARE_DOCKER_DIR/$CACHE_NAME
    ls -al $BB_SHARE_DOCKER_DIR
    cp -R $INSTALL_DIR $BB_SHARE_DOCKER_DIR/$CACHE_NAME
    ls -al $INSTALL_DIR
    ls -al $BB_SHARE_DOCKER_DIR/$CACHE_NAME
    ls -al $BB_SHARE_DOCKER_DIR
    echo "Done saving cache"
else
    echo "$CACHE_NAME already exists. Skipping"
fi
