#!/bin/bash

# save the cached object

# prefix before the hash of the cached dir
PREFIX=$1

# relative to the $HOME
HASH_FILE=$HOME/$2
INSTALL_DIR=$HOME/$3

# full cached name
CACHE_NAME=$PREFIX-$(md5sum $HASH_FILE | awk '{print $1;}')

echo "DEBUG:"
echo "$PREFIX"
echo "$(md5sum $HASH_FILE | awk '{print $1;}')"
echo "$CACHE_NAME"

if [ ! -d "$BB_SHARE_DOCKER_DIR/$CACHE_NAME" ]; then
    echo "Saving $CACHE_NAME"
    mkdir -p $INSTALL_DIR
    cp -R $INSTALL_DIR $BB_SHARE_DOCKER_DIR/$CACHE_NAME
    echo "Done saving cache"
else
    echo "$CACHE_NAME already exists. Skipping"
fi
