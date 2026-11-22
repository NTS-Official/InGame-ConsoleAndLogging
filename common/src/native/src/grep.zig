const std = @import("std");
const comb = @import("comb");

pub const ABI_VERSION: u32 = 1;
pub const OK: i32 = 0;
pub const BAD_ARGUMENT: i32 = 1;
pub const BAD_LAYOUT: i32 = 2;

const MAX_FIELDS: u32 = 1024;
const MAX_RECORD_INDEX: u32 = 63;

pub const Field = extern struct {
    offset: u32,
    length: u32,
    record_index: u32,
};

/// Returns the ABI version implemented by this library.
export fn igcnl_grep_abi_version() callconv(.c) u32 {
    return ABI_VERSION;
}

/// Scans one batch of ASCII fields for a case-insensitive literal match.
///
/// Each field is a byte range inside `text`; `record_index` selects one of 64
/// output bits. Inputs are borrowed for the duration of the call only.
export fn igcnl_grep_scan_ascii_v1(
    needle_ptr: ?[*]const u8,
    needle_len: u32,
    text_ptr: ?[*]const u8,
    text_len: u32,
    fields_ptr: ?[*]const Field,
    field_count: u32,
    out_mask: ?*u64,
) callconv(.c) i32 {
    if (needle_ptr == null or needle_len == 0) return BAD_ARGUMENT;
    if (text_ptr == null and text_len != 0) return BAD_ARGUMENT;
    if (fields_ptr == null and field_count != 0) return BAD_ARGUMENT;
    if (field_count > MAX_FIELDS) return BAD_LAYOUT;
    const mask_ptr = out_mask orelse return BAD_ARGUMENT;

    const needle = needle_ptr.?[0..needle_len];
    const text = if (text_ptr) |ptr| ptr[0..text_len] else &[_]u8{};
    const matcher = comb.LiteralMatcher.init(needle, false, false) catch return BAD_ARGUMENT;

    var mask: u64 = 0;
    if (field_count != 0) {
        const fields = fields_ptr.?[0..field_count];
        for (fields) |field| {
            if (field.record_index > MAX_RECORD_INDEX) return BAD_LAYOUT;
            if (field.offset > text_len) return BAD_LAYOUT;
            if (field.length > text_len - field.offset) return BAD_LAYOUT;
            const start: usize = field.offset;
            const end: usize = start + field.length;
            if (matcher.findFirst(text[start..end], 0) != null) {
                mask |= @as(u64, 1) << @intCast(field.record_index);
            }
        }
    }

    mask_ptr.* = mask;
    return OK;
}

test "reports ABI version" {
    try std.testing.expectEqual(ABI_VERSION, igcnl_grep_abi_version());
}

test "matches fields and combines record bits" {
    const needle = "failed";
    const text = "Failed to load\nother failed line";
    const fields = [_]Field{
        .{ .offset = 0, .length = 6, .record_index = 2 },
        .{ .offset = 15, .length = 6, .record_index = 2 },
    };
    var mask: u64 = 0;
    try std.testing.expectEqual(
        OK,
        igcnl_grep_scan_ascii_v1(needle, needle.len, text, text.len, &fields, fields.len, &mask),
    );
    try std.testing.expectEqual(@as(u64, 1) << 2, mask);
}

test "rejects out-of-range fields" {
    const needle = "x";
    const text = "abc";
    const fields = [_]Field{
        .{ .offset = 2, .length = 2, .record_index = 0 },
    };
    var mask: u64 = 0;
    try std.testing.expectEqual(
        BAD_LAYOUT,
        igcnl_grep_scan_ascii_v1(needle, needle.len, text, text.len, &fields, fields.len, &mask),
    );
}
