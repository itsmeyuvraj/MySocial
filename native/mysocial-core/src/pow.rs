use sha2::{Digest, Sha256};

/// Counts the number of leading zero bits in a 32-byte hash.
#[inline]
pub fn count_leading_zero_bits(hash: &[u8; 32]) -> u32 {
    let mut zeros = 0u32;
    for &byte in hash {
        let lz = byte.leading_zeros();
        zeros += lz;
        if lz != 8 {
            break;
        }
    }
    zeros
}

/// Mines a Hashcash Proof-of-Work nonce for a given challenge and bit difficulty.
/// Uses pre-computed SHA-256 state cloning for maximum throughput.
pub fn mine_hashcash(challenge: &[u8], difficulty_bits: u32) -> u64 {
    if difficulty_bits == 0 {
        return 0;
    }

    // Pre-hash the challenge prefix once
    let mut base_hasher = Sha256::new();
    base_hasher.update(challenge);

    let mut nonce: u64 = 0;
    loop {
        let mut hasher = base_hasher.clone();
        hasher.update(&nonce.to_be_bytes());
        let digest: [u8; 32] = hasher.finalize().into();

        if count_leading_zero_bits(&digest) >= difficulty_bits {
            return nonce;
        }

        nonce = nonce.wrapping_add(1);
    }
}

/// Verifies whether a given nonce satisfies the Hashcash Proof-of-Work bit difficulty.
pub fn verify_hashcash(challenge: &[u8], nonce: u64, difficulty_bits: u32) -> bool {
    if difficulty_bits == 0 {
        return true;
    }

    let mut hasher = Sha256::new();
    hasher.update(challenge);
    hasher.update(&nonce.to_be_bytes());
    let digest: [u8; 32] = hasher.finalize().into();

    count_leading_zero_bits(&digest) >= difficulty_bits
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_mine_and_verify_pow() {
        let challenge = b"test_author:seq_1:test_content_hash";
        let difficulty = 12; // 12 bits = 3 hex zeros

        let nonce = mine_hashcash(challenge, difficulty);
        assert!(verify_hashcash(challenge, nonce, difficulty));
        // Wrong nonce fails
        assert!(!verify_hashcash(challenge, nonce.wrapping_add(1), 24));
    }

    #[test]
    fn test_zero_difficulty() {
        let challenge = b"free_pass";
        assert!(verify_hashcash(challenge, 0, 0));
        assert_eq!(mine_hashcash(challenge, 0), 0);
    }
}

