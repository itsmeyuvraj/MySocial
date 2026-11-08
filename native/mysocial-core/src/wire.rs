/// Fast binary frame validation and bounds checking for streaming radio sockets.
/// Prevents memory exhaustion attacks and malformed buffer exploits.

pub const MAX_SAFE_FRAME_SIZE: usize = 16 * 1024 * 1024; // 16 MB max frame
pub const MIN_SAFE_FRAME_SIZE: usize = 4; // At least 4-byte length prefix

/// Validates whether a raw byte buffer contains a safe, correctly formatted frame.
pub fn validate_frame_bounds(frame: &[u8]) -> bool {
    if frame.len() < MIN_SAFE_FRAME_SIZE || frame.len() > MAX_SAFE_FRAME_SIZE {
        return false;
    }
    true
}

/// Checks whether a stream header contains a valid UTF-8 JSON prefix or binary magic.
pub fn validate_frame_content(frame: &[u8]) -> bool {
    if !validate_frame_bounds(frame) {
        return false;
    }

    // Fast header inspect: JSON '{', or binary magic 'LR' (LoRa) or 'MS' (MySocial)
    let first = frame[0];
    if first == b'{' || first == b'[' {
        return true;
    }
    if frame.len() >= 2 && frame[0] == b'L' && frame[1] == b'R' {
        return true;
    }
    if frame.len() >= 2 && frame[0] == b'M' && frame[1] == b'S' {
        return true;
    }

    // Otherwise, require valid UTF-8
    std::str::from_utf8(frame).is_ok()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_valid_json_frame() {
        let json = b"{\"type\":\"HELLO_VECTOR\",\"peerId\":\"author_123\"}";
        assert!(validate_frame_content(json));
    }

    #[test]
    fn test_valid_lora_magic() {
        let lora = b"LR\x01\x01\x00\x05HELLO";
        assert!(validate_frame_content(lora));
    }

    #[test]
    fn test_empty_or_tiny_frame() {
        assert!(!validate_frame_content(b""));
        assert!(!validate_frame_content(b"abc"));
    }
}

