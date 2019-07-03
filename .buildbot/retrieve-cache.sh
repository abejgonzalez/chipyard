#!/bin/bash

# retrieve the cached object

# prefix before the hash of the cached dir
PREFIX=$1

# relative to the $HOME
HASH_FILE=$HOME/$2
INSTALL_DIR=$HOME/$3

# full cached name
CACHE_NAME=$PREFIX-$(md5sum $HASH_FILE | awk '{print $1;}')

if [ ! -d "$BB_SHARE_DOCKER_DIR/$CACHE_NAME" ]; then
    echo "Failed to get $CACHE_NAME from the cache"
else
    echo "Getting cache and moving it to $INSTALL_DIR"
    mkdir -p $INSTALL_DIR
    cp -R $BB_SHARE_DOCKER_DIR/$CACHE_NAME $INSTALL_DIR
    echo "Done moving cache"
fi
