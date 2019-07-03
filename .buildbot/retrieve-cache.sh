#!/bin/bash

# retrieve the cached object

# prefix before the hash of the cached dir
PREFIX=$1

# relative to the $HOME
HASH_FILE=$HOME/$2
INSTALL_DIR=$HOME/$3

# full cached name
CACHE_NAME=$PREFIX-$(md5sum $2 | awk '{print $1;}')

echo "DEBUG:"
echo "$PREFIX"
echo "$(md5sum $2 | awk '{print $1;}')"
echo "$CACHE_NAME"

if [ ! -d "$BB_SHARED_DOCKER_DIR/$CACHE_NAME" ]; then
    echo "Failed to get $CACHE_NAME from the cache"
else
    echo "Getting cache and moving it to $INSTALL_DIR"
    mkdir -p $INSTALL_DIR
    cp -R $BB_SHARED_DOCKER_DIR/$CACHE_NAME $INSTALL_DIR
    echo "Done moving cache"
fi
