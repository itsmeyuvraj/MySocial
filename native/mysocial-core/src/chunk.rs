use sha2::{Digest, Sha256};

/// Computes a Merkle root hash across multiple 16KB-64KB data chunks.
/// Uses ARM NEON hardware acceleration on supported AArch64 Android SoCs.
pub fn compute_chunk_tree_hash(chunks: &[&[u8]]) -> [u8; 32] {
    if chunks.is_empty() {
        return [0u8; 32];
    }

    let mut tree_hasher = Sha256::new();
    for chunk in chunks {
        let mut chunk_hasher = Sha256::new();
        chunk_hasher.update(chunk);
        let chunk_hash: [u8; 32] = chunk_hasher.finalize().into();
        tree_hasher.update(&chunk_hash);
    }

    tree_hasher.finalize().into()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_chunk_tree_hash_determinism() {
        let chunk1 = b"Chunk number one data bytes";
        let chunk2 = b"Chunk number two data bytes";
        let chunks = vec![chunk1.as_slice(), chunk2.as_slice()];

        let hash1 = compute_chunk_tree_hash(&chunks);
        let hash2 = compute_chunk_tree_hash(&chunks);

        assert_eq!(hash1, hash2);
        assert_ne!(hash1, [0u8; 32]);
    }

    #[test]
    fn test_empty_chunks() {
        let empty: Vec<&[u8]> = Vec::new();
        assert_eq!(compute_chunk_tree_hash(&empty), [0u8; 32]);
    }
}

