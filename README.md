# In-Game Console And Logging

## Overview

A Forts-syle in-game command & logging view console mod for Minecraft 26.1.2 and above.

> [!Warning]
> 
> Cross-platform `comb` acceleration(for the command `grep`) hasn't been verified on macOS.

Full usage please refer [here](docs\usage.md).

## Build

Asides JDK25, you'll additionally need a zig toolchain versioned at least 0.17.1 for native support, and a LLVM backend setup for zig's cross-compiling.

There's no need for you to manully run `zig build -Drelease`, the gradle will complete this for you when you run related tasks.

## License

- `comb` was licensed under the MIT License.
