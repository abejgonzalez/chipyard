#!/bin/bash

# build verilator

# turn echo on and error on earliest command
set -ex

cd $HOME/build
cd sims/verisim

if [ ! -d "$HOME/build/sims/verisim/verilator" ]; then
    # make verilator
    make verilator_install
fi
