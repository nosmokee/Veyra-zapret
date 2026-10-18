#!/bin/sh
set -eu
# Requires Zig 0.13.0 in PATH. Build from Linux/.
cd "$(dirname "$0")/core-byedpi"
mkdir -p ../bin
for arch in x86_64 aarch64; do
 zig cc -target "$arch-linux-musl" -static -D_DEFAULT_SOURCE -std=c99 -O2 -I. packets.c main.c conev.c proxy.c desync.c mpool.c extend.c -o "../bin/ciadpi-$arch" -Wno-unused-parameter
done
# Choose matching architecture for local launcher.
cp "../bin/ciadpi-$(uname -m)" ../bin/ciadpi
