const std = @import("std");

pub fn build(b: *std.Build) void {
    const target = b.standardTargetOptions(.{});
    const optimize = b.standardOptimizeOption(.{ .preferred_optimize_mode = .fast });

    const comb = b.dependency("comb", .{
        .target = target,
        .optimize = optimize,
    }).module("comb");

    const lib = b.addLibrary(.{
        .name = "ingamecnl_grep",
        .linkage = .dynamic,
        .root_module = b.createModule(.{
            .root_source_file = b.path("src/grep.zig"),
            .target = target,
            .optimize = optimize,
            .imports = &.{
                .{ .name = "comb", .module = comb },
            },
        }),
    });
    b.installArtifact(lib);

    const tests = b.addTest(.{
        .root_module = lib.root_module,
    });
    const run_tests = b.addRunArtifact(tests);
    const test_step = b.step("test", "Run native grep ABI tests");
    test_step.dependOn(&run_tests.step);
}
